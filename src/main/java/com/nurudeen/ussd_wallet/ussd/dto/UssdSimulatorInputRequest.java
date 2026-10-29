package com.nurudeen.ussd_wallet.ussd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UssdSimulatorInputRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+") String sessionId,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+") String callbackId,
        @Size(max = 160) String text,
        @NotBlank @Size(max = 32) String phoneNumber) {
}
