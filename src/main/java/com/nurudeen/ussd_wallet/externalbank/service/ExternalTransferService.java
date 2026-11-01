package com.nurudeen.ussd_wallet.externalbank.service;

import com.nurudeen.ussd_wallet.common.exception.InsufficientWalletFundsException;
import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.common.exception.WalletTransferConflictException;
import com.nurudeen.ussd_wallet.externalbank.dto.ExternalTransferRequest;
import com.nurudeen.ussd_wallet.externalbank.dto.ExternalTransferResponse;
import com.nurudeen.ussd_wallet.externalbank.entity.AccountResolution;
import com.nurudeen.ussd_wallet.externalbank.entity.Bank;
import com.nurudeen.ussd_wallet.externalbank.entity.ExternalTransfer;
import com.nurudeen.ussd_wallet.externalbank.entity.TransferAttempt;
import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;
import com.nurudeen.ussd_wallet.externalbank.enums.ExternalTransferStatus;
import com.nurudeen.ussd_wallet.externalbank.exception.AccountResolutionException;
import com.nurudeen.ussd_wallet.externalbank.exception.ExternalTransferException;
import com.nurudeen.ussd_wallet.externalbank.repository.ExternalTransferRepository;
import com.nurudeen.ussd_wallet.externalbank.repository.TransferAttemptRepository;
import com.nurudeen.ussd_wallet.externalbank.simulator.SimulatedBankResponse;
import com.nurudeen.ussd_wallet.security.service.PinVerificationService;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import com.nurudeen.ussd_wallet.wallet.repository.WalletBalanceRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalTransferService {

    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final ExternalTransferRepository transferRepository;
    private final TransferAttemptRepository attemptRepository;
    private final AccountResolutionService accountResolutionService;
    private final BankService bankService;
    private final TransferFeeService feeService;
    private final BankSwitchService bankSwitchService;
    private final PinVerificationService pinVerificationService;
    private final TransferReversalService reversalService;

    public ExternalTransferService(
            WalletRepository walletRepository,
            WalletBalanceRepository walletBalanceRepository,
            ExternalTransferRepository transferRepository,
            TransferAttemptRepository attemptRepository,
            AccountResolutionService accountResolutionService,
            BankService bankService,
            TransferFeeService feeService,
            BankSwitchService bankSwitchService,
            PinVerificationService pinVerificationService,
            TransferReversalService reversalService) {
        this.walletRepository = walletRepository;
        this.walletBalanceRepository = walletBalanceRepository;
        this.transferRepository = transferRepository;
        this.attemptRepository = attemptRepository;
        this.accountResolutionService = accountResolutionService;
        this.bankService = bankService;
        this.feeService = feeService;
        this.bankSwitchService = bankSwitchService;
        this.pinVerificationService = pinVerificationService;
        this.reversalService = reversalService;
    }

    @Transactional
    public ExternalTransferResponse initiate(UUID customerId, ExternalTransferRequest request) {
        Wallet wallet = walletRepository.findByCustomer_Id(customerId).orElseThrow(WalletNotFoundException::new);
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new ExternalTransferException("Wallet is not active for external transfers");
        }

        Bank bank = bankService.requireActiveBank(request.bankCode());
        AccountResolution resolution = accountResolutionService.validateResolution(customerId, request.bankCode(), request.accountNumber(), request.resolutionReference());
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = feeService.calculateFee(amount);
        BigDecimal totalDebit = amount.add(fee);

        String fingerprint = buildFingerprint(customerId, request);
        ExternalTransfer existing = transferRepository.findByWalletIdAndIdempotencyKey(wallet.getId(), request.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getRequestFingerprint(), fingerprint)) {
                throw new WalletTransferConflictException("Idempotency key was already used for a different request");
            }
            return toResponse(existing);
        }

        pinVerificationService.verify(customerId, request.transactionPin());

        WalletBalance balance = walletBalanceRepository.findByWalletIdForUpdate(wallet.getId())
                .orElseThrow(() -> new IllegalStateException("Wallet balance missing"));
        if (balance.getAvailableBalance().compareTo(totalDebit) < 0) {
            throw new InsufficientWalletFundsException();
        }

        String transactionReference = "EXT-TRF-" + java.time.LocalDate.now() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        ExternalTransfer transfer = new ExternalTransfer(
                transactionReference,
                wallet.getId(),
                bank,
                maskAccount(request.accountNumber()),
                resolution.getAccountName(),
                amount,
                fee,
                totalDebit,
                request.idempotencyKey(),
                fingerprint);
        transferRepository.saveAndFlush(transfer);

        balance.debit(totalDebit);
        walletBalanceRepository.save(balance);

        SimulatedBankResponse switchResponse = bankSwitchService.processTransfer(transfer);
        transfer.setStatusFromResponse(switchResponse.responseCode(), switchResponse.message(), switchResponse.switchReference());
        transferRepository.save(transfer);

        attemptRepository.save(new TransferAttempt(
                transfer,
                "ATT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
                switchResponse.responseCode(),
                switchResponse.message(),
                switchResponse.responseCode().name()));

        if (switchResponse.responseCode() == BankResponseCode.FAILED
                || switchResponse.responseCode() == BankResponseCode.BANK_UNAVAILABLE
                || switchResponse.responseCode() == BankResponseCode.ACCOUNT_NOT_FOUND
                || switchResponse.responseCode() == BankResponseCode.INSUFFICIENT_BANK_FUNDS) {
            transfer.setStatus(ExternalTransferStatus.FAILED);
            reversalService.reverse(transfer, switchResponse.message());
        }

        if (switchResponse.responseCode() == BankResponseCode.TIMEOUT) {
            transfer.setStatus(ExternalTransferStatus.UNKNOWN);
        }

        transferRepository.save(transfer);
        return toResponse(transfer);
    }

    private String buildFingerprint(UUID customerId, ExternalTransferRequest request) {
        String raw = customerId + "|" + request.bankCode() + "|" + request.accountNumber() + "|" + request.amount() + "|" + request.resolutionReference() + "|" + request.idempotencyKey();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String maskAccount(String accountNumber) {
        String digits = accountNumber == null ? "" : accountNumber.replaceAll("\\D+", "");
        if (digits.length() <= 4) {
            return "******";
        }
        return "******" + digits.substring(digits.length() - 4);
    }

    private ExternalTransferResponse toResponse(ExternalTransfer transfer) {
        return new ExternalTransferResponse(
                transfer.getTransactionReference(),
                transfer.getStatus().name(),
                transfer.getBank().getBankName(),
                maskAccount(transfer.getDestinationAccountNumberProtected()),
                transfer.getDestinationAccountName(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getTotalDebit(),
                transfer.getCurrency(),
                transfer.getFailureReason() == null ? "Processed" : transfer.getFailureReason(),
                transfer.getSwitchReference());
    }
}
