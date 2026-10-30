package com.nurudeen.ussd_wallet.wallet.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wallet.balance")
public record WalletBalanceProperties(
        @Min(1) @Max(1000) int maxRequestsPerMinute) {
}
