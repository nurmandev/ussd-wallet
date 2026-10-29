package com.nurudeen.ussd_wallet.ussd.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UssdTransactionHistoryItem(
        String direction,
        BigDecimal amount,
        String currency,
        String reference,
        Instant createdAt) {
}
