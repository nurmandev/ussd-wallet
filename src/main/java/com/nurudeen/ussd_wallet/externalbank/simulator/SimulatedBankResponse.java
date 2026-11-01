package com.nurudeen.ussd_wallet.externalbank.simulator;

import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;

public record SimulatedBankResponse(
        BankResponseCode responseCode,
        String message,
        String switchReference,
        boolean accepted,
        long latencyMs) {
}
