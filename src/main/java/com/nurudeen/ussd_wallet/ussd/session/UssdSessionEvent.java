package com.nurudeen.ussd_wallet.ussd.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ussd_session_events")
public class UssdSessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "from_step", length = 40)
    private String fromStep;

    @Column(name = "to_step", length = 40)
    private String toStep;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected UssdSessionEvent() {
    }

    public UssdSessionEvent(String sessionId, String eventType, String fromStep, String toStep) {
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.fromStep = fromStep;
        this.toStep = toStep;
    }
}
