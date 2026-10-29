package com.nurudeen.ussd_wallet.common.exception;

public class PinReuseException extends RuntimeException {

    public PinReuseException() {
        super("New transaction PIN must be different from the current PIN");
    }
}
