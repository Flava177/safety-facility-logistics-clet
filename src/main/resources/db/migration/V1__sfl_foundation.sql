CREATE SCHEMA IF NOT EXISTS ifimp;
CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS messaging;
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE ifimp.facility_faults (
    id UUID PRIMARY KEY,
    fault_number VARCHAR(40) NOT NULL UNIQUE,
    site_code VARCHAR(40) NOT NULL,
    location_code VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    category VARCHAR(120),
    priority VARCHAR(32) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reported_by VARCHAR(160) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL,
    work_order_id UUID
);

CREATE INDEX ix_facility_faults_site_status
    ON ifimp.facility_faults (site_code, status);
CREATE INDEX ix_facility_faults_work_order
    ON ifimp.facility_faults (work_order_id);

CREATE TABLE platform.audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(180) NOT NULL,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id UUID NOT NULL,
    actor_id VARCHAR(160) NOT NULL,
    source VARCHAR(120) NOT NULL,
    correlation_id VARCHAR(120),
    occurred_at TIMESTAMPTZ NOT NULL,
    details JSONB NOT NULL
);

CREATE INDEX ix_audit_events_aggregate
    ON platform.audit_events (aggregate_type, aggregate_id, occurred_at);
CREATE INDEX ix_audit_events_correlation
    ON platform.audit_events (correlation_id);

CREATE TABLE messaging.outbox_messages (
    id UUID PRIMARY KEY,
    event_type VARCHAR(180) NOT NULL,
    aggregate_id UUID NOT NULL,
    correlation_id VARCHAR(120),
    payload JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    failure_reason VARCHAR(2000)
);

CREATE INDEX ix_outbox_messages_status_created
    ON messaging.outbox_messages (status, created_at);

