package com.nurudeen.ussd_wallet.common.exception;

public class PinNotConfiguredException extends RuntimeException {

    public PinNotConfiguredException() {
        super("Transaction PIN has not been configured");
    }
}
