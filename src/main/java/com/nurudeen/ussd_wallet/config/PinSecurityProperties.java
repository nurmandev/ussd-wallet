package com.nurudeen.ussd_wallet.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pin.security")
public record PinSecurityProperties(
        @Min(3) @Max(10) int maxFailedAttempts,
        @Min(1) @Max(1440) int lockoutMinutes,
        @Min(16) @Max(64) int argon2SaltLength,
        @Min(16) @Max(64) int argon2HashLength,
        @Min(1) @Max(4) int argon2Parallelism,
        @Min(16384) @Max(262144) int argon2MemoryKib,
        @Min(2) @Max(10) int argon2Iterations,
        @Min(1) @Max(30) int recoveryTokenMinutes,
        @NotBlank @Size(min = 32, max = 1024) String pepper) {
}
