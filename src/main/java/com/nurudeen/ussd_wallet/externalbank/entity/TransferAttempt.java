package com.nurudeen.ussd_wallet.externalbank.entity;

import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;
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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "transfer_attempts")
public class TransferAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private ExternalTransfer transfer;

    @Column(name = "attempt_reference", nullable = false, unique = true, length = 40)
    private String attemptReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "switch_response_code", nullable = false, length = 30)
    private BankResponseCode switchResponseCode;

    @Column(name = "switch_response_message", length = 255)
    private String switchResponseMessage;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus = "ATTEMPTED";

    @CreationTimestamp
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected TransferAttempt() {
    }

    public TransferAttempt(ExternalTransfer transfer, String attemptReference, BankResponseCode switchResponseCode,
            String switchResponseMessage, String processingStatus) {
        this.transfer = transfer;
        this.attemptReference = attemptReference;
        this.switchResponseCode = switchResponseCode;
        this.switchResponseMessage = switchResponseMessage;
        this.processingStatus = processingStatus;
    }
}
