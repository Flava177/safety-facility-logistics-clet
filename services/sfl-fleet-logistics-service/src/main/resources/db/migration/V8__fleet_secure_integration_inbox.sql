-- =====================================================================================
-- SRS-SFL-S166-04 — Secure Integrations
-- =====================================================================================

CREATE TABLE fleet_logistics.fleet_integration_inbox_messages (
    id UUID PRIMARY KEY,
    source_system VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    occurred_at TIMESTAMPTZ NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    raw_payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(2000),
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_fleet_integration_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_fleet_integration_payload_hash CHECK (payload_hash ~ '^[A-Fa-f0-9]{64}$'),
    CONSTRAINT ck_fleet_integration_processed CHECK (
        status NOT IN ('PROCESSED', 'REJECTED', 'DEAD_LETTER') OR processed_at IS NOT NULL
    )
);

CREATE UNIQUE INDEX ux_fleet_integration_idempotency
    ON fleet_logistics.fleet_integration_inbox_messages (source_system, idempotency_key);
CREATE INDEX ix_fleet_integration_status_received
    ON fleet_logistics.fleet_integration_inbox_messages (status, received_at DESC);
CREATE INDEX ix_fleet_integration_site_received
    ON fleet_logistics.fleet_integration_inbox_messages (site_code, received_at DESC);

CREATE TABLE fleet_logistics.fleet_vehicle_locations (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    odometer_value BIGINT,
    recorded_at TIMESTAMPTZ NOT NULL,
    source_system VARCHAR(80) NOT NULL,
    integration_message_id UUID NOT NULL REFERENCES fleet_logistics.fleet_integration_inbox_messages (id)
        ON DELETE RESTRICT,
    correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_vehicle_location_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_fleet_vehicle_location_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_fleet_vehicle_location_odometer CHECK (odometer_value IS NULL OR odometer_value >= 0)
);

CREATE INDEX ix_fleet_vehicle_locations_vehicle_recorded
    ON fleet_logistics.fleet_vehicle_locations (vehicle_id, recorded_at DESC);
CREATE INDEX ix_fleet_vehicle_locations_site_recorded
    ON fleet_logistics.fleet_vehicle_locations (site_code, recorded_at DESC);
