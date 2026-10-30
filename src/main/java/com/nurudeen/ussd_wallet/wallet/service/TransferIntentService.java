package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.InsufficientWalletFundsException;
import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.common.exception.WalletTransferConflictException;
import com.nurudeen.ussd_wallet.config.TransferProperties;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.wallet.dto.TransferConfirmationRequest;
import com.nurudeen.ussd_wallet.wallet.dto.TransferConfirmationResponse;
import com.nurudeen.ussd_wallet.wallet.dto.TransferIntentResponse;
import com.nurudeen.ussd_wallet.wallet.dto.TransferValidationRequest;
import com.nurudeen.ussd_wallet.wallet.dto.WalletTransferRequest;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.entity.WalletTransfer;
import com.nurudeen.ussd_wallet.wallet.entity.WalletTransferIntent;
import com.nurudeen.ussd_wallet.wallet.enums.TransferIntentStatus;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferIntentRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferIntentService {

    private final WalletRepository walletRepository;
    private final WalletTransferRepository transferRepository;
    private final WalletTransferIntentRepository intentRepository;
    private final CustomerSecurityRepository securityRepository;
    private final TransferProperties transferProperties;
    private final TransferLimitService limitService;
    private final TransferLimitProperties limitProperties;
    private final WalletTransferService transferService;
    private final Clock clock;

    public TransferIntentService(
            WalletRepository walletRepository,
            WalletTransferRepository transferRepository,
            WalletTransferIntentRepository intentRepository,
            CustomerSecurityRepository securityRepository,
            TransferProperties transferProperties,
            TransferLimitService limitService,
            TransferLimitProperties limitProperties,
            WalletTransferService transferService,
            Clock clock) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.intentRepository = intentRepository;
        this.securityRepository = securityRepository;
        this.transferProperties = transferProperties;
        this.limitService = limitService;
        this.limitProperties = limitProperties;
        this.transferService = transferService;
        this.clock = clock;
    }

    @Transactional
    public TransferIntentResponse createIntent(UUID customerId, TransferValidationRequest request) {
        Wallet sender = walletRepository.findByCustomer_Id(customerId)
                .orElseThrow(WalletNotFoundException::new);
        Wallet recipient = walletRepository.findByWalletNumber(request.recipientWalletNumber())
                .orElseThrow(WalletNotFoundException::new);
        validateWallets(customerId, sender, recipient);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        String description = sanitizeDescription(request.description());
        Map<UUID, Wallet> locked = walletRepository.findAllByIdInForUpdate(List.of(sender.getId())).stream()
                .collect(Collectors.toMap(Wallet::getId, Function.identity()));
        sender = locked.get(sender.getId());
        if (sender == null) {
            throw new WalletNotFoundException();
        }

        WalletTransferIntent prior = intentRepository
                .findByCustomerIdAndIdempotencyKey(customerId, request.idempotencyKey())
                .orElse(null);
        if (prior != null) {
            if (!prior.matches(sender.getId(), recipient.getId(), amount, description)) {
                throw new WalletTransferConflictException(
                        "Idempotency key was already used for a different transfer intent");
            }
            return response(prior, recipient);
        }

        limitService.validateSingleAmount(
                amount, transferProperties.minimumAmount(), transferProperties.maximumAmount());
        if (limitProperties.fee().signum() != 0) {
            throw new WalletTransferConflictException("Non-zero internal transfer fees are not enabled");
        }
        if (securityRepository.findById(customerId).filter(security -> security.isPinConfigured()).isEmpty()) {
            throw new WalletTransferConflictException("Set up your transaction PIN before transferring");
        }
        WalletBalance balance = sender.getBalance();
        if (balance == null) {
            throw new IllegalStateException("A wallet balance record is missing");
        }
        BigDecimal fee = limitService.configuredFee().setScale(2, RoundingMode.UNNECESSARY);
        if (balance.getAvailableBalance().compareTo(amount.add(fee)) < 0) {
            throw new InsufficientWalletFundsException();
        }
        limitService.validateDailyLimit(sender.getId(), amount, clock.instant());

        Instant now = clock.instant();
        WalletTransferIntent intent = intentRepository.saveAndFlush(new WalletTransferIntent(
                UUID.randomUUID(),
                customerId,
                sender.getId(),
                recipient.getId(),
                request.idempotencyKey(),
                amount,
                fee,
                sender.getCurrency(),
                description,
                now,
                now.plusSeconds(limitService.intentExpirySeconds())));
        return response(intent, recipient);
    }

    @Transactional(noRollbackFor = TransferIntentExpiredException.class)
    public TransferConfirmationResponse confirm(
            UUID customerId,
            TransferConfirmationRequest request,
            WalletTransferService.TransferChannel channel,
            String correlationId) {
        WalletTransferIntent intent = intentRepository
                .findOwnedByIdForUpdate(request.transferIntentId(), customerId)
                .orElseThrow(WalletNotFoundException::new);
        if (!intent.getIdempotencyKey().equals(request.idempotencyKey())) {
            throw new WalletTransferConflictException("Idempotency key does not match the transfer intent");
        }
        if (intent.getStatus() == TransferIntentStatus.COMPLETED) {
            return priorConfirmation(intent);
        }
        if (intent.getStatus() != TransferIntentStatus.PENDING_CONFIRMATION) {
            throw new WalletTransferConflictException("Transfer intent is no longer available");
        }
        Instant now = clock.instant();
        if (intent.isExpiredAt(now)) {
            intent.expire(now);
            throw new TransferIntentExpiredException();
        }
        Wallet recipient = walletRepository.findById(intent.getRecipientWalletId())
                .orElseThrow(WalletNotFoundException::new);
        WalletTransferRequest transferRequest = new WalletTransferRequest(
                recipient.getWalletNumber(),
                intent.getAmount(),
                request.transactionPin(),
                intent.getIdempotencyKey());
        var result = transferService.transfer(
                customerId,
                transferRequest,
                intent.getFee(),
                intent.getDescription(),
                channel,
                correlationId);
        intent.complete(result.reference(), now);
        return new TransferConfirmationResponse(
                "SUCCESSFUL",
                result.reference(),
                result.recipientWalletNumber(),
                result.amount(),
                intent.getFee(),
                result.amount().add(intent.getFee()),
                result.currency(),
                now);
    }

    @Transactional
    public void cancel(UUID customerId, UUID intentId) {
        WalletTransferIntent intent = intentRepository.findOwnedByIdForUpdate(intentId, customerId)
                .orElseThrow(WalletNotFoundException::new);
        if (intent.getStatus() != TransferIntentStatus.PENDING_CONFIRMATION) {
            throw new WalletTransferConflictException("Transfer intent is no longer available");
        }
        Instant now = clock.instant();
        if (intent.isExpiredAt(now)) {
            intent.expire(now);
            return;
        }
        intent.cancel(now);
    }

    private TransferConfirmationResponse priorConfirmation(WalletTransferIntent intent) {
        WalletTransfer transfer = transferRepository
                .findBySenderWalletIdAndIdempotencyKey(
                        intent.getSenderWalletId(), intent.getIdempotencyKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Completed transfer intent has no matching posted transfer"));
        Wallet recipient = walletRepository.findById(transfer.getRecipientWalletId())
                .orElseThrow(WalletNotFoundException::new);
        return new TransferConfirmationResponse(
                "SUCCESSFUL",
                transfer.getReference(),
                recipient.getWalletNumber(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getTotalDebit(),
                transfer.getCurrency(),
                transfer.getCreatedAt());
    }

    private void validateWallets(UUID customerId, Wallet sender, Wallet recipient) {
        if (sender.getId().equals(recipient.getId()) || customerId.equals(recipient.getCustomerId())) {
            throw new WalletTransferConflictException("Cannot transfer to the same wallet");
        }
        if (sender.getStatus() != WalletStatus.ACTIVE
                || recipient.getStatus() != WalletStatus.ACTIVE
                || !sender.getCustomerStatus().isFinanciallyActive()
                || !recipient.getCustomerStatus().isFinanciallyActive()) {
            throw new WalletTransferConflictException("Wallet is not available for transfers");
        }
        if (!sender.getCurrency().equals(recipient.getCurrency())) {
            throw new WalletTransferConflictException("Wallet currencies do not match");
        }
    }

    private TransferIntentResponse response(WalletTransferIntent intent, Wallet recipient) {
        return new TransferIntentResponse(
                intent.getId(),
                recipientName(recipient),
                recipient.getWalletNumber(),
                intent.getAmount(),
                intent.getFee(),
                intent.getTotalDebit(),
                intent.getCurrency(),
                intent.getStatus(),
                intent.getExpiresAt());
    }

    private String recipientName(Wallet wallet) {
        String fullName = ((wallet.getCustomerEntity().getFirstName() == null
                        ? ""
                        : wallet.getCustomerEntity().getFirstName())
                + " "
                + (wallet.getCustomerEntity().getLastName() == null
                        ? ""
                        : wallet.getCustomerEntity().getLastName())).strip();
        if (fullName.isBlank()) {
            return "Nuru Wallet customer";
        }
        return java.util.Arrays.stream(fullName.split("\\s+"))
                .map(part -> part.length() < 2 ? part : part.charAt(0) + "***")
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String sanitizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String sanitized = description.strip().replaceAll("[\\p{Cntrl}]", " ");
        return sanitized.length() > 140 ? sanitized.substring(0, 140) : sanitized;
    }
}
