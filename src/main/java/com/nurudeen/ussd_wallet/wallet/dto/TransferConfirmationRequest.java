package com.nurudeen.ussd_wallet.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record TransferConfirmationRequest(
        @NotNull UUID transferIntentId,
        @NotBlank @Pattern(regexp = "[0-9]{4,6}") String transactionPin,
        @NotNull UUID idempotencyKey) {
}
