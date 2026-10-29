ALTER TABLE customer_security
    ADD COLUMN wallet_id UUID,
    ADD COLUMN last_successful_verification TIMESTAMPTZ;

UPDATE customer_security security
SET wallet_id = wallet.id
FROM wallets wallet
WHERE wallet.customer_id = security.customer_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM customer_security WHERE wallet_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot add PIN wallet ownership: customer security rows without wallets exist';
    END IF;
END;
$$;

ALTER TABLE customer_security
    ALTER COLUMN wallet_id SET NOT NULL,
    ADD CONSTRAINT uk_customer_security_wallet_id UNIQUE (wallet_id),
    ADD CONSTRAINT fk_customer_security_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets(id);

CREATE TABLE pin_security_audit (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    event_type VARCHAR(40) NOT NULL
        CHECK (event_type IN (
            'PIN_SETUP',
            'PIN_VERIFICATION_SUCCESS',
            'PIN_VERIFICATION_FAILED',
            'PIN_LOCKED',
            'PIN_CHANGED',
            'PIN_RESET',
            'PIN_RESET_OTP_REQUESTED',
            'PIN_RESET_OTP_VERIFIED'
        )),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pin_security_audit_customer_time
    ON pin_security_audit(customer_id, occurred_at DESC);

CREATE TABLE pin_recovery_authorizations (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pin_recovery_authorizations_customer
    ON pin_recovery_authorizations(customer_id, expires_at);
