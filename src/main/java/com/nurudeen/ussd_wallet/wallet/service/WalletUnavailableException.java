package com.nurudeen.ussd_wallet.wallet.service;

public class WalletUnavailableException extends RuntimeException {

    public WalletUnavailableException() {
        super("Your wallet is currently unavailable");
    }
}
