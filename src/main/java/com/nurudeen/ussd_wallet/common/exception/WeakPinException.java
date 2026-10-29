package com.nurudeen.ussd_wallet.common.exception;

public class WeakPinException extends RuntimeException {

    public WeakPinException() {
        super("Choose a less predictable transaction PIN");
    }
}
