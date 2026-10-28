package com.nurudeen.ussd_wallet.common.exception;

public class ResourceCreationException extends RuntimeException {

    public ResourceCreationException() {
        super("Registration could not be completed");
    }

    public ResourceCreationException(Throwable cause) {
        super("Registration could not be completed", cause);
    }
}
