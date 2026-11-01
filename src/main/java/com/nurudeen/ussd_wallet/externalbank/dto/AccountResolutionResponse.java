package com.nurudeen.ussd_wallet.externalbank.dto;

public record AccountResolutionResponse(
        boolean success,
        String accountName,
        String bankName,
        String accountNumber,
        String resolutionReference,
        String status) {
}
