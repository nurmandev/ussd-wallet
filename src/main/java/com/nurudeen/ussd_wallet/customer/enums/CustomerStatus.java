package com.nurudeen.ussd_wallet.customer.enums;

public enum CustomerStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    CLOSED;

    public boolean isFinanciallyActive() {
        return this == ACTIVE;
    }
}
