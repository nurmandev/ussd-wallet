package com.nurudeen.ussd_wallet.wallet.service;

public class TransferLimitExceededException extends RuntimeException {

    public TransferLimitExceededException(String message) {
        super(message);
    }
}
