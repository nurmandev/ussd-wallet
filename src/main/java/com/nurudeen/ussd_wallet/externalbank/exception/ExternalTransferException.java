package com.nurudeen.ussd_wallet.externalbank.exception;

public class ExternalTransferException extends RuntimeException {
    public ExternalTransferException(String message) {
        super(message);
    }
}
