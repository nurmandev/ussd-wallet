package com.nurudeen.ussd_wallet.security.dto;

import java.time.Instant;

public record PinStatusResponse(
        boolean pinConfigured,
        boolean verificationLocked,
        Instant lockExpiresAt) {
}
