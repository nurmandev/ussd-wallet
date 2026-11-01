package com.nurudeen.ussd_wallet.externalbank.entity;

import com.nurudeen.ussd_wallet.externalbank.enums.AccountResolutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "account_resolutions")
public class AccountResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resolution_reference", nullable = false, unique = true, length = 40)
    private String resolutionReference;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "bank_id", nullable = false)
    private UUID bankId;

    @Column(name = "account_number_hash", nullable = false, length = 128)
    private String accountNumberHash;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private AccountResolutionStatus status = AccountResolutionStatus.VERIFIED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountResolution() {
    }

    public AccountResolution(UUID customerId, UUID bankId, String resolutionReference, String accountNumberHash,
            String accountName, Instant expiresAt) {
        this.customerId = customerId;
        this.bankId = bankId;
        this.resolutionReference = resolutionReference;
        this.accountNumberHash = accountNumberHash;
        this.accountName = accountName;
        this.expiresAt = expiresAt;
        this.status = AccountResolutionStatus.VERIFIED;
    }

    public UUID getId() {
        return id;
    }

    public String getResolutionReference() {
        return resolutionReference;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getBankId() {
        return bankId;
    }

    public String getAccountNumberHash() {
        return accountNumberHash;
    }

    public String getAccountName() {
        return accountName;
    }

    public AccountResolutionStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt) || now.equals(expiresAt);
    }

    public void expire() {
        this.status = AccountResolutionStatus.EXPIRED;
    }
}
