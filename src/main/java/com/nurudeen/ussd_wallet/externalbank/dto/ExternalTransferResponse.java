package com.nurudeen.ussd_wallet.externalbank.dto;

import java.math.BigDecimal;

public record ExternalTransferResponse(
        String transactionReference,
        String status,
        String destinationBank,
        String destinationAccount,
        String accountName,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal totalDebit,
        String currency,
        String message,
        String switchReference) {
}
