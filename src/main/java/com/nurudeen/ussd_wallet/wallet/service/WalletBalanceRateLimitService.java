package com.nurudeen.ussd_wallet.wallet.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletBalanceRateLimitService {

    private static final String CONSUME_REQUEST_SQL = """
            INSERT INTO wallet_balance_rate_limits (customer_id, window_started_at, request_count)
            VALUES (?, ?, 1)
            ON CONFLICT (customer_id) DO UPDATE
            SET window_started_at = CASE
                    WHEN wallet_balance_rate_limits.window_started_at <= ? THEN EXCLUDED.window_started_at
                    ELSE wallet_balance_rate_limits.window_started_at
                END,
                request_count = CASE
                    WHEN wallet_balance_rate_limits.window_started_at <= ? THEN 1
                    ELSE wallet_balance_rate_limits.request_count + 1
                END
            RETURNING request_count
            """;

    private final JdbcTemplate jdbcTemplate;
    private final WalletBalanceProperties properties;

    public WalletBalanceRateLimitService(
            JdbcTemplate jdbcTemplate, WalletBalanceProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean allow(UUID customerId, Instant now) {
        Timestamp currentTime = Timestamp.from(now);
        Timestamp windowStart = Timestamp.from(now.minusSeconds(60));
        Integer requestCount = jdbcTemplate.queryForObject(
                CONSUME_REQUEST_SQL,
                Integer.class,
                customerId,
                currentTime,
                windowStart,
                windowStart);
        if (requestCount == null) {
            throw new IllegalStateException("Balance enquiry rate limit did not return a request count");
        }
        return requestCount <= properties.maxRequestsPerMinute();
    }
}
