package com.nurudeen.ussd_wallet.common.exception;

public class InvalidPhoneNumberException extends RuntimeException {

    public InvalidPhoneNumberException() {
        super("Enter a valid Nigerian phone number");
    }
}
