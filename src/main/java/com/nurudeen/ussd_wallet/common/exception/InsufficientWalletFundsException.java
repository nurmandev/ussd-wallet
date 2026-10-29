package com.nurudeen.ussd_wallet.common.exception;

public class InsufficientWalletFundsException extends RuntimeException {

    public InsufficientWalletFundsException() {
        super("Insufficient available balance");
    }
}
