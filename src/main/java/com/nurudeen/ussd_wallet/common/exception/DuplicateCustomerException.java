package com.nurudeen.ussd_wallet.common.exception;

public class DuplicateCustomerException extends RuntimeException {

    public DuplicateCustomerException() {
        super("A customer with this phone number is already registered");
    }
}
