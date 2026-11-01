package com.nurudeen.ussd_wallet.externalbank.entity;

import com.nurudeen.ussd_wallet.externalbank.enums.ReversalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "transfer_reversals")
public class TransferReversal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "transfer_id", nullable = false, unique = true)
    private ExternalTransfer transfer;

    @Column(name = "reversal_reference", nullable = false, unique = true, length = 40)
    private String reversalReference;

    @Column(name = "reversal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal reversalAmount;

    @Column(nullable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReversalStatus status = ReversalStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TransferReversal() {
    }

    public TransferReversal(ExternalTransfer transfer, String reversalReference, BigDecimal reversalAmount, String reason) {
        this.transfer = transfer;
        this.reversalReference = reversalReference;
        this.reversalAmount = reversalAmount;
        this.reason = reason;
    }

    public String getReversalReference() {
        return reversalReference;
    }

    public BigDecimal getReversalAmount() {
        return reversalAmount;
    }

    public String getReason() {
        return reason;
    }

    public ReversalStatus getStatus() {
        return status;
    }

    public void complete() {
        this.status = ReversalStatus.COMPLETED;
        this.completedAt = Instant.now();
    }
}
