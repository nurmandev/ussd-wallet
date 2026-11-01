package com.nurudeen.ussd_wallet.externalbank.controller;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.externalbank.dto.ExternalTransferRequest;
import com.nurudeen.ussd_wallet.externalbank.dto.ExternalTransferResponse;
import com.nurudeen.ussd_wallet.externalbank.dto.TransferHistoryResponse;
import com.nurudeen.ussd_wallet.externalbank.dto.TransferStatusResponse;
import com.nurudeen.ussd_wallet.externalbank.entity.ExternalTransfer;
import com.nurudeen.ussd_wallet.externalbank.repository.ExternalTransferRepository;
import com.nurudeen.ussd_wallet.externalbank.service.ExternalTransferService;
import com.nurudeen.ussd_wallet.externalbank.service.TransferStatusService;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ExternalTransferController {

    private final ExternalTransferService externalTransferService;
    private final TransferStatusService transferStatusService;
    private final ExternalTransferRepository transferRepository;

    public ExternalTransferController(
            ExternalTransferService externalTransferService,
            TransferStatusService transferStatusService,
            ExternalTransferRepository transferRepository) {
        this.externalTransferService = externalTransferService;
        this.transferStatusService = transferStatusService;
        this.transferRepository = transferRepository;
    }

    @PostMapping("/bank-transfers")
    public ResponseEntity<ApiResponse<ExternalTransferResponse>> create(
            Authentication authentication,
            @Valid @RequestBody ExternalTransferRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new CustomerIdentityUnavailableException();
        }
        ExternalTransferResponse response = externalTransferService.initiate(principal.customerId(), request);
        return ResponseEntity.ok(ApiResponse.success("External transfer processed", response));
    }

    @GetMapping("/bank-transfers/{transactionReference}")
    public ResponseEntity<ApiResponse<TransferStatusResponse>> status(
            Authentication authentication,
            @PathVariable String transactionReference) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new CustomerIdentityUnavailableException();
        }
        TransferStatusResponse response = transferStatusService.fetch(principal.customerId(), transactionReference);
        return ResponseEntity.ok(ApiResponse.success("Transfer status retrieved", response));
    }

    @GetMapping("/bank-transfers/history")
    public ResponseEntity<ApiResponse<TransferHistoryResponse>> history(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new CustomerIdentityUnavailableException();
        }
        var items = transferRepository.findByWalletIdOrderByCreatedAtDesc(principal.customerId()).stream()
                .map(transfer -> new TransferStatusResponse(
                        transfer.getTransactionReference(),
                        transfer.getStatus().name(),
                        transfer.getAmount(),
                        transfer.getFee(),
                        transfer.getTotalDebit(),
                        transfer.getCurrency(),
                        transfer.getBank().getBankName(),
                        transfer.getDestinationAccountNumberProtected(),
                        transfer.getDestinationAccountName(),
                        transfer.getCreatedAt(),
                        transfer.getCompletedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Transfer history retrieved", new TransferHistoryResponse(items)));
    }

    @PostMapping("/bank-transfers/{transactionReference}/status-enquiry")
    public ResponseEntity<ApiResponse<TransferStatusResponse>> statusEnquiry(
            Authentication authentication,
            @PathVariable String transactionReference) {
        return status(authentication, transactionReference);
    }
}
