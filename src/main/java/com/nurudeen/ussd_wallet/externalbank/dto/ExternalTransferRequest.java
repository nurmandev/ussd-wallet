package com.nurudeen.ussd_wallet.externalbank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ExternalTransferRequest(
        @NotBlank(message = "Bank code is required") String bankCode,
        @NotBlank(message = "Account number is required") String accountNumber,
        @NotBlank(message = "Resolution reference is required") String resolutionReference,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places") BigDecimal amount,
        @NotBlank(message = "Transaction PIN is required") String transactionPin,
        @NotBlank(message = "Idempotency key is required") String idempotencyKey) {
}
