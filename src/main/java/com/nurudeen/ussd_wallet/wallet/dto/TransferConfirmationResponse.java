package com.nurudeen.ussd_wallet.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferConfirmationResponse(
        String status,
        String reference,
        String recipientWalletNumber,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal totalDebit,
        String currency,
        Instant completedAt) {
}
