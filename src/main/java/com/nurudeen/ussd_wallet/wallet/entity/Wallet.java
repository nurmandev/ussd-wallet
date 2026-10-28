package com.nurudeen.ussd_wallet.wallet.entity;

import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(name = "wallet_number", nullable = false, unique = true, length = 30)
    private String walletNumber;

    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status = WalletStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "wallet")
    private WalletBalance balance;

    protected Wallet() {
    }

    public Wallet(Customer customer, String walletNumber, String currency) {
        this.customer = customer;
        this.walletNumber = walletNumber;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public String getWalletNumber() {
        return walletNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public WalletBalance getBalance() {
        return balance;
    }
}
