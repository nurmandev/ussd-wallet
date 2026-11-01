package com.nurudeen.ussd_wallet.externalbank.controller;

import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.externalbank.dto.BankResponse;
import com.nurudeen.ussd_wallet.externalbank.service.BankService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BankController {

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    @GetMapping("/banks")
    public ResponseEntity<ApiResponse<List<BankResponse>>> listBanks() {
        return ResponseEntity.ok(ApiResponse.success("Bank directory retrieved", bankService.listActiveBanks()));
    }

    @GetMapping("/banks/{bankCode}")
    public ResponseEntity<ApiResponse<BankResponse>> getBank(@PathVariable String bankCode) {
        BankResponse response = bankService.findByBankCode(bankCode)
                .map(bank -> new BankResponse(bank.getBankCode(), bank.getBankName(), bank.getStatus(), bank.getSupportedTransferChannel()))
                .orElseThrow(() -> new IllegalArgumentException("Bank not found"));
        return ResponseEntity.ok(ApiResponse.success("Bank retrieved", response));
    }
}
