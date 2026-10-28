CREATE TABLE customers (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    kyc_level SMALLINT NOT NULL DEFAULT 0 CHECK (kyc_level BETWEEN 0 AND 3),
    phone_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_customers_phone_number UNIQUE (phone_number)
);

CREATE INDEX idx_customers_status ON customers(status);

CREATE TABLE customer_security (
    customer_id UUID PRIMARY KEY REFERENCES customers(id),
    pin_hash VARCHAR(255),
    pin_set_at TIMESTAMPTZ,
    pin_changed_at TIMESTAMPTZ,
    failed_pin_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_pin_attempts >= 0),
    locked_until TIMESTAMPTZ,
    pin_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SET'
        CHECK (pin_status IN ('NOT_SET', 'ACTIVE', 'LOCKED', 'RESET_REQUIRED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_customer_security_pin_state CHECK (
        (pin_status = 'NOT_SET' AND pin_hash IS NULL)
        OR (pin_status <> 'NOT_SET' AND pin_hash IS NOT NULL)
    )
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    wallet_number VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'NGN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_wallets_customer_id UNIQUE (customer_id),
    CONSTRAINT uk_wallets_wallet_number UNIQUE (wallet_number),
    CONSTRAINT fk_wallets_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE wallet_balances (
    wallet_id UUID PRIMARY KEY REFERENCES wallets(id),
    ledger_balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (ledger_balance >= 0),
    available_balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (available_balance >= 0),
    reserved_balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (reserved_balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_wallet_balances_not_overdrawn
        CHECK (available_balance + reserved_balance <= ledger_balance)
);

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    account_code VARCHAR(60) NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    account_type VARCHAR(30) NOT NULL
        CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    wallet_id UUID,
    currency CHAR(3) NOT NULL DEFAULT 'NGN',
    is_system_account BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_ledger_accounts_account_code UNIQUE (account_code),
    CONSTRAINT uk_ledger_accounts_wallet_id UNIQUE (wallet_id),
    CONSTRAINT fk_ledger_accounts_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

CREATE INDEX idx_ledger_accounts_type_active ON ledger_accounts(account_type, is_active);
