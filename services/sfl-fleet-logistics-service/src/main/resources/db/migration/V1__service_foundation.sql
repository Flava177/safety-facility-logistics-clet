CREATE SCHEMA IF NOT EXISTS fleet_logistics;

CREATE TABLE fleet_logistics.service_metadata (
    service_name VARCHAR(120) PRIMARY KEY,
    boundary_description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO fleet_logistics.service_metadata (service_name, boundary_description)
VALUES ('sfl-fleet-logistics-service', 'Fleet and logistics service for S166, S168_fuel and S171')
ON CONFLICT (service_name) DO NOTHING;

CREATE TABLE fleet_logistics.outbox_messages (
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

CREATE INDEX ix_fleet_logistics_outbox_status_created
    ON fleet_logistics.outbox_messages (status, created_at);

CREATE TABLE fleet_logistics.inbox_messages (
    message_id UUID PRIMARY KEY,
    consumer_name VARCHAR(160) NOT NULL,
    event_type VARCHAR(180) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(120)
);

CREATE INDEX ix_fleet_logistics_inbox_consumer_processed
    ON fleet_logistics.inbox_messages (consumer_name, processed_at);

