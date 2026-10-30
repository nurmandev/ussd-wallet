package com.nurudeen.ussd_wallet.wallet.entity;

import com.nurudeen.ussd_wallet.wallet.enums.TransferIntentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "wallet_transfer_intents")
public class WalletTransferIntent {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "sender_wallet_id", nullable = false)
    private UUID senderWalletId;

    @Column(name = "recipient_wallet_id", nullable = false)
    private UUID recipientWalletId;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit;

    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(length = 140)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TransferIntentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "transfer_reference", length = 40)
    private String transferReference;

    protected WalletTransferIntent() {
    }

    public WalletTransferIntent(
            UUID id,
            UUID customerId,
            UUID senderWalletId,
            UUID recipientWalletId,
            UUID idempotencyKey,
            BigDecimal amount,
            BigDecimal fee,
            String currency,
            String description,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.customerId = customerId;
        this.senderWalletId = senderWalletId;
        this.recipientWalletId = recipientWalletId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.fee = fee;
        this.totalDebit = amount.add(fee);
        this.currency = currency;
        this.description = description;
        this.status = TransferIntentStatus.PENDING_CONFIRMATION;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getSenderWalletId() {
        return senderWalletId;
    }

    public UUID getRecipientWalletId() {
        return recipientWalletId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
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

    public String getDescription() {
        return description;
    }

    public TransferIntentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getTransferReference() {
        return transferReference;
    }

    public boolean matches(UUID senderId, UUID recipientId, BigDecimal requestedAmount, String requestedDescription) {
        return senderWalletId.equals(senderId)
                && recipientWalletId.equals(recipientId)
                && amount.compareTo(requestedAmount) == 0
                && java.util.Objects.equals(description, requestedDescription);
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void complete(String reference, Instant now) {
        requirePending();
        status = TransferIntentStatus.COMPLETED;
        transferReference = reference;
        completedAt = now;
    }

    public void cancel(Instant now) {
        requirePending();
        status = TransferIntentStatus.CANCELLED;
        completedAt = now;
    }

    public void expire(Instant now) {
        requirePending();
        status = TransferIntentStatus.EXPIRED;
        completedAt = now;
    }

    private void requirePending() {
        if (status != TransferIntentStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Transfer intent is no longer pending confirmation");
        }
    }
}
