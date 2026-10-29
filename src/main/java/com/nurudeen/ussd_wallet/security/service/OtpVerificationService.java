package com.nurudeen.ussd_wallet.security.service;

import java.util.UUID;

public interface OtpVerificationService {

    String PIN_RECOVERY_PURPOSE = "TRANSACTION_PIN_RECOVERY";

    void requestOtp(UUID authenticatedCustomerId, String purpose);

    /**
     * Invalidates every outstanding challenge for the authenticated customer and purpose.
     * Implementations must make this operation durable and must not expose challenge data.
     */
    void invalidate(UUID authenticatedCustomerId, String purpose);

    /**
     * Verifies and atomically consumes a customer- and purpose-bound OTP.
     * Implementations must enforce expiry, attempt limits, rate limits, one-time use, and never log OTP values.
     */
    boolean verifyAndConsume(UUID authenticatedCustomerId, String purpose, String otp);
}
