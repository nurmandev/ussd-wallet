package com.nurudeen.ussd_wallet.externalbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccountResolutionRequest(
        @NotBlank(message = "Bank code is required") String bankCode,
        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "[0-9]{6,10}", message = "Account number must contain 6 to 10 digits")
        String accountNumber) {
}
