package com.nurudeen.ussd_wallet.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_balance_enquiry_audit")
public class WalletBalanceEnquiryAudit {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(nullable = false, length = 10)
    private String channel;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "result_category", nullable = false, length = 32)
    private String resultCategory;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected WalletBalanceEnquiryAudit() {
    }

    public WalletBalanceEnquiryAudit(
            UUID customerId,
            UUID walletId,
            String sessionId,
            String channel,
            String correlationId,
            String resultCategory,
            Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.walletId = walletId;
        this.sessionId = sessionId;
        this.channel = channel;
        this.correlationId = correlationId;
        this.resultCategory = resultCategory;
        this.occurredAt = occurredAt;
    }
}
