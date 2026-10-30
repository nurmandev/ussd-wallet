package com.nurudeen.ussd_wallet.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_transfer_audit")
public class WalletTransferAudit {

    @Id
    private UUID id;

    @Column(name = "transfer_reference", nullable = false, length = 40)
    private String transferReference;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "sender_wallet_id", nullable = false)
    private UUID senderWalletId;

    @Column(name = "recipient_wallet_id", nullable = false)
    private UUID recipientWalletId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, length = 10)
    private String channel;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected WalletTransferAudit() {
    }

    public WalletTransferAudit(
            WalletTransfer transfer,
            UUID customerId,
            WalletTransferServiceChannel channel,
            String correlationId,
            Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.transferReference = transfer.getReference();
        this.customerId = customerId;
        this.senderWalletId = transfer.getSenderWalletId();
        this.recipientWalletId = transfer.getRecipientWalletId();
        this.amount = transfer.getAmount();
        this.fee = transfer.getFee();
        this.status = "SUCCESSFUL";
        this.channel = channel.name();
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public enum WalletTransferServiceChannel {
        REST,
        USSD
    }
}
