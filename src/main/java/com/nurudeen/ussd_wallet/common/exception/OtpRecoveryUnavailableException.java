package com.nurudeen.ussd_wallet.common.exception;

public class OtpRecoveryUnavailableException extends RuntimeException {

    public OtpRecoveryUnavailableException() {
        super("PIN recovery is unavailable because identity verification is not configured");
    }
}
