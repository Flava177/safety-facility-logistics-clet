CREATE SCHEMA IF NOT EXISTS safety_security;

CREATE TABLE safety_security.service_metadata (
    service_name VARCHAR(120) PRIMARY KEY,
    boundary_description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO safety_security.service_metadata (service_name, boundary_description)
VALUES ('sfl-safety-security-service', 'Safety and security service for S160, S160a, S161, S162, S162a, S163 and S174')
ON CONFLICT (service_name) DO NOTHING;

CREATE TABLE safety_security.outbox_messages (
    id UUID PRIMARY KEY,
    event_type VARCHAR(180) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    site_scope VARCHAR(80),
    correlation_id VARCHAR(120),
    causation_id VARCHAR(120),
    payload JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    failure_reason VARCHAR(2000)
);

CREATE INDEX ix_safety_security_outbox_status_created
    ON safety_security.outbox_messages (status, created_at);

CREATE TABLE safety_security.inbox_messages (
    message_id UUID PRIMARY KEY,
    consumer_name VARCHAR(160) NOT NULL,
    event_type VARCHAR(180) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(120)
);

CREATE INDEX ix_safety_security_inbox_consumer_processed
    ON safety_security.inbox_messages (consumer_name, processed_at);

