package com.nurudeen.ussd_wallet.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;

public record WalletTransferRequest(
        @NotBlank
                @Pattern(regexp = "[0-9]{8,30}", message = "Recipient wallet number is invalid")
                String recipientWalletNumber,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[0-9]{4,6}", message = "PIN must contain 4 to 6 digits")
                String pin,
        @NotNull UUID idempotencyKey) {
}
