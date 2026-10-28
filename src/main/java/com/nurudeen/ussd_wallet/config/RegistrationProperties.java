package com.nurudeen.ussd_wallet.config;

import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wallet.registration")
public record RegistrationProperties(
        CustomerStatus initialCustomerStatus,
        @Valid RateLimit rateLimit) {

    public RegistrationProperties {
        if (initialCustomerStatus == null) {
            initialCustomerStatus = CustomerStatus.PENDING;
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

    public record RateLimit(
            @Min(1) @Max(1000) int maxRequests,
            @Min(1) @Max(3600) int windowSeconds) {
    }
}
