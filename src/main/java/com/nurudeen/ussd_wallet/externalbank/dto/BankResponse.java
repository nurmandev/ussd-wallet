package com.nurudeen.ussd_wallet.externalbank.dto;

import com.nurudeen.ussd_wallet.externalbank.enums.BankStatus;
import com.nurudeen.ussd_wallet.externalbank.enums.TransferChannel;

public record BankResponse(
        String bankCode,
        String bankName,
        BankStatus status,
        TransferChannel supportedTransferChannel) {
}
