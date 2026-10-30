package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.common.exception.WalletTransferConflictException;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.wallet.dto.RecipientWalletResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipientWalletLookupService {

    private static final String LIMIT_SQL = """
            INSERT INTO wallet_recipient_lookup_rate_limits (customer_id, window_started_at, request_count)
            VALUES (?, now(), 1)
            ON CONFLICT (customer_id) DO UPDATE
            SET window_started_at = CASE
                    WHEN wallet_recipient_lookup_rate_limits.window_started_at <= now() - interval '1 minute'
                    THEN now() ELSE wallet_recipient_lookup_rate_limits.window_started_at END,
                request_count = CASE
                    WHEN wallet_recipient_lookup_rate_limits.window_started_at <= now() - interval '1 minute'
                    THEN 1 ELSE wallet_recipient_lookup_rate_limits.request_count + 1 END
            RETURNING request_count
            """;

    private final WalletRepository walletRepository;
    private final JdbcTemplate jdbcTemplate;

    public RecipientWalletLookupService(WalletRepository walletRepository, JdbcTemplate jdbcTemplate) {
        this.walletRepository = walletRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RecipientWalletResponse lookup(UUID requesterCustomerId, String walletNumber) {
        if (walletNumber == null || !walletNumber.matches("[0-9]{8,30}")) {
            throw new IllegalArgumentException("Wallet number is invalid");
        }
        Integer requestCount = jdbcTemplate.queryForObject(
                LIMIT_SQL, Integer.class, requesterCustomerId);
        if (requestCount == null) {
            throw new IllegalStateException("Recipient lookup rate limit did not return a count");
        }
        if (requestCount > 30) {
            throw new RecipientLookupRateLimitExceededException();
        }
        Wallet wallet = walletRepository.findByWalletNumber(walletNumber)
                .orElseThrow(WalletNotFoundException::new);
        if (requesterCustomerId.equals(wallet.getCustomerId())) {
            throw new WalletTransferConflictException("Cannot transfer to the same wallet");
        }
        if (wallet.getStatus() != WalletStatus.ACTIVE || !wallet.getCustomerStatus().isFinanciallyActive()) {
            throw new WalletTransferConflictException("Recipient wallet is unavailable");
        }
        Customer owner = wallet.getCustomerEntity();
        String name = ((owner.getFirstName() == null ? "" : owner.getFirstName()) + " "
                + (owner.getLastName() == null ? "" : owner.getLastName())).strip();
        return new RecipientWalletResponse(
                wallet.getWalletNumber(),
                name.isBlank() ? "Nuru Wallet customer" : maskName(name),
                wallet.getStatus(),
                wallet.getCurrency());
    }

    private String maskName(String name) {
        return java.util.Arrays.stream(name.split("\\s+"))
                .map(part -> part.length() < 2 ? part : part.charAt(0) + "***")
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
