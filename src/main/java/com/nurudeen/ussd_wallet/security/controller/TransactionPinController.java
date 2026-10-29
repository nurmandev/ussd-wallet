package com.nurudeen.ussd_wallet.security.controller;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.security.dto.PinChangeRequest;
import com.nurudeen.ussd_wallet.security.dto.PinRecoveryAuthorizationResponse;
import com.nurudeen.ussd_wallet.security.dto.PinResetCompleteRequest;
import com.nurudeen.ussd_wallet.security.dto.PinResetVerificationRequest;
import com.nurudeen.ussd_wallet.security.dto.PinSetupRequest;
import com.nurudeen.ussd_wallet.security.dto.PinStatusResponse;
import com.nurudeen.ussd_wallet.security.dto.PinVerificationRequest;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import com.nurudeen.ussd_wallet.security.service.PinRecoveryService;
import com.nurudeen.ussd_wallet.security.service.PinVerificationService;
import com.nurudeen.ussd_wallet.security.service.TransactionPinManagementService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet/pin")
public class TransactionPinController {

    private final TransactionPinManagementService managementService;
    private final PinVerificationService verificationService;
    private final PinRecoveryService recoveryService;

    public TransactionPinController(
            TransactionPinManagementService managementService,
            PinVerificationService verificationService,
            PinRecoveryService recoveryService) {
        this.managementService = managementService;
        this.verificationService = verificationService;
        this.recoveryService = recoveryService;
    }

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<Void>> setup(
            Authentication authentication,
            @Valid @RequestBody PinSetupRequest request) {
        managementService.setup(
                customerId(authentication), request.pin(), request.confirmPin());
        return ResponseEntity.ok(ApiResponse.success("Transaction PIN successfully configured", null));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(
            Authentication authentication,
            @Valid @RequestBody PinVerificationRequest request) {
        verificationService.verify(customerId(authentication), request.pin());
        return ResponseEntity.ok(ApiResponse.success("Transaction PIN verified successfully", null));
    }

    @PostMapping("/change")
    public ResponseEntity<ApiResponse<Void>> change(
            Authentication authentication,
            @Valid @RequestBody PinChangeRequest request) {
        managementService.change(
                customerId(authentication),
                request.currentPin(),
                request.newPin(),
                request.confirmPin());
        return ResponseEntity.ok(ApiResponse.success("Transaction PIN changed successfully", null));
    }

    @PostMapping("/reset/request")
    public ResponseEntity<ApiResponse<Void>> requestReset(Authentication authentication) {
        recoveryService.requestOtp(customerId(authentication));
        return ResponseEntity.accepted()
                .body(ApiResponse.success("PIN recovery verification requested", null));
    }

    @PostMapping("/reset/verify")
    public ResponseEntity<ApiResponse<PinRecoveryAuthorizationResponse>> verifyReset(
            Authentication authentication,
            @Valid @RequestBody PinResetVerificationRequest request) {
        PinRecoveryAuthorizationResponse response =
                recoveryService.verifyOtp(customerId(authentication), request.otp());
        return ResponseEntity.ok(ApiResponse.success("Identity verification succeeded", response));
    }

    @PostMapping("/reset/complete")
    public ResponseEntity<ApiResponse<Void>> completeReset(
            Authentication authentication,
            @Valid @RequestBody PinResetCompleteRequest request) {
        recoveryService.completeReset(
                customerId(authentication),
                request.recoveryToken(),
                request.newPin(),
                request.confirmPin());
        return ResponseEntity.ok(ApiResponse.success("Transaction PIN reset successfully", null));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<PinStatusResponse>> status(Authentication authentication) {
        PinStatusResponse response = managementService.status(customerId(authentication));
        return ResponseEntity.ok(ApiResponse.success("Transaction PIN status retrieved", response));
    }

    private UUID customerId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new CustomerIdentityUnavailableException();
        }
        return principal.customerId();
    }
}
