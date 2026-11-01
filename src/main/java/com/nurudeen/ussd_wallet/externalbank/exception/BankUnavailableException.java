package com.nurudeen.ussd_wallet.externalbank.exception;

public class BankUnavailableException extends RuntimeException {
    public BankUnavailableException(String message) {
        super(message);
    }
}
