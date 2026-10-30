package com.nurudeen.ussd_wallet.wallet.service;

public class TransferIntentExpiredException extends RuntimeException {

    public TransferIntentExpiredException() {
        super("Transfer confirmation expired. Start a new transfer.");
    }
}
