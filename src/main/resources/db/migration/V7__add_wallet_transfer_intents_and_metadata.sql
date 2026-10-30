ALTER TABLE wallet_transfers
    ADD COLUMN fee NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    ADD COLUMN description VARCHAR(140);

CREATE TABLE wallet_transfer_intents (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    sender_wallet_id UUID NOT NULL REFERENCES wallets(id),
    recipient_wallet_id UUID NOT NULL REFERENCES wallets(id),
    idempotency_key UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    fee NUMERIC(19, 2) NOT NULL CHECK (fee >= 0),
    total_debit NUMERIC(19, 2) NOT NULL CHECK (total_debit = amount + fee),
    currency CHAR(3) NOT NULL,
    description VARCHAR(140),
    status VARCHAR(24) NOT NULL CHECK (status IN (
        'PENDING_CONFIRMATION', 'COMPLETED', 'CANCELLED', 'EXPIRED'
    )),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    transfer_reference VARCHAR(40) REFERENCES wallet_transfers(reference),
    CONSTRAINT chk_wallet_transfer_intent_distinct_wallets
        CHECK (sender_wallet_id <> recipient_wallet_id),
    CONSTRAINT uk_transfer_intent_customer_idempotency UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX idx_transfer_intents_pending_expiry
    ON wallet_transfer_intents(expires_at)
    WHERE status = 'PENDING_CONFIRMATION';
CREATE INDEX idx_wallet_transfers_recipient_created
    ON wallet_transfers(recipient_wallet_id, created_at DESC);

CREATE TABLE wallet_transfer_audit (
    id UUID PRIMARY KEY,
    transfer_reference VARCHAR(40) NOT NULL REFERENCES wallet_transfers(reference),
    customer_id UUID NOT NULL REFERENCES customers(id),
    sender_wallet_id UUID NOT NULL REFERENCES wallets(id),
    recipient_wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    fee NUMERIC(19, 2) NOT NULL CHECK (fee >= 0),
    status VARCHAR(16) NOT NULL CHECK (status = 'SUCCESSFUL'),
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('REST', 'USSD')),
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_transfer_audit_customer_time
    ON wallet_transfer_audit(customer_id, occurred_at DESC);

CREATE TABLE wallet_recipient_lookup_rate_limits (
    customer_id UUID PRIMARY KEY REFERENCES customers(id),
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0)
);
