package com.nurudeen.ussd_wallet.externalbank.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "externalbank.transfer")
public record TransferConfiguration(
        BigDecimal flatFee,
        BigDecimal percentageFee,
        BigDecimal minimumFee,
        BigDecimal maximumFee,
        BigDecimal maxAmount,
        long resolutionExpiryMinutes) {

    public TransferConfiguration {
        if (flatFee == null) flatFee = BigDecimal.ZERO.setScale(2);
        if (percentageFee == null) percentageFee = BigDecimal.ZERO.setScale(2);
        if (minimumFee == null) minimumFee = BigDecimal.ZERO.setScale(2);
        if (maximumFee == null) maximumFee = new BigDecimal("5000.00");
        if (maxAmount == null) maxAmount = new BigDecimal("1000000.00");
        if (resolutionExpiryMinutes <= 0) resolutionExpiryMinutes = 15;
    }
}
