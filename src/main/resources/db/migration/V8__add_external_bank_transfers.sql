CREATE TABLE banks (
    id UUID PRIMARY KEY,
    bank_name VARCHAR(150) NOT NULL,
    bank_code VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    supported_transfer_channel VARCHAR(25) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_banks_bank_code UNIQUE (bank_code)
);

CREATE TABLE external_bank_accounts (
    id UUID PRIMARY KEY,
    bank_id UUID NOT NULL REFERENCES banks(id),
    account_number_hash VARCHAR(128) NOT NULL,
    masked_account_number VARCHAR(20) NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_external_bank_account_bank_hash UNIQUE (bank_id, account_number_hash)
);

CREATE INDEX idx_external_bank_accounts_lookup
    ON external_bank_accounts(bank_id, account_number_hash, status);

CREATE TABLE account_resolutions (
    id UUID PRIMARY KEY,
    resolution_reference VARCHAR(40) NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id),
    bank_id UUID NOT NULL REFERENCES banks(id),
    account_number_hash VARCHAR(128) NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    status VARCHAR(25) NOT NULL
        CHECK (status IN ('VERIFIED', 'EXPIRED', 'FAILED', 'ACCOUNT_NOT_FOUND', 'BANK_UNAVAILABLE', 'BANK_INACTIVE')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_account_resolutions_reference UNIQUE (resolution_reference)
);

CREATE INDEX idx_account_resolutions_customer_expiry
    ON account_resolutions(customer_id, expires_at);

CREATE TABLE external_transfers (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(40) NOT NULL,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    bank_id UUID NOT NULL REFERENCES banks(id),
    destination_account_number_protected VARCHAR(255) NOT NULL,
    destination_account_name VARCHAR(150) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    fee NUMERIC(19, 2) NOT NULL CHECK (fee >= 0),
    total_debit NUMERIC(19, 2) NOT NULL CHECK (total_debit = amount + fee),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'NGN'),
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'INITIATED', 'VALIDATING', 'PROCESSING', 'PENDING', 'SUCCESSFUL',
        'FAILED', 'UNKNOWN', 'PENDING_RECONCILIATION', 'REVERSAL_PENDING', 'REVERSED'
    )),
    switch_reference VARCHAR(40),
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_external_transfers_reference UNIQUE (transaction_reference),
    CONSTRAINT uk_external_transfers_wallet_idempotency UNIQUE (wallet_id, idempotency_key)
);

CREATE INDEX idx_external_transfers_wallet_created
    ON external_transfers(wallet_id, created_at DESC);
CREATE INDEX idx_external_transfers_status_created
    ON external_transfers(status, created_at);
CREATE INDEX idx_external_transfers_switch_reference
    ON external_transfers(switch_reference)
    WHERE switch_reference IS NOT NULL;

CREATE TABLE transfer_attempts (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES external_transfers(id),
    attempt_reference VARCHAR(40) NOT NULL,
    switch_response_code VARCHAR(30) NOT NULL CHECK (switch_response_code IN (
        'SUCCESS', 'PENDING', 'FAILED', 'TIMEOUT', 'BANK_UNAVAILABLE',
        'ACCOUNT_NOT_FOUND', 'INSUFFICIENT_BANK_FUNDS', 'DUPLICATE_TRANSACTION'
    )),
    switch_response_message VARCHAR(255),
    processing_status VARCHAR(30) NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_transfer_attempts_reference UNIQUE (attempt_reference)
);

CREATE INDEX idx_transfer_attempts_transfer_attempted
    ON transfer_attempts(transfer_id, attempted_at DESC);

CREATE TABLE transfer_reversals (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    reversal_reference VARCHAR(40) NOT NULL,
    reversal_amount NUMERIC(19, 2) NOT NULL CHECK (reversal_amount > 0),
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_transfer_reversals_transfer
        FOREIGN KEY (transfer_id) REFERENCES external_transfers(id),
    CONSTRAINT uk_transfer_reversals_transfer UNIQUE (transfer_id),
    CONSTRAINT uk_transfer_reversals_reference UNIQUE (reversal_reference)
);
