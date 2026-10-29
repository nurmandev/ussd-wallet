package com.nurudeen.ussd_wallet.security.entity;

import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.security.enums.PinStatus;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "customer_security")
public class CustomerSecurity {

    @Id
    private UUID customerId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @OneToOne(optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, unique = true)
    private Wallet wallet;

    @Column(name = "pin_hash", length = 255)
    private String pinHash;

    @Column(name = "pin_set_at")
    private Instant pinSetAt;

    @Column(name = "pin_changed_at")
    private Instant pinChangedAt;

    @Column(name = "failed_pin_attempts", nullable = false)
    private int failedPinAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_successful_verification")
    private Instant lastSuccessfulVerification;

    @Enumerated(EnumType.STRING)
    @Column(name = "pin_status", nullable = false, length = 20)
    private PinStatus pinStatus = PinStatus.NOT_SET;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerSecurity() {
    }

    public CustomerSecurity(Customer customer, Wallet wallet) {
        this.customer = customer;
        this.wallet = wallet;
        this.pinStatus = PinStatus.NOT_SET;
    }

    public String getPinHash() {
        return pinHash;
    }

    public PinStatus getPinStatus() {
        return pinStatus;
    }

    public int getFailedPinAttempts() {
        return failedPinAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastSuccessfulVerification() {
        return lastSuccessfulVerification;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getWalletId() {
        return wallet.getId();
    }

    public boolean isPinConfigured() {
        return pinHash != null;
    }

    public void configurePin(String encodedPin, Instant now) {
        pinHash = encodedPin;
        pinSetAt = now;
        pinStatus = PinStatus.ACTIVE;
        resetFailureState();
    }

    public boolean recordFailedAttempt(int maximumAttempts, Instant lockExpiresAt) {
        failedPinAttempts++;
        if (failedPinAttempts >= maximumAttempts) {
            pinStatus = PinStatus.LOCKED;
            lockedUntil = lockExpiresAt;
            return true;
        }
        return false;
    }

    public void recordSuccessfulVerification(Instant now) {
        lastSuccessfulVerification = now;
        pinStatus = PinStatus.ACTIVE;
        resetFailureState();
    }

    public void replacePin(String encodedPin, Instant now) {
        pinHash = encodedPin;
        pinChangedAt = now;
        recordSuccessfulVerification(now);
    }

    public void unlockExpiredLockout() {
        if (pinStatus == PinStatus.LOCKED) {
            pinStatus = PinStatus.ACTIVE;
            resetFailureState();
        }
    }

    private void resetFailureState() {
        failedPinAttempts = 0;
        lockedUntil = null;
    }
}
