package com.nurudeen.ussd_wallet.externalbank.entity;

import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;
import com.nurudeen.ussd_wallet.externalbank.enums.ExternalTransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "external_transfers")
public class ExternalTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 40)
    private String transactionReference;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "destination_account_number_protected", nullable = false, length = 255)
    private String destinationAccountNumberProtected;

    @Column(name = "destination_account_name", nullable = false, length = 150)
    private String destinationAccountName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO.setScale(2);

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit;

    @Column(nullable = false, length = 3)
    private String currency = "NGN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalTransferStatus status = ExternalTransferStatus.INITIATED;

    @Column(name = "switch_reference", length = 40)
    private String switchReference;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ExternalTransfer() {
    }

    public ExternalTransfer(String transactionReference, UUID walletId, Bank bank, String destinationAccountNumberProtected,
            String destinationAccountName, BigDecimal amount, BigDecimal fee, BigDecimal totalDebit,
            String idempotencyKey, String requestFingerprint) {
        this.transactionReference = transactionReference;
        this.walletId = walletId;
        this.bank = bank;
        this.destinationAccountNumberProtected = destinationAccountNumberProtected;
        this.destinationAccountName = destinationAccountName;
        this.amount = amount;
        this.fee = fee;
        this.totalDebit = totalDebit;
        this.currency = "NGN";
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    public UUID getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public Bank getBank() {
        return bank;
    }

    public String getDestinationAccountNumberProtected() {
        return destinationAccountNumberProtected;
    }

    public String getDestinationAccountName() {
        return destinationAccountName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public String getCurrency() {
        return currency;
    }

    public ExternalTransferStatus getStatus() {
        return status;
    }

    public void setStatus(ExternalTransferStatus status) {
        this.status = status;
        if (status == ExternalTransferStatus.SUCCESSFUL || status == ExternalTransferStatus.FAILED || status == ExternalTransferStatus.REVERSED || status == ExternalTransferStatus.UNKNOWN) {
            this.completedAt = Instant.now();
        }
    }

    public String getSwitchReference() {
        return switchReference;
    }

    public void setSwitchReference(String switchReference) {
        this.switchReference = switchReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setStatusFromResponse(BankResponseCode responseCode, String responseMessage, String switchReference) {
        this.switchReference = switchReference;
        this.failureReason = responseMessage;
        switch (responseCode) {
            case SUCCESS -> this.status = ExternalTransferStatus.SUCCESSFUL;
            case PENDING -> this.status = ExternalTransferStatus.PENDING;
            case TIMEOUT -> this.status = ExternalTransferStatus.UNKNOWN;
            case BANK_UNAVAILABLE, FAILED, ACCOUNT_NOT_FOUND, DUPLICATE_TRANSACTION, INSUFFICIENT_BANK_FUNDS -> {
                this.status = ExternalTransferStatus.FAILED;
            }
        }
        if (this.status == ExternalTransferStatus.SUCCESSFUL || this.status == ExternalTransferStatus.FAILED || this.status == ExternalTransferStatus.UNKNOWN) {
            this.completedAt = Instant.now();
        }
    }
}
