CREATE TABLE fleet_logistics.driver_logbooks (
    id UUID PRIMARY KEY,
    logbook_number VARCHAR(50) NOT NULL UNIQUE,
    site_code VARCHAR(40) NOT NULL,
    driver_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    trip_id UUID,
    journey_date DATE NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    origin VARCHAR(200) NOT NULL,
    destination VARCHAR(200) NOT NULL,
    route_notes VARCHAR(1000),
    use_classification VARCHAR(30) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    passenger_load_notes VARCHAR(1000),
    start_odometer BIGINT NOT NULL,
    end_odometer BIGINT,
    declaration_accepted BOOLEAN NOT NULL,
    evidence_id UUID,
    status VARCHAR(30) NOT NULL,
    review_comment VARCHAR(1000),
    transition_reason VARCHAR(1000),
    submitted_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_logbook_times CHECK (end_time IS NULL OR end_time >= start_time),
    CONSTRAINT ck_logbook_odometer CHECK (start_odometer >= 0 AND (end_odometer IS NULL OR end_odometer >= start_odometer))
);

CREATE TABLE fleet_logistics.driver_logbook_fuel_transactions (
    logbook_id UUID NOT NULL REFERENCES fleet_logistics.driver_logbooks(id),
    fuel_transaction_id UUID NOT NULL REFERENCES fleet_logistics.fuel_transactions(id),
    PRIMARY KEY (logbook_id, fuel_transaction_id)
);

CREATE INDEX ix_logbook_driver_date ON fleet_logistics.driver_logbooks(driver_id, journey_date DESC);
CREATE INDEX ix_logbook_site_status ON fleet_logistics.driver_logbooks(site_code, status, journey_date);
