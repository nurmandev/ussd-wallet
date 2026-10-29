package com.nurudeen.ussd_wallet.ussd.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ussd")
public record UssdProperties(
        @NotBlank @Size(max = 20) String serviceCode,
        @Min(30) @Max(900) int sessionTimeoutSeconds,
        @Min(60) @Max(2592000) int sessionRetentionSeconds,
        @Min(1) @Max(1000) int maxRequestsPerMinute,
        @NotBlank @Size(min = 32, max = 1024) String gatewaySecret,
        String simulatorSecret,
        boolean simulatorEnabled) {
}
