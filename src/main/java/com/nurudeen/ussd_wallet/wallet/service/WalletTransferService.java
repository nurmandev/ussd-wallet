package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.InsufficientWalletFundsException;
import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.common.exception.WalletTransferConflictException;
import com.nurudeen.ussd_wallet.config.TransferProperties;
import com.nurudeen.ussd_wallet.security.service.PinVerificationService;
import com.nurudeen.ussd_wallet.wallet.dto.WalletTransferRequest;
import com.nurudeen.ussd_wallet.wallet.dto.WalletTransferResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.entity.WalletTransfer;
import com.nurudeen.ussd_wallet.wallet.entity.WalletTransferEntry;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import com.nurudeen.ussd_wallet.wallet.repository.WalletBalanceRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferEntryRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletTransferService {

    private final WalletRepository walletRepository;
    private final WalletBalanceRepository balanceRepository;
    private final WalletTransferRepository transferRepository;
    private final WalletTransferEntryRepository entryRepository;
    private final PinVerificationService pinVerificationService;
    private final EntityManager entityManager;
    private final TransferProperties transferProperties;

    public WalletTransferService(
            WalletRepository walletRepository,
            WalletBalanceRepository balanceRepository,
            WalletTransferRepository transferRepository,
            WalletTransferEntryRepository entryRepository,
            PinVerificationService pinVerificationService,
            EntityManager entityManager,
            TransferProperties transferProperties) {
        this.walletRepository = walletRepository;
        this.balanceRepository = balanceRepository;
        this.transferRepository = transferRepository;
        this.entryRepository = entryRepository;
        this.pinVerificationService = pinVerificationService;
        this.entityManager = entityManager;
        this.transferProperties = transferProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WalletTransferResponse transfer(UUID customerId, WalletTransferRequest request) {
        Wallet sender = walletRepository.findWalletForTransfer(customerId)
                .orElseThrow(WalletNotFoundException::new);
        Wallet recipient = walletRepository.findByWalletNumber(request.recipientWalletNumber())
                .orElseThrow(WalletNotFoundException::new);
        validateWallets(sender, recipient);

        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        if (amount.compareTo(transferProperties.minimumAmount()) < 0
                || amount.compareTo(transferProperties.maximumAmount()) > 0) {
            throw new WalletTransferConflictException("Transfer amount is outside the permitted limits");
        }
        pinVerificationService.verify(customerId, request.pin());

        List<UUID> walletIds = List.of(sender.getId(), recipient.getId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        Map<UUID, Wallet> lockedWallets = walletRepository.findAllByIdInForUpdate(walletIds).stream()
                .collect(Collectors.toMap(Wallet::getId, Function.identity()));
        Wallet lockedSender = lockedWallets.get(sender.getId());
        Wallet lockedRecipient = lockedWallets.get(recipient.getId());
        if (lockedSender == null || lockedRecipient == null) {
            throw new WalletNotFoundException();
        }
        walletIds.stream()
                .map(lockedWallets::get)
                .forEach(wallet -> entityManager.refresh(wallet, LockModeType.PESSIMISTIC_WRITE));
        List<Wallet> byCustomerId = List.of(lockedSender, lockedRecipient).stream()
                .sorted(Comparator.comparing(Wallet::getCustomerId))
                .toList();
        byCustomerId.forEach(wallet -> entityManager.refresh(
                wallet.getCustomerEntity(), LockModeType.PESSIMISTIC_WRITE));
        validateWallets(lockedSender, lockedRecipient);

        var existingTransfer = transferRepository.findBySenderWalletIdAndIdempotencyKey(
                lockedSender.getId(), request.idempotencyKey());
        if (existingTransfer.isPresent()) {
            WalletTransfer prior = existingTransfer.get();
            if (!prior.getRecipientWalletId().equals(lockedRecipient.getId())
                    || prior.getAmount().compareTo(amount) != 0
                    || !prior.getCurrency().equals(lockedSender.getCurrency())) {
                throw new WalletTransferConflictException(
                        "Idempotency key was already used for a different transfer");
            }
            return response(prior, lockedRecipient);
        }

        Map<UUID, WalletBalance> balances = balanceRepository
                .findAllByWalletIdInForUpdate(walletIds).stream()
                .collect(Collectors.toMap(WalletBalance::getWalletId, Function.identity()));
        WalletBalance senderBalance = balances.get(lockedSender.getId());
        WalletBalance recipientBalance = balances.get(lockedRecipient.getId());
        if (senderBalance == null || recipientBalance == null) {
            throw new IllegalStateException("A wallet balance record is missing");
        }
        if (senderBalance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientWalletFundsException();
        }

        UUID transferId = UUID.randomUUID();
        WalletTransfer transfer = transferRepository.saveAndFlush(new WalletTransfer(
                transferId,
                lockedSender.getId(),
                lockedRecipient.getId(),
                request.idempotencyKey(),
                amount,
                lockedSender.getCurrency()));

        senderBalance.debit(amount);
        recipientBalance.credit(amount);
        entryRepository.saveAll(List.of(
                new WalletTransferEntry(
                        transfer, lockedSender, WalletTransferEntry.EntryType.DEBIT, amount,
                        lockedSender.getCurrency()),
                new WalletTransferEntry(
                        transfer, lockedRecipient, WalletTransferEntry.EntryType.CREDIT, amount,
                        lockedSender.getCurrency())));
        entryRepository.flush();

        return response(transfer, lockedRecipient);
    }

    private void validateWallets(Wallet sender, Wallet recipient) {
        if (sender.getId().equals(recipient.getId())) {
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

    private WalletTransferResponse response(WalletTransfer transfer, Wallet recipient) {
        return new WalletTransferResponse(
                transfer.getReference(),
                recipient.getWalletNumber(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getCreatedAt());
    }
}
