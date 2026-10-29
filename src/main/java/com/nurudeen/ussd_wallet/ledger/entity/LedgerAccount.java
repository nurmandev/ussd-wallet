package com.nurudeen.ussd_wallet.ledger.entity;

import com.nurudeen.ussd_wallet.ledger.enums.LedgerAccountType;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_code", nullable = false, unique = true, length = 60)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private LedgerAccountType accountType;

    @OneToOne
    @JoinColumn(name = "wallet_id", unique = true)
    private Wallet wallet;

    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency = "NGN";

    @Column(name = "is_system_account", nullable = false)
    private boolean systemAccount = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerAccount() {
    }

    public LedgerAccount(Wallet wallet) {
        this.wallet = wallet;
        this.accountCode = "WALLET:" + wallet.getId();
        this.accountName = "Customer wallet liability";
        this.accountType = LedgerAccountType.LIABILITY;
        this.currency = wallet.getCurrency();
    }
}
