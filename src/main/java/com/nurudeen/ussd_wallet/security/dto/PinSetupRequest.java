package com.nurudeen.ussd_wallet.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PinSetupRequest(
        @NotBlank @Pattern(regexp = "[0-9]{4,6}", message = "PIN must contain 4 to 6 digits")
        String pin,
        @NotBlank @Pattern(regexp = "[0-9]{4,6}", message = "PIN confirmation must contain 4 to 6 digits")
        String confirmPin) {
}
