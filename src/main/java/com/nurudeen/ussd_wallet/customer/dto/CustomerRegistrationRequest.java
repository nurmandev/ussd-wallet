package com.nurudeen.ussd_wallet.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRegistrationRequest(
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "[+0-9()\\s-]+", message = "Enter a valid Nigerian phone number")
        String phoneNumber) {
}
