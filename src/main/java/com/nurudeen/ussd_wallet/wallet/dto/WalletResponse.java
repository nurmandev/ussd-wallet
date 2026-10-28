package com.nurudeen.ussd_wallet.wallet.dto;

import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID walletId,
        String walletNumber,
        String currency,
        WalletStatus status,
        Balance balance) {

    public static WalletResponse from(Wallet wallet, WalletBalance balance) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getWalletNumber(),
                wallet.getCurrency(),
                wallet.getStatus(),
                new Balance(
                        balance.getLedgerBalance(),
                        balance.getAvailableBalance(),
                        balance.getReservedBalance()));
    }

    public record Balance(
            BigDecimal ledgerBalance,
            BigDecimal availableBalance,
            BigDecimal reservedBalance) {
    }
}
