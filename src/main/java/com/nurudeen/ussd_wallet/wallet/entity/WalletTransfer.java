package com.nurudeen.ussd_wallet.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "wallet_transfers")
public class WalletTransfer {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(name = "sender_wallet_id", nullable = false)
    private UUID senderWalletId;

    @Column(name = "recipient_wallet_id", nullable = false)
    private UUID recipientWalletId;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO.setScale(2);

    @Column(length = 140)
    private String description;

    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransfer() {
    }

    public WalletTransfer(
            UUID id,
            UUID senderWalletId,
            UUID recipientWalletId,
            UUID idempotencyKey,
            BigDecimal amount,
            BigDecimal fee,
            String currency,
            String description) {
        this.id = id;
        this.reference = "TXN-" + id;
        this.senderWalletId = senderWalletId;
        this.recipientWalletId = recipientWalletId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.fee = fee;
        this.currency = currency;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
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
        return amount.add(fee);
    }

    public String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
