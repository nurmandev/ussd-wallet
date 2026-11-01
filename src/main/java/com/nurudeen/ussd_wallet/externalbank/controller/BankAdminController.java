package com.nurudeen.ussd_wallet.externalbank.controller;

import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;
import com.nurudeen.ussd_wallet.externalbank.service.BankService;
import com.nurudeen.ussd_wallet.externalbank.simulator.BankFailureSimulator;
import com.nurudeen.ussd_wallet.externalbank.simulator.BankProcessingScenario;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class BankAdminController {

    private final BankService bankService;
    private final BankFailureSimulator simulator;

    public BankAdminController(BankService bankService, BankFailureSimulator simulator) {
        this.bankService = bankService;
        this.simulator = simulator;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/bank-simulator/banks/{bankCode}/availability")
    public ResponseEntity<ApiResponse<String>> setAvailability(
            @PathVariable String bankCode,
            @RequestBody AvailabilityRequest request) {
        bankService.updateBankAvailability(bankCode, request.active());
        return ResponseEntity.ok(ApiResponse.success("Bank availability updated", bankCode));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/bank-simulator/scenarios")
    public ResponseEntity<ApiResponse<String>> setScenario(@RequestBody ScenarioRequest request) {
        simulator.setScenario(request.bankCode(), BankResponseCode.valueOf(request.scenario()), request.message(), request.latencyMs(), request.bankAvailable());
        return ResponseEntity.ok(ApiResponse.success("Simulator scenario configured", request.bankCode()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/bank-simulator/transactions")
    public ResponseEntity<ApiResponse<String>> transactions() {
        return ResponseEntity.ok(ApiResponse.success("Bank simulator transactions are available for operators", "OK"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/bank-simulator/reconciliation")
    public ResponseEntity<ApiResponse<String>> reconciliation() {
        return ResponseEntity.ok(ApiResponse.success("Reconciliation report ready", "OK"));
    }

    public record AvailabilityRequest(boolean active) {
    }

    public record ScenarioRequest(String bankCode, String scenario, String message, long latencyMs, boolean bankAvailable) {
    }
}
