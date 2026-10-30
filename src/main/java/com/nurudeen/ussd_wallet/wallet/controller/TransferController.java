package com.nurudeen.ussd_wallet.wallet.controller;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import com.nurudeen.ussd_wallet.wallet.dto.TransferConfirmationRequest;
import com.nurudeen.ussd_wallet.wallet.dto.TransferConfirmationResponse;
import com.nurudeen.ussd_wallet.wallet.dto.TransferHistoryPage;
import com.nurudeen.ussd_wallet.wallet.dto.TransferIntentResponse;
import com.nurudeen.ussd_wallet.wallet.dto.TransferReceiptResponse;
import com.nurudeen.ussd_wallet.wallet.dto.TransferValidationRequest;
import com.nurudeen.ussd_wallet.wallet.service.TransferIntentService;
import com.nurudeen.ussd_wallet.wallet.service.WalletTransferQueryService;
import com.nurudeen.ussd_wallet.wallet.service.WalletTransferService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferIntentService intentService;
    private final WalletTransferQueryService queryService;

    public TransferController(
            TransferIntentService intentService, WalletTransferQueryService queryService) {
        this.intentService = intentService;
        this.queryService = queryService;
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<TransferIntentResponse>> validate(
            Authentication authentication, @Valid @RequestBody TransferValidationRequest request) {
        var result = intentService.createIntent(customerId(authentication), request);
        return ResponseEntity.ok(ApiResponse.success("Please review your transfer", result));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<TransferConfirmationResponse>> confirm(
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody TransferConfirmationRequest request) {
        String requestId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.strip();
        if (requestId.length() > 128) {
            throw new IllegalArgumentException("Correlation ID is too long");
        }
        var result = intentService.confirm(
                customerId(authentication),
                request,
                WalletTransferService.TransferChannel.REST,
                requestId);
        return ResponseEntity.ok(ApiResponse.success("Transfer completed successfully", result));
    }

    @DeleteMapping("/intents/{intentId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            Authentication authentication, @PathVariable UUID intentId) {
        intentService.cancel(customerId(authentication), intentId);
        return ResponseEntity.ok(ApiResponse.success("Transfer intent cancelled", null));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<TransferHistoryPage>> history(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "Transfer history retrieved",
                queryService.history(customerId(authentication), page, size)));
    }

    @GetMapping("/{reference}/receipt")
    public ResponseEntity<ApiResponse<TransferReceiptResponse>> receipt(
            Authentication authentication, @PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(
                "Transfer receipt retrieved",
                queryService.receipt(customerId(authentication), reference)));
    }

    @GetMapping("/{reference}")
    public ResponseEntity<ApiResponse<TransferReceiptResponse>> details(
            Authentication authentication, @PathVariable String reference) {
        return receipt(authentication, reference);
    }

    private UUID customerId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new CustomerIdentityUnavailableException();
        }
        return principal.customerId();
    }
}
