package com.nurudeen.ussd_wallet.common.exception;

public class WalletTransferConflictException extends RuntimeException {

    public WalletTransferConflictException(String message) {
        super(message);
    }
}
