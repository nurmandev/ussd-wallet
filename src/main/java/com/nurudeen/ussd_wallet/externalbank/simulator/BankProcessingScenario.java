package com.nurudeen.ussd_wallet.externalbank.simulator;

import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;

public record BankProcessingScenario(
        BankResponseCode responseCode,
        String message,
        long latencyMs,
        boolean bankAvailable) {
}
