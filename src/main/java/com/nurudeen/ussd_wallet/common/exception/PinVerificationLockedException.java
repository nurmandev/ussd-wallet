package com.nurudeen.ussd_wallet.common.exception;

public class PinVerificationLockedException extends RuntimeException {

    public PinVerificationLockedException() {
        super("Transaction PIN verification is temporarily locked");
    }
}
