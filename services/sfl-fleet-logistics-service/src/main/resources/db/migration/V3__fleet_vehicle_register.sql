-- =====================================================================================
-- SRS-SFL-S166-01 — Maintain Fleet Operational Records: the vehicle register, its
-- compliance documents and its service history.
-- =====================================================================================

CREATE TABLE fleet_logistics.vehicles (
    id UUID PRIMARY KEY,
    registration_number VARCHAR(40) NOT NULL,
    vin VARCHAR(40),
    make VARCHAR(80) NOT NULL,
    model VARCHAR(80) NOT NULL,
    manufacture_year INTEGER NOT NULL,
    category VARCHAR(40) NOT NULL,
    capacity INTEGER NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    responsible_unit VARCHAR(160) NOT NULL,
    operational_owner VARCHAR(160) NOT NULL,
    acquisition_reference VARCHAR(120),
    lifecycle_status VARCHAR(30) NOT NULL,
    service_status VARCHAR(30) NOT NULL,
    availability_status VARCHAR(30) NOT NULL,
    odometer_value BIGINT NOT NULL,
    odometer_unit VARCHAR(20) NOT NULL,
    odometer_source VARCHAR(40) NOT NULL,
    odometer_recorded_at TIMESTAMPTZ NOT NULL,
    emergency_only BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_operating_modes VARCHAR(200) NOT NULL,
    current_trip_id UUID,
    -- SRS-SFL-S166-01 system-managed fields
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_vehicles_capacity CHECK (capacity BETWEEN 1 AND 200),
    CONSTRAINT ck_fleet_vehicles_year CHECK (manufacture_year >= 1950),
    CONSTRAINT ck_fleet_vehicles_odometer CHECK (odometer_value >= 0),
    CONSTRAINT ck_fleet_vehicles_modified_after_created CHECK (last_modified_at >= created_at)
);

-- SRS-SFL-S166-01: "Duplicate active identifiers are blocked within the same site and object type."
-- Partial, so an archived registration can legitimately be reissued to a replacement vehicle.
CREATE UNIQUE INDEX ux_fleet_vehicles_site_registration_active
    ON fleet_logistics.vehicles (site_code, upper(registration_number))
    WHERE lifecycle_status <> 'ARCHIVED';

CREATE UNIQUE INDEX ux_fleet_vehicles_site_vin_active
    ON fleet_logistics.vehicles (site_code, upper(vin))
    WHERE vin IS NOT NULL AND lifecycle_status <> 'ARCHIVED';

CREATE INDEX ix_fleet_vehicles_site_lifecycle_availability
    ON fleet_logistics.vehicles (site_code, lifecycle_status, availability_status);
CREATE INDEX ix_fleet_vehicles_site_service_status
    ON fleet_logistics.vehicles (site_code, service_status);
CREATE INDEX ix_fleet_vehicles_registration_search
    ON fleet_logistics.vehicles (upper(registration_number));
CREATE INDEX ix_fleet_vehicles_current_trip
    ON fleet_logistics.vehicles (current_trip_id)
    WHERE current_trip_id IS NOT NULL;
CREATE INDEX ix_fleet_vehicles_site_created
    ON fleet_logistics.vehicles (site_code, created_at DESC, id DESC);

-- -------------------------------------------------------------------------------------
-- Compliance documents
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.vehicle_compliance_documents (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES fleet_logistics.vehicles (id) ON DELETE RESTRICT,
    site_code VARCHAR(40) NOT NULL,
    document_type VARCHAR(60) NOT NULL,
    document_reference VARCHAR(160) NOT NULL,
    issuing_authority VARCHAR(160) NOT NULL,
    issued_on DATE NOT NULL,
    expires_on DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    evidence_id UUID,
    retention_class VARCHAR(40) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_compliance_validity CHECK (expires_on >= issued_on)
);

-- At most one document of each type may currently provide cover for a vehicle.
CREATE UNIQUE INDEX ux_fleet_compliance_active_type
    ON fleet_logistics.vehicle_compliance_documents (vehicle_id, document_type)
    WHERE status IN ('ACTIVE', 'EXPIRING');

CREATE INDEX ix_fleet_compliance_vehicle
    ON fleet_logistics.vehicle_compliance_documents (vehicle_id, expires_on);
CREATE INDEX ix_fleet_compliance_expiry_sweep
    ON fleet_logistics.vehicle_compliance_documents (expires_on)
    WHERE status IN ('ACTIVE', 'EXPIRING');
CREATE INDEX ix_fleet_compliance_site_status
    ON fleet_logistics.vehicle_compliance_documents (site_code, status);

-- -------------------------------------------------------------------------------------
-- Service history
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.vehicle_service_records (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES fleet_logistics.vehicles (id) ON DELETE RESTRICT,
    site_code VARCHAR(40) NOT NULL,
    service_type VARCHAR(60) NOT NULL,
    performed_on DATE NOT NULL,
    odometer_at_service BIGINT NOT NULL,
    next_due_on DATE,
    next_due_odometer BIGINT,
    provider_reference VARCHAR(160),
    work_summary VARCHAR(2000) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    evidence_id UUID,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_service_odometer CHECK (odometer_at_service >= 0),
    CONSTRAINT ck_fleet_service_next_due_on CHECK (next_due_on IS NULL OR next_due_on >= performed_on),
    CONSTRAINT ck_fleet_service_next_due_odometer
        CHECK (next_due_odometer IS NULL OR next_due_odometer >= odometer_at_service)
);

CREATE INDEX ix_fleet_service_vehicle_performed
    ON fleet_logistics.vehicle_service_records (vehicle_id, performed_on DESC, id DESC);
CREATE INDEX ix_fleet_service_next_due
    ON fleet_logistics.vehicle_service_records (next_due_on)
    WHERE next_due_on IS NOT NULL;
CREATE INDEX ix_fleet_service_site
    ON fleet_logistics.vehicle_service_records (site_code, performed_on DESC);
