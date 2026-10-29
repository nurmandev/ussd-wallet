package com.nurudeen.ussd_wallet.common.exception;

public class PinAlreadyConfiguredException extends RuntimeException {

    public PinAlreadyConfiguredException() {
        super("Transaction PIN has already been configured");
    }
}
