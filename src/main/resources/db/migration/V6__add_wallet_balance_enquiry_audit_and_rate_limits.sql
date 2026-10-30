CREATE TABLE wallet_balance_enquiry_audit (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    wallet_id UUID REFERENCES wallets(id),
    session_id VARCHAR(128),
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('REST', 'USSD')),
    correlation_id VARCHAR(128) NOT NULL,
    result_category VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_balance_audit_customer_time
    ON wallet_balance_enquiry_audit(customer_id, occurred_at DESC);
CREATE INDEX idx_wallet_balance_audit_session_time
    ON wallet_balance_enquiry_audit(session_id, occurred_at DESC)
    WHERE session_id IS NOT NULL;

CREATE TABLE wallet_balance_rate_limits (
    customer_id UUID PRIMARY KEY REFERENCES customers(id),
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0)
);
