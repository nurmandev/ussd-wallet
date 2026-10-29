package com.nurudeen.ussd_wallet.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PinResetVerificationRequest(
        @NotBlank @Pattern(regexp = "[0-9]{4,10}", message = "OTP format is invalid")
        String otp) {
}
