package com.nurudeen.ussd_wallet.ussd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UssdGatewayRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+") String sessionId,
        @NotBlank @Pattern(regexp = "\\*[0-9]{2,6}#") String serviceCode,
        @NotBlank @Size(max = 32) String phoneNumber,
        @Size(max = 160) String text,
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_-]+") String networkCode,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+") String callbackId) {
}
