package com.nurudeen.ussd_wallet.common.exception;

public class CustomerIdentityUnavailableException extends RuntimeException {

    public CustomerIdentityUnavailableException() {
        super("Authenticated customer identity is unavailable");
    }
}
