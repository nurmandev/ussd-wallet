package com.nurudeen.ussd_wallet.ussd.session;

public enum UssdSessionStatus {
    ACTIVE,
    WAITING_FOR_INPUT,
    PROCESSING,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    FAILED
}
