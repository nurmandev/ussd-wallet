package com.nurudeen.ussd_wallet.common.exception;

public class PinConfirmationMismatchException extends RuntimeException {

    public PinConfirmationMismatchException() {
        super("Transaction PIN confirmation does not match");
    }
}
