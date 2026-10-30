package com.nurudeen.ussd_wallet.wallet.dto;

import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletBalanceResponse(
        String walletNumber,
        String currency,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        BigDecimal pendingAmount,
        WalletStatus walletStatus,
        Instant lastUpdated) {
}
