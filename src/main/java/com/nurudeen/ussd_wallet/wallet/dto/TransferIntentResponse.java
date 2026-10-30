package com.nurudeen.ussd_wallet.wallet.dto;

import com.nurudeen.ussd_wallet.wallet.enums.TransferIntentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferIntentResponse(
        UUID transferIntentId,
        String recipientName,
        String recipientWalletNumber,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal totalDebit,
        String currency,
        TransferIntentStatus status,
        Instant expiresAt) {
}
