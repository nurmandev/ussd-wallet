package com.nurudeen.ussd_wallet.wallet.dto;

import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;

public record RecipientWalletResponse(
        String walletNumber,
        String customerName,
        WalletStatus walletStatus,
        String currency) {
}
