-- =====================================================================================
-- SRS-SFL-S166-02 — vehicle/driver assignment and trip workflow, plus the inspections
-- that gate it (inspections trace to S166-01 and S166-02; see gap report C-01).
-- =====================================================================================

-- btree_gist lets an exclusion constraint mix an equality test on the vehicle with an
-- overlap test on the period, which is what makes double-booking impossible at the
-- database level rather than only in application code.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE fleet_logistics.trips (
    id UUID PRIMARY KEY,
    trip_number VARCHAR(40) NOT NULL,
    vehicle_id UUID REFERENCES fleet_logistics.vehicles (id) ON DELETE RESTRICT,
    driver_id UUID REFERENCES fleet_logistics.driver_profile_references (id) ON DELETE RESTRICT,
    site_code VARCHAR(40) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    origin VARCHAR(200) NOT NULL,
    destination VARCHAR(200) NOT NULL,
    operating_mode VARCHAR(30) NOT NULL,
    planned_start TIMESTAMPTZ NOT NULL,
    planned_end TIMESTAMPTZ NOT NULL,
    actual_start TIMESTAMPTZ,
    actual_end TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL,
    status_before_hold VARCHAR(30),
    hold_reason VARCHAR(1000),
    cancellation_reason VARCHAR(1000),
    closure_reason VARCHAR(1000),
    closure_evidence_id UUID,
    start_odometer BIGINT,
    end_odometer BIGINT,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_trips_period CHECK (planned_end > planned_start),
    CONSTRAINT ck_fleet_trips_odometer CHECK (end_odometer IS NULL OR start_odometer IS NULL
        OR end_odometer >= start_odometer),
    -- SRS-SFL-S166-02: a workflow cannot be closed without required evidence or closure reason.
    CONSTRAINT ck_fleet_trips_closure CHECK (status <> 'COMPLETED'
        OR (closure_reason IS NOT NULL AND closure_evidence_id IS NOT NULL AND end_odometer IS NOT NULL)),
    CONSTRAINT ck_fleet_trips_cancellation CHECK (status <> 'CANCELLED' OR cancellation_reason IS NOT NULL),
    CONSTRAINT ck_fleet_trips_hold CHECK (status <> 'ON_HOLD' OR hold_reason IS NOT NULL),
    CONSTRAINT ck_fleet_trips_assignment CHECK (status IN ('PLANNED', 'CANCELLED')
        OR (vehicle_id IS NOT NULL AND driver_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_fleet_trips_site_number
    ON fleet_logistics.trips (site_code, trip_number);

-- No two live trips may hold the same vehicle over overlapping periods. The range is half-open
-- so a trip ending at 12:00 and another starting at 12:00 are back-to-back, not conflicting.
ALTER TABLE fleet_logistics.trips
    ADD CONSTRAINT ux_fleet_trips_vehicle_period
    EXCLUDE USING gist (
        vehicle_id WITH =,
        tstzrange(planned_start, planned_end, '[)') WITH &&
    ) WHERE (status IN ('PLANNED', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD') AND vehicle_id IS NOT NULL);

ALTER TABLE fleet_logistics.trips
    ADD CONSTRAINT ux_fleet_trips_driver_period
    EXCLUDE USING gist (
        driver_id WITH =,
        tstzrange(planned_start, planned_end, '[)') WITH &&
    ) WHERE (status IN ('PLANNED', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD') AND driver_id IS NOT NULL);

CREATE INDEX ix_fleet_trips_site_status_start
    ON fleet_logistics.trips (site_code, status, planned_start DESC);
CREATE INDEX ix_fleet_trips_vehicle_period
    ON fleet_logistics.trips (vehicle_id, planned_start DESC);
CREATE INDEX ix_fleet_trips_driver_period
    ON fleet_logistics.trips (driver_id, planned_start DESC);
CREATE INDEX ix_fleet_trips_live
    ON fleet_logistics.trips (status, planned_start)
    WHERE status IN ('PLANNED', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD');

-- -------------------------------------------------------------------------------------
-- Inspections
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.vehicle_inspections (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES fleet_logistics.vehicles (id) ON DELETE RESTRICT,
    trip_id UUID REFERENCES fleet_logistics.trips (id) ON DELETE RESTRICT,
    site_code VARCHAR(40) NOT NULL,
    inspection_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    result VARCHAR(40) NOT NULL,
    performed_by VARCHAR(160) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL,
    odometer_reading BIGINT NOT NULL,
    evidence_id UUID,
    -- Checklist findings vary by vehicle category and local checklist, so the shape is
    -- genuinely variable; this is one of the few places JSONB is justified.
    findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes VARCHAR(2000),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_inspections_odometer CHECK (odometer_reading >= 0),
    -- A failed inspection takes a vehicle off the road; the SRS requires the evidence behind it.
    CONSTRAINT ck_fleet_inspections_failure_evidence
        CHECK (result <> 'FAILED' OR evidence_id IS NOT NULL)
);

CREATE INDEX ix_fleet_inspections_vehicle_performed
    ON fleet_logistics.vehicle_inspections (vehicle_id, performed_at DESC, id DESC);
CREATE INDEX ix_fleet_inspections_trip
    ON fleet_logistics.vehicle_inspections (trip_id)
    WHERE trip_id IS NOT NULL;
CREATE INDEX ix_fleet_inspections_site_result
    ON fleet_logistics.vehicle_inspections (site_code, result, performed_at DESC);
CREATE INDEX ix_fleet_inspections_failures
    ON fleet_logistics.vehicle_inspections (site_code, performed_at DESC)
    WHERE result = 'FAILED';
