package com.nurudeen.ussd_wallet.common.exception;

public class WalletAlreadyExistsException extends RuntimeException {

    public WalletAlreadyExistsException() {
        super("A wallet already exists for this customer");
    }
}
