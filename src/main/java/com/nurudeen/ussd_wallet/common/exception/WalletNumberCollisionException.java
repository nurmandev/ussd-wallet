package com.nurudeen.ussd_wallet.common.exception;

public class WalletNumberCollisionException extends RuntimeException {

    public WalletNumberCollisionException() {
        super("Wallet number collision");
    }
}
