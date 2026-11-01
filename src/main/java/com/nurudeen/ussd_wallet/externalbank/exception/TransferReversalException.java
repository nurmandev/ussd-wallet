package com.nurudeen.ussd_wallet.externalbank.exception;

public class TransferReversalException extends RuntimeException {
    public TransferReversalException(String message) {
        super(message);
    }
}
