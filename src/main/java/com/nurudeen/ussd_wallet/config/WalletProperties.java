package com.nurudeen.ussd_wallet.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(
        @Valid Number number,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {

    public WalletProperties {
        if (number == null) {
            number = new Number("10", 10, 5);
        }
        if (currency == null) {
            currency = "NGN";
        }
        if (number.prefix().length() >= number.length()) {
            throw new IllegalArgumentException("Wallet number prefix must be shorter than its total length");
        }
    }

    public record Number(
            @NotBlank @Pattern(regexp = "[0-9]{1,6}") String prefix,
            @Min(8) @Max(30) int length,
            @Min(1) @Max(20) int maxRetries) {
    }
}
