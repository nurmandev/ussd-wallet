package com.nurudeen.ussd_wallet.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PinChangeRequest(
        @NotBlank @Pattern(regexp = "[0-9]{4,6}", message = "Current PIN must contain 4 to 6 digits")
        String currentPin,
        @NotBlank @Pattern(regexp = "[0-9]{4,6}", message = "New PIN must contain 4 to 6 digits")
        String newPin,
        @NotBlank @Pattern(regexp = "[0-9]{4,6}", message = "PIN confirmation must contain 4 to 6 digits")
        String confirmPin) {
}
