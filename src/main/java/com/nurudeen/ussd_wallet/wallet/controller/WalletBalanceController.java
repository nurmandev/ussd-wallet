package com.nurudeen.ussd_wallet.wallet.controller;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import com.nurudeen.ussd_wallet.wallet.dto.WalletBalanceResponse;
import com.nurudeen.ussd_wallet.wallet.service.WalletBalanceService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletBalanceController {

    private final WalletBalanceService walletBalanceService;

    public WalletBalanceController(WalletBalanceService walletBalanceService) {
        this.walletBalanceService = walletBalanceService;
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new CustomerIdentityUnavailableException();
        }
        String requestId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.strip();
        if (requestId.length() > 128) {
            throw new IllegalArgumentException("Correlation ID is too long");
        }
        WalletBalanceResponse balance = walletBalanceService.getCustomerBalance(
                principal.customerId(), WalletBalanceService.Channel.REST, null, requestId);
        return ResponseEntity.ok(ApiResponse.success("Balance retrieved successfully", balance));
    }
}
