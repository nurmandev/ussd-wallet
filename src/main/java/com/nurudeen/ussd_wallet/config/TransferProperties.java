package com.nurudeen.ussd_wallet.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "wallet.transfer")
public record TransferProperties(
        @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal minimumAmount,
        @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal maximumAmount) {

    public TransferProperties {
        if (minimumAmount == null) {
            minimumAmount = new BigDecimal("0.01");
        }
        if (maximumAmount == null) {
            maximumAmount = new BigDecimal("1000000.00");
        }
        if (maximumAmount.compareTo(minimumAmount) < 0) {
            throw new IllegalArgumentException("Maximum transfer amount must not be less than minimum amount");
        }
    }
}
