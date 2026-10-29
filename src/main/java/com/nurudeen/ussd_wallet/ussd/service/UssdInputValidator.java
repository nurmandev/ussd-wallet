package com.nurudeen.ussd_wallet.ussd.service;

import com.nurudeen.ussd_wallet.config.TransferProperties;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class UssdInputValidator {

    private static final Pattern WALLET_NUMBER = Pattern.compile("[0-9]{8,30}");

    public String input(String input) {
        if (input == null || input.length() > 160 || input.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid input");
        }
        return input.strip();
    }

    public String walletNumber(String input) {
        String value = input(input);
        if (!WALLET_NUMBER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid wallet number");
        }
        return value;
    }

    private final TransferProperties transferProperties;

    public UssdInputValidator(TransferProperties transferProperties) {
        this.transferProperties = transferProperties;
    }

    public BigDecimal amount(String input) {
        String value = input(input);
        if (!value.matches("[0-9]{1,9}(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Enter a valid amount");
        }
        BigDecimal amount = new BigDecimal(value).setScale(2);
        if (amount.compareTo(transferProperties.minimumAmount()) < 0
                || amount.compareTo(transferProperties.maximumAmount()) > 0) {
            throw new IllegalArgumentException("Amount is outside the permitted transfer limits");
        }
        return amount;
    }
}
