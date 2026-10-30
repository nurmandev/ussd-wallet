package com.nurudeen.ussd_wallet.wallet.service;

public class RecipientLookupRateLimitExceededException extends RuntimeException {

    public RecipientLookupRateLimitExceededException() {
        super("Too many recipient lookups. Please try again later.");
    }
}
