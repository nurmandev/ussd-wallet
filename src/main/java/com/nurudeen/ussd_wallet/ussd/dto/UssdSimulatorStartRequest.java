package com.nurudeen.ussd_wallet.ussd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UssdSimulatorStartRequest(
        @NotBlank @Pattern(regexp = "\\*[0-9]{2,6}#") String serviceCode,
        @NotBlank @jakarta.validation.constraints.Size(max = 32) String phoneNumber) {
}
