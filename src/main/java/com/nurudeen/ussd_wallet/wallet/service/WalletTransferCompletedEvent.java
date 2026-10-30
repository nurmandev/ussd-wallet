package com.nurudeen.ussd_wallet.wallet.service;

import java.math.BigDecimal;

public record WalletTransferCompletedEvent(
        String reference,
        String senderWalletNumber,
        String recipientWalletNumber,
        BigDecimal amount,
        String currency) {
}
