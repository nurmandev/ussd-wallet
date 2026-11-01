package com.nurudeen.ussd_wallet.externalbank.entity;

import com.nurudeen.ussd_wallet.externalbank.enums.BankStatus;
import com.nurudeen.ussd_wallet.externalbank.enums.TransferChannel;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "banks")
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bank_name", nullable = false, length = 150)
    private String bankName;

    @Column(name = "bank_code", nullable = false, unique = true, length = 30)
    private String bankCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankStatus status = BankStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "supported_transfer_channel", nullable = false, length = 25)
    private TransferChannel supportedTransferChannel = TransferChannel.BANK_TRANSFER;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Bank() {
    }

    public Bank(String bankName, String bankCode, BankStatus status, TransferChannel supportedTransferChannel) {
        this.bankName = bankName;
        this.bankCode = bankCode;
        this.status = status;
        this.supportedTransferChannel = supportedTransferChannel;
    }

    public UUID getId() {
        return id;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankCode() {
        return bankCode;
    }

    public BankStatus getStatus() {
        return status;
    }

    public void setStatus(BankStatus status) {
        this.status = status;
    }

    public TransferChannel getSupportedTransferChannel() {
        return supportedTransferChannel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
