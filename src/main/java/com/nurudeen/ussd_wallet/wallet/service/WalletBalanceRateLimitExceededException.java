package com.nurudeen.ussd_wallet.wallet.service;

public class WalletBalanceRateLimitExceededException extends RuntimeException {

    public WalletBalanceRateLimitExceededException() {
        super("Too many balance enquiries. Please try again later.");
    }
}
