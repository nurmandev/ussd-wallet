CREATE TABLE ussd_sessions (
    session_id VARCHAR(128) PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    service_code VARCHAR(20) NOT NULL,
    network_code VARCHAR(32) NOT NULL,
    current_menu VARCHAR(40) NOT NULL,
    current_step VARCHAR(40) NOT NULL,
    session_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    session_status VARCHAR(20) NOT NULL
        CHECK (session_status IN (
            'ACTIVE', 'WAITING_FOR_INPUT', 'PROCESSING', 'COMPLETED',
            'EXPIRED', 'CANCELLED', 'FAILED'
        )),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ussd_sessions_phone_status
    ON ussd_sessions(phone_number, session_status);

CREATE INDEX idx_ussd_sessions_expiry
    ON ussd_sessions(expires_at)
    WHERE session_status IN ('ACTIVE', 'WAITING_FOR_INPUT', 'PROCESSING');

CREATE TABLE ussd_session_events (
    id UUID PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL REFERENCES ussd_sessions(session_id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    from_step VARCHAR(40),
    to_step VARCHAR(40),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ussd_session_events_session_time
    ON ussd_session_events(session_id, occurred_at DESC);

CREATE TABLE ussd_gateway_callbacks (
    session_id VARCHAR(128) NOT NULL REFERENCES ussd_sessions(session_id) ON DELETE CASCADE,
    callback_id VARCHAR(128) NOT NULL,
    response_type VARCHAR(10) NOT NULL CHECK (response_type IN ('CONTINUE', 'END')),
    response_text VARCHAR(182),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, callback_id)
);

CREATE INDEX idx_ussd_gateway_callbacks_created
    ON ussd_gateway_callbacks(created_at);
