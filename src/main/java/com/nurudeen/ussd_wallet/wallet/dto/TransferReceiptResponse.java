package com.nurudeen.ussd_wallet.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferReceiptResponse(
        String reference,
        String status,
        String direction,
        String senderWalletNumber,
        String recipientWalletNumber,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal totalDebit,
        String currency,
        String description,
        Instant completedAt) {
}
