package com.nurudeen.ussd_wallet.wallet.controller;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import com.nurudeen.ussd_wallet.wallet.dto.WalletResponse;
import com.nurudeen.ussd_wallet.wallet.service.WalletQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletQueryService walletQueryService;

    public WalletController(WalletQueryService walletQueryService) {
        this.walletQueryService = walletQueryService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomerPrincipal customerPrincipal)) {
            throw new CustomerIdentityUnavailableException();
        }

        WalletResponse wallet = walletQueryService.getForCustomer(customerPrincipal.customerId());
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully", wallet));
    }
}
