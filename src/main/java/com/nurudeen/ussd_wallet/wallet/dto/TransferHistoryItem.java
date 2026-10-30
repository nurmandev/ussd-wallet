package com.nurudeen.ussd_wallet.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferHistoryItem(
        String reference,
        String direction,
        String counterpartyWalletNumber,
        BigDecimal amount,
        BigDecimal fee,
        String currency,
        String description,
        String status,
        Instant createdAt) {
}
