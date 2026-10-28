package com.nurudeen.ussd_wallet.common.exception;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException() {
        super("Wallet was not found for the authenticated customer");
    }
}
