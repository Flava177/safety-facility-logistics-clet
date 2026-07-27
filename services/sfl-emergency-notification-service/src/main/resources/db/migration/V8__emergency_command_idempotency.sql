-- S174 command idempotency for retried state-creating emergency activation requests.
CREATE TABLE emergency_notification.command_idempotency_keys (
    id UUID PRIMARY KEY,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(80) NOT NULL,
    result_id UUID NOT NULL,
    site_code VARCHAR(40),
    actor_id VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_emergency_command_idempotency UNIQUE (operation, idempotency_key)
);

CREATE INDEX ix_emergency_command_idempotency_result
    ON emergency_notification.command_idempotency_keys(result_id);
