package com.nurudeen.ussd_wallet.externalbank.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferStatusResponse(
        String transactionReference,
        String status,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal totalDebit,
        String currency,
        String destinationBank,
        String destinationAccount,
        String accountName,
        Instant createdAt,
        Instant completedAt) {
}
