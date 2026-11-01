package com.nurudeen.ussd_wallet.externalbank.controller;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.externalbank.dto.AccountResolutionRequest;
import com.nurudeen.ussd_wallet.externalbank.dto.AccountResolutionResponse;
import com.nurudeen.ussd_wallet.externalbank.service.AccountResolutionService;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountResolutionController {

    private final AccountResolutionService accountResolutionService;

    public AccountResolutionController(AccountResolutionService accountResolutionService) {
        this.accountResolutionService = accountResolutionService;
    }

    @PostMapping("/bank-transfers/account-resolution")
    public ResponseEntity<ApiResponse<AccountResolutionResponse>> resolve(
            Authentication authentication,
            @Valid @RequestBody AccountResolutionRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new CustomerIdentityUnavailableException();
        }
        AccountResolutionResponse response = accountResolutionService.resolve(principal.customerId(), request.bankCode(), request.accountNumber());
        return ResponseEntity.ok(ApiResponse.success("Account resolution completed", response));
    }
}
