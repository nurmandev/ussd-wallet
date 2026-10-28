package com.nurudeen.ussd_wallet.customer.controller;

import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.customer.dto.CustomerRegistrationRequest;
import com.nurudeen.ussd_wallet.customer.dto.CustomerRegistrationResponse;
import com.nurudeen.ussd_wallet.customer.service.CustomerRegistrationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerRegistrationController {

    private final CustomerRegistrationService registrationService;

    public CustomerRegistrationController(CustomerRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a customer by Nigerian phone number",
            description = "Creates a pending customer, wallet, zero balance, and wallet liability account atomically. Phone ownership is not verified by registration.")
    public ResponseEntity<ApiResponse<CustomerRegistrationResponse>> register(
            @Valid @RequestBody CustomerRegistrationRequest request) {
        CustomerRegistrationResponse response = registrationService.register(request.phoneNumber());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer registered and wallet created successfully", response));
    }
}
