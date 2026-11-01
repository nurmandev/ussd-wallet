package com.nurudeen.ussd_wallet.externalbank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "external_bank_accounts")
public class ExternalBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "account_number_hash", nullable = false, length = 128)
    private String accountNumberHash;

    @Column(name = "masked_account_number", nullable = false, length = 20)
    private String maskedAccountNumber;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExternalBankAccount() {
    }

    public ExternalBankAccount(Bank bank, String accountNumberHash, String maskedAccountNumber, String accountName) {
        this.bank = bank;
        this.accountNumberHash = accountNumberHash;
        this.maskedAccountNumber = maskedAccountNumber;
        this.accountName = accountName;
        this.status = "ACTIVE";
    }

    public UUID getId() {
        return id;
    }

    public Bank getBank() {
        return bank;
    }

    public String getAccountNumberHash() {
        return accountNumberHash;
    }

    public String getMaskedAccountNumber() {
        return maskedAccountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getStatus() {
        return status;
    }
}
