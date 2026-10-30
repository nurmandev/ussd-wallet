package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.wallet.dto.WalletBalanceResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletBalanceService {

    private final WalletRepository walletRepository;
    private final WalletBalanceRateLimitService rateLimitService;
    private final WalletBalanceAuditService auditService;
    private final Clock clock;

    public WalletBalanceService(
            WalletRepository walletRepository,
            WalletBalanceRateLimitService rateLimitService,
            WalletBalanceAuditService auditService,
            Clock clock) {
        this.walletRepository = walletRepository;
        this.rateLimitService = rateLimitService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public WalletBalanceResponse getCustomerBalance(
            UUID customerId, Channel channel, String sessionId, String correlationId) {
        if (!rateLimitService.allow(customerId, clock.instant())) {
            auditService.record(
                    customerId, null, sessionId, channel, correlationId, "RATE_LIMITED");
            throw new WalletBalanceRateLimitExceededException();
        }

        Wallet wallet;
        try {
            wallet = walletRepository.findByCustomer_Id(customerId)
                    .orElseThrow(WalletNotFoundException::new);
        } catch (WalletNotFoundException exception) {
            auditService.record(
                    customerId, null, sessionId, channel, correlationId, "WALLET_NOT_FOUND");
            throw exception;
        }

        if (!customerId.equals(wallet.getCustomerId())) {
            auditService.record(
                    customerId, null, sessionId, channel, correlationId, "OWNERSHIP_MISMATCH");
            throw new IllegalStateException("Wallet ownership does not match the authenticated customer");
        }
        if (wallet.getStatus() != WalletStatus.ACTIVE
                || !wallet.getCustomerStatus().isFinanciallyActive()) {
            auditService.record(
                    customerId, wallet.getId(), sessionId, channel, correlationId, "WALLET_UNAVAILABLE");
            throw new WalletUnavailableException();
        }

        WalletBalance balance = wallet.getBalance();
        if (balance == null) {
            auditService.record(
                    customerId, wallet.getId(), sessionId, channel, correlationId, "BALANCE_UNAVAILABLE");
            throw new IllegalStateException("Wallet balance record is missing");
        }
        try {
            validateBalances(balance);
        } catch (IllegalStateException exception) {
            auditService.record(
                    customerId, wallet.getId(), sessionId, channel, correlationId, "BALANCE_UNAVAILABLE");
            throw exception;
        }

        WalletBalanceResponse response = new WalletBalanceResponse(
                wallet.getWalletNumber(),
                wallet.getCurrency(),
                balance.getLedgerBalance(),
                balance.getAvailableBalance(),
                balance.getReservedBalance(),
                wallet.getStatus(),
                balance.getUpdatedAt());
        auditService.record(
                customerId, wallet.getId(), sessionId, channel, correlationId, "SUCCESS");
        return response;
    }

    private void validateBalances(WalletBalance balance) {
        BigDecimal ledger = balance.getLedgerBalance();
        BigDecimal available = balance.getAvailableBalance();
        BigDecimal reserved = balance.getReservedBalance();
        if (ledger.signum() < 0
                || available.signum() < 0
                || reserved.signum() < 0
                || available.add(reserved).compareTo(ledger) > 0) {
            throw new IllegalStateException("Wallet balance projection violates its accounting invariant");
        }
    }

    public enum Channel {
        REST,
        USSD
    }
}
