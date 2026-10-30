package com.nurudeen.ussd_wallet.wallet.controller;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import com.nurudeen.ussd_wallet.wallet.dto.RecipientWalletResponse;
import com.nurudeen.ussd_wallet.wallet.service.RecipientWalletLookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet/lookup")
public class RecipientWalletLookupController {

    private final RecipientWalletLookupService lookupService;

    public RecipientWalletLookupController(RecipientWalletLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/{walletNumber}")
    public ResponseEntity<ApiResponse<RecipientWalletResponse>> lookup(
            Authentication authentication, @PathVariable String walletNumber) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new CustomerIdentityUnavailableException();
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Recipient found",
                lookupService.lookup(principal.customerId(), walletNumber)));
    }
}
