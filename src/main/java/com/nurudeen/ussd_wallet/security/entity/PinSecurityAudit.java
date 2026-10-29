package com.nurudeen.ussd_wallet.security.entity;

import com.nurudeen.ussd_wallet.security.enums.PinSecurityEvent;
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
@Table(name = "pin_security_audit")
public class PinSecurityAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private PinSecurityEvent eventType;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PinSecurityAudit() {
    }

    public PinSecurityAudit(UUID customerId, UUID walletId, PinSecurityEvent eventType) {
        this.customerId = customerId;
        this.walletId = walletId;
        this.eventType = eventType;
    }
}
