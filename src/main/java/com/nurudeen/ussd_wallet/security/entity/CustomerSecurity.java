package com.nurudeen.ussd_wallet.security.entity;

import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.security.enums.PinStatus;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "pin_status", nullable = false, length = 20)
    private PinStatus pinStatus = PinStatus.NOT_SET;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerSecurity() {
    }

    public CustomerSecurity(Customer customer) {
        this.customer = customer;
        this.pinStatus = PinStatus.NOT_SET;
    }
}
