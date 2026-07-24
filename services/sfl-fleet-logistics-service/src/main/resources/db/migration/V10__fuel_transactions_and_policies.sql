CREATE TABLE fleet_logistics.fuel_policies (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    policy_name VARCHAR(160) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    policy_version INTEGER NOT NULL,
    max_per_transaction NUMERIC(19,3) NOT NULL,
    daily_limit NUMERIC(19,3),
    monthly_limit NUMERIC(19,3),
    tank_capacity NUMERIC(19,3),
    min_consumption NUMERIC(19,4),
    max_consumption NUMERIC(19,4),
    odometer_jump_tolerance BIGINT NOT NULL,
    receipt_required BOOLEAN NOT NULL,
    receipt_grace_hours INTEGER NOT NULL,
    materiality_amount NUMERIC(19,2) NOT NULL,
    anomaly_sla_hours INTEGER NOT NULL,
    allowed_fuel_products TEXT NOT NULL,
    approved_vendors TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fuel_policy_version UNIQUE (site_code, policy_name, policy_version),
    CONSTRAINT ck_fuel_policy_period CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_fuel_policy_limits CHECK (max_per_transaction > 0 AND odometer_jump_tolerance >= 0
        AND receipt_grace_hours >= 0 AND materiality_amount >= 0 AND anomaly_sla_hours > 0)
);

CREATE TABLE fleet_logistics.fuel_transactions (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    provider_transaction_id VARCHAR(160),
    source_system VARCHAR(100) NOT NULL,
    vehicle_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    trip_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    vendor_reference VARCHAR(160) NOT NULL,
    station_reference VARCHAR(160),
    fuel_product VARCHAR(60) NOT NULL,
    quantity NUMERIC(19,3) NOT NULL,
    quantity_unit VARCHAR(20) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    total_cost NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    masked_card_reference VARCHAR(40),
    odometer_reading BIGINT NOT NULL,
    receipt_evidence_id UUID,
    comments VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    lifecycle_status VARCHAR(30) NOT NULL,
    ingestion_timestamp TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(160),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fuel_provider_transaction UNIQUE NULLS NOT DISTINCT (site_code, source_system, provider_transaction_id),
    CONSTRAINT ck_fuel_transaction_values CHECK (quantity > 0 AND unit_price >= 0 AND total_cost >= 0 AND odometer_reading >= 0)
);

CREATE TABLE fleet_logistics.fuel_odometer_observations (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    vehicle_id UUID NOT NULL,
    transaction_id UUID,
    logbook_id UUID,
    reading BIGINT NOT NULL,
    source VARCHAR(40) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    accepted BOOLEAN NOT NULL,
    rejection_reason VARCHAR(500),
    actor_id VARCHAR(160) NOT NULL,
    correlation_id VARCHAR(120),
    CONSTRAINT ck_fuel_odometer_reading CHECK (reading >= 0)
);

CREATE INDEX ix_fuel_transaction_site_date ON fleet_logistics.fuel_transactions(site_code, occurred_at DESC);
CREATE INDEX ix_fuel_transaction_status ON fleet_logistics.fuel_transactions(site_code, status, occurred_at);
CREATE INDEX ix_fuel_transaction_vehicle ON fleet_logistics.fuel_transactions(vehicle_id, occurred_at DESC);
CREATE INDEX ix_fuel_transaction_driver ON fleet_logistics.fuel_transactions(driver_id, occurred_at DESC);
CREATE INDEX ix_fuel_policy_effective ON fleet_logistics.fuel_policies(site_code, status, effective_from, effective_to);
