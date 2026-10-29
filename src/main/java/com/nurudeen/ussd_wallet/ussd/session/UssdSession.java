package com.nurudeen.ussd_wallet.ussd.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ussd_sessions")
public class UssdSession {

    @Id
    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "service_code", nullable = false, length = 20)
    private String serviceCode;

    @Column(name = "network_code", nullable = false, length = 32)
    private String networkCode;

    @Column(name = "current_menu", nullable = false, length = 40)
    private String currentMenu;

    @Column(name = "current_step", nullable = false, length = 40)
    private String currentStep;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> sessionData = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 20)
    private UssdSessionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UssdSession() {
    }

    public UssdSession(
            String sessionId,
            String phoneNumber,
            String serviceCode,
            String networkCode,
            String currentMenu,
            String currentStep,
            UssdSessionStatus status,
            Instant now,
            Instant expiresAt) {
        this.sessionId = sessionId;
        this.phoneNumber = phoneNumber;
        this.serviceCode = serviceCode;
        this.networkCode = networkCode;
        this.currentMenu = currentMenu;
        this.currentStep = currentStep;
        this.status = status;
        this.startedAt = now;
        this.lastActivityAt = now;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getNetworkCode() {
        return networkCode;
    }

    public String getCurrentMenu() {
        return currentMenu;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public Map<String, String> getSessionData() {
        return sessionData;
    }

    public UssdSessionStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isOpen() {
        return status == UssdSessionStatus.ACTIVE
                || status == UssdSessionStatus.WAITING_FOR_INPUT
                || status == UssdSessionStatus.PROCESSING;
    }

    public void advance(
            String menu,
            String step,
            UssdSessionStatus newStatus,
            Instant now,
            Instant newExpiresAt) {
        currentMenu = menu;
        currentStep = step;
        status = newStatus;
        lastActivityAt = now;
        expiresAt = newExpiresAt;
        if (newStatus == UssdSessionStatus.COMPLETED
                || newStatus == UssdSessionStatus.CANCELLED
                || newStatus == UssdSessionStatus.FAILED
                || newStatus == UssdSessionStatus.EXPIRED) {
            completedAt = now;
        }
    }
}
