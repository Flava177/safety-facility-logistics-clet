-- S174 Emergency Mass Notification — service foundation: schema, metadata, outbox, secure inbox, audit chain.
CREATE SCHEMA IF NOT EXISTS emergency_notification;

CREATE TABLE emergency_notification.service_metadata (
    service_name VARCHAR(120) PRIMARY KEY,
    boundary_description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO emergency_notification.service_metadata (service_name, boundary_description)
VALUES ('sfl-emergency-notification-service',
        'S174 Emergency Mass Notification System (SFL.SSEMP / Emergency Communications)')
ON CONFLICT DO NOTHING;

-- Transactional outbound event store (drained by an in-process drainer; dead-letter + privileged replay).
CREATE TABLE emergency_notification.outbox_messages (
    id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    site_scope VARCHAR(40),
    correlation_id VARCHAR(120),
    causation_id VARCHAR(120),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PUBLISHED','DEAD_LETTERED'))
);
CREATE INDEX ix_outbox_status ON emergency_notification.outbox_messages(status, created_at);

-- Secure integration inbox: envelope persisted before domain processing; idempotent per (source, key).
CREATE TABLE emergency_notification.integration_inbox_messages (
    id UUID PRIMARY KEY,
    source_system VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    site_scope VARCHAR(40) NOT NULL,
    payload_hash VARCHAR(80) NOT NULL,
    raw_payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000),
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    correlation_id VARCHAR(120),
    CONSTRAINT uq_inbox_source_key UNIQUE (source_system, idempotency_key),
    CONSTRAINT ck_inbox_status CHECK (status IN ('PROCESSED','REJECTED','DEAD_LETTER'))
);
CREATE INDEX ix_inbox_status ON emergency_notification.integration_inbox_messages(status, received_at);

-- Append-only, tamper-evident audit hash chain.
CREATE TABLE emergency_notification.audit_events (
    id UUID PRIMARY KEY,
    sequence_no BIGINT NOT NULL,
    actor VARCHAR(160) NOT NULL,
    action VARCHAR(40) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(120),
    site_scope VARCHAR(40),
    before_value JSONB,
    after_value JSONB,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    reason VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    previous_hash VARCHAR(80),
    record_hash VARCHAR(80) NOT NULL,
    CONSTRAINT uq_audit_sequence UNIQUE (sequence_no)
);
CREATE INDEX ix_audit_resource ON emergency_notification.audit_events(resource_type, resource_id);
