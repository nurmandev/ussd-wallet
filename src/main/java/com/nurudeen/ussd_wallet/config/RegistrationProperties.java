package com.nurudeen.ussd_wallet.config;

import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wallet.registration")
public record RegistrationProperties(
        CustomerStatus initialCustomerStatus,
        @Valid WalletNumber walletNumber,
        @Valid RateLimit rateLimit) {

    public RegistrationProperties {
        if (initialCustomerStatus == null) {
            initialCustomerStatus = CustomerStatus.PENDING;
        }
        if (walletNumber == null) {
            walletNumber = new WalletNumber("MG", 12, 5);
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(10, 60);
        }
        if (initialCustomerStatus != CustomerStatus.PENDING
                && initialCustomerStatus != CustomerStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Initial customer status must be PENDING or explicitly configured ACTIVE");
        }
    }

    public record WalletNumber(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,6}") String prefix,
            @Min(8) @Max(18) int randomDigits,
            @Min(1) @Max(20) int maxAttempts) {
    }

    public record RateLimit(
            @Min(1) @Max(1000) int maxRequests,
            @Min(1) @Max(3600) int windowSeconds) {
    }
}
