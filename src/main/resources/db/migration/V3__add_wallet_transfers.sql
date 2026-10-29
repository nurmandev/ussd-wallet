CREATE TABLE wallet_transfers (
    id UUID PRIMARY KEY,
    reference VARCHAR(40) NOT NULL UNIQUE,
    sender_wallet_id UUID NOT NULL REFERENCES wallets(id),
    recipient_wallet_id UUID NOT NULL REFERENCES wallets(id),
    idempotency_key UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_wallet_transfer_distinct_wallets
        CHECK (sender_wallet_id <> recipient_wallet_id),
    CONSTRAINT uk_wallet_transfer_sender_idempotency
        UNIQUE (sender_wallet_id, idempotency_key)
);

CREATE INDEX idx_wallet_transfers_sender_created
    ON wallet_transfers(sender_wallet_id, created_at DESC);

CREATE TABLE wallet_transfer_entries (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES wallet_transfers(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_wallet_transfer_entry_type UNIQUE (transfer_id, entry_type)
);

CREATE INDEX idx_wallet_transfer_entries_wallet_created
    ON wallet_transfer_entries(wallet_id, created_at DESC);
