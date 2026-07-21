-- =====================================================================================
-- SRS-SFL-S166-01 — Maintain Fleet Operational Records: approved driver profile references.
--
-- This table is a *reference*, not a personnel record. HRMS owns the person; fleet holds only
-- what an assignment decision needs (SRS-SFL-S166-04 HRMS driver/staff records).
-- =====================================================================================

CREATE TABLE fleet_logistics.driver_profile_references (
    id UUID PRIMARY KEY,
    staff_reference VARCHAR(80) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    licence_number VARCHAR(80) NOT NULL,
    licence_class VARCHAR(10) NOT NULL,
    licence_expires_on DATE NOT NULL,
    medical_clearance_expires_on DATE,
    site_code VARCHAR(40) NOT NULL,
    responsible_unit VARCHAR(160) NOT NULL,
    lifecycle_status VARCHAR(30) NOT NULL,
    eligibility_status VARCHAR(30) NOT NULL,
    suspension_reason VARCHAR(1000),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_drivers_modified_after_created CHECK (last_modified_at >= created_at),
    -- A suspended profile must say why; the eligibility assessment quotes this reason back.
    CONSTRAINT ck_fleet_drivers_suspension_reason
        CHECK (lifecycle_status <> 'SUSPENDED' OR suspension_reason IS NOT NULL)
);

-- SRS-SFL-S166-01 duplicate-identifier rule, per site and per object type.
CREATE UNIQUE INDEX ux_fleet_drivers_site_staff_active
    ON fleet_logistics.driver_profile_references (site_code, upper(staff_reference))
    WHERE lifecycle_status <> 'ARCHIVED';

CREATE UNIQUE INDEX ux_fleet_drivers_site_licence_active
    ON fleet_logistics.driver_profile_references (site_code, upper(licence_number))
    WHERE lifecycle_status <> 'ARCHIVED';

CREATE INDEX ix_fleet_drivers_site_lifecycle_eligibility
    ON fleet_logistics.driver_profile_references (site_code, lifecycle_status, eligibility_status);
CREATE INDEX ix_fleet_drivers_licence_expiry
    ON fleet_logistics.driver_profile_references (licence_expires_on)
    WHERE lifecycle_status <> 'ARCHIVED';
CREATE INDEX ix_fleet_drivers_medical_expiry
    ON fleet_logistics.driver_profile_references (medical_clearance_expires_on)
    WHERE medical_clearance_expires_on IS NOT NULL AND lifecycle_status <> 'ARCHIVED';
CREATE INDEX ix_fleet_drivers_site_created
    ON fleet_logistics.driver_profile_references (site_code, created_at DESC, id DESC);
