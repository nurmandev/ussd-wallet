package com.nurudeen.ussd_wallet.externalbank.service;

import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.externalbank.dto.AccountResolutionResponse;
import com.nurudeen.ussd_wallet.externalbank.entity.AccountResolution;
import com.nurudeen.ussd_wallet.externalbank.entity.Bank;
import com.nurudeen.ussd_wallet.externalbank.entity.ExternalBankAccount;
import com.nurudeen.ussd_wallet.externalbank.enums.AccountResolutionStatus;
import com.nurudeen.ussd_wallet.externalbank.exception.AccountResolutionException;
import com.nurudeen.ussd_wallet.externalbank.exception.BankUnavailableException;
import com.nurudeen.ussd_wallet.externalbank.repository.AccountResolutionRepository;
import com.nurudeen.ussd_wallet.externalbank.repository.ExternalBankAccountRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountResolutionService {

    private final ExternalBankAccountRepository externalBankAccountRepository;
    private final AccountResolutionRepository accountResolutionRepository;
    private final WalletRepository walletRepository;
    private final BankService bankService;
    private final Clock clock;

    public AccountResolutionService(
            ExternalBankAccountRepository externalBankAccountRepository,
            AccountResolutionRepository accountResolutionRepository,
            WalletRepository walletRepository,
            BankService bankService,
            Clock clock) {
        this.externalBankAccountRepository = externalBankAccountRepository;
        this.accountResolutionRepository = accountResolutionRepository;
        this.walletRepository = walletRepository;
        this.bankService = bankService;
        this.clock = clock;
    }

    @Transactional
    public AccountResolutionResponse resolve(UUID customerId, String bankCode, String accountNumber) {
        walletRepository.findByCustomer_Id(customerId).orElseThrow(WalletNotFoundException::new);
        Bank bank = bankService.findByBankCode(bankCode)
                .orElseThrow(() -> new BankUnavailableException("Destination bank is unavailable or inactive"));
        if (bank.getStatus() != com.nurudeen.ussd_wallet.externalbank.enums.BankStatus.ACTIVE) {
            throw new BankUnavailableException("Destination bank is inactive");
        }
        String normalizedAccountNumber = normalizeAccountNumber(accountNumber);
        String hash = sha256(normalizedAccountNumber);
        ExternalBankAccount account = externalBankAccountRepository.findByBank_BankCodeAndAccountNumberHash(bankCode, hash)
                .orElse(null);
        if (account == null) {
            throw new AccountResolutionException("ACCOUNT_NOT_FOUND");
        }

        Instant expiresAt = clock.instant().plusSeconds(900);
        String resolutionReference = "RES-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        AccountResolution resolution = new AccountResolution(customerId, bank.getId(), resolutionReference, hash, account.getAccountName(), expiresAt);
        accountResolutionRepository.save(resolution);

        return new AccountResolutionResponse(
                true,
                account.getAccountName(),
                bank.getBankName(),
                maskAccountNumber(normalizedAccountNumber),
                resolutionReference,
                AccountResolutionStatus.VERIFIED.name());
    }

    @Transactional(readOnly = true)
    public AccountResolution validateResolution(UUID customerId, String bankCode, String accountNumber, String resolutionReference) {
        Bank bank = bankService.requireActiveBank(bankCode);
        AccountResolution resolution = accountResolutionRepository.findByResolutionReferenceAndCustomerId(resolutionReference, customerId)
                .orElseThrow(() -> new AccountResolutionException("Invalid or expired resolution reference"));
        if (!resolution.getBankId().equals(bank.getId())) {
            throw new AccountResolutionException("Resolved bank does not match the submitted destination bank");
        }
        if (resolution.isExpiredAt(clock.instant())) {
            resolution.expire();
            throw new AccountResolutionException("Account resolution has expired");
        }
        String suppliedHash = sha256(normalizeAccountNumber(accountNumber));
        if (!resolution.getAccountNumberHash().equals(suppliedHash)) {
            throw new AccountResolutionException("Resolved account does not match the submitted destination account");
        }
        return resolution;
    }

    private String normalizeAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            throw new IllegalArgumentException("Account number is required");
        }
        String digits = accountNumber.replaceAll("\\D+", "");
        if (digits.length() < 6 || digits.length() > 10) {
            throw new IllegalArgumentException("Account number must be between 6 and 10 digits");
        }
        return digits;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "******";
        }
        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
