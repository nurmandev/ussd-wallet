package com.nurudeen.ussd_wallet.common.exception;

public class InvalidPinRecoveryAuthorizationException extends RuntimeException {

    public InvalidPinRecoveryAuthorizationException() {
        super("PIN recovery authorization is invalid or expired");
    }
}
