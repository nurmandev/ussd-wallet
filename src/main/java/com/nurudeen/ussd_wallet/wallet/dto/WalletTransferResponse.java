package com.nurudeen.ussd_wallet.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransferResponse(
        String reference,
        String recipientWalletNumber,
        BigDecimal amount,
        String currency,
        Instant createdAt) {
}
