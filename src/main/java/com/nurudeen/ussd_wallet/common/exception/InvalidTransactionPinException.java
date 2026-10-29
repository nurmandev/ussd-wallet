package com.nurudeen.ussd_wallet.common.exception;

public class InvalidTransactionPinException extends RuntimeException {

    public InvalidTransactionPinException() {
        super("Invalid transaction PIN");
    }
}
