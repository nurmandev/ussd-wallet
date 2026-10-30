package com.nurudeen.ussd_wallet.wallet.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wallet.transfer.limits")
public record TransferLimitProperties(
        @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal dailyAmount,
        @Min(1) int dailyCount,
        @Min(60) int intentExpirySeconds,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal fee) {

    public TransferLimitProperties {
        if (dailyAmount == null) {
            dailyAmount = new BigDecimal("2000000.00");
        }
        if (dailyCount == 0) {
            dailyCount = 20;
        }
        if (intentExpirySeconds == 0) {
            intentExpirySeconds = 300;
        }
        if (fee == null) {
            fee = BigDecimal.ZERO.setScale(2);
        }
    }
}
