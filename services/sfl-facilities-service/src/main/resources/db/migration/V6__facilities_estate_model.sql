-- =====================================================================================
-- S152 CAFM / IWMS — the estate model.
--
-- Extends the six tables V2 created rather than replacing them. Renaming them would
-- break V3, V4, six JPA entities, the maintenance module, four tests and the facilities
-- dashboard page, in exchange for a prefix the `facilities` schema already supplies
-- (gap report C-01). New S152 tables are prefixed `facility_` to distinguish them.
--
--   SRS-SFL-S152-01  system-managed fields, lifecycle states, facility asset, zone membership
--   NFR 23.3         site operating mode (Routine / Examination), explicit and audited
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- System-managed fields, required on every operational record:
-- "created by/date, last modified by/date, version, source channel and audit correlation ID".
--
-- Added with defaults so the existing rows migrate without a separate backfill pass, then
-- the defaults are dropped: a record written from here on must state its own provenance,
-- and a column default would let a caller omit it silently.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION facilities.facility_add_record_metadata(target_table TEXT)
RETURNS VOID AS $$
BEGIN
    EXECUTE format($fmt$
        ALTER TABLE facilities.%I
            ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
            ADD COLUMN created_by VARCHAR(160) NOT NULL DEFAULT 'system',
            ADD COLUMN last_modified_by VARCHAR(160) NOT NULL DEFAULT 'system',
            ADD COLUMN last_modified_at TIMESTAMPTZ,
            ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0,
            ADD COLUMN source_channel VARCHAR(40) NOT NULL DEFAULT 'SYSTEM',
            ADD COLUMN correlation_id VARCHAR(120)
    $fmt$, target_table);

    EXECUTE format(
        'UPDATE facilities.%I SET last_modified_at = created_at WHERE last_modified_at IS NULL',
        target_table);

    -- %I for the table (quotes if it must), %s for the constraint name (an identifier
    -- fragment, which %I would wrongly quote as a whole).
    EXECUTE format($fmt$
        ALTER TABLE facilities.%I
            ALTER COLUMN last_modified_at SET NOT NULL,
            ALTER COLUMN created_by DROP DEFAULT,
            ALTER COLUMN last_modified_by DROP DEFAULT,
            ALTER COLUMN source_channel DROP DEFAULT,
            ADD CONSTRAINT %s CHECK
                (lifecycle_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED'))
    $fmt$, target_table, 'ck_' || target_table || '_lifecycle');
END;
$$ LANGUAGE plpgsql;

SELECT facilities.facility_add_record_metadata('sites');
SELECT facilities.facility_add_record_metadata('buildings');
SELECT facilities.facility_add_record_metadata('facility_floors');
SELECT facilities.facility_add_record_metadata('facility_rooms');
SELECT facilities.facility_add_record_metadata('zones');
SELECT facilities.facility_add_record_metadata('device_references');

DROP FUNCTION facilities.facility_add_record_metadata(TEXT);

-- The pre-S152 `active` flag is now derived from lifecycle_status. Existing inactive sites
-- carry that forward before the column goes, so no site silently reactivates.
UPDATE facilities.sites SET lifecycle_status = 'INACTIVE' WHERE active = FALSE;
ALTER TABLE facilities.sites DROP COLUMN active;

-- -------------------------------------------------------------------------------------
-- NFR 23.3 — site operating mode.
-- Mode is a property of the site: an examination is declared over a centre and every
-- space beneath inherits the stricter rules.
-- -------------------------------------------------------------------------------------
ALTER TABLE facilities.sites
    ADD COLUMN operating_mode VARCHAR(20) NOT NULL DEFAULT 'ROUTINE',
    ADD COLUMN operating_mode_changed_at TIMESTAMPTZ,
    ADD COLUMN operating_mode_changed_by VARCHAR(160),
    ADD CONSTRAINT ck_sites_operating_mode CHECK (operating_mode IN ('ROUTINE', 'EXAMINATION'));

CREATE INDEX ix_sites_operating_mode
    ON facilities.sites (operating_mode)
    WHERE operating_mode = 'EXAMINATION';

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-01 — space attributes.
-- The free-text room_type is retained and mirrored from space_type, so the facilities
-- dashboard page and any existing consumer keep reading the field they already read.
-- -------------------------------------------------------------------------------------
ALTER TABLE facilities.facility_rooms
    ADD COLUMN space_type VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN area_sqm NUMERIC(12, 2),
    ADD COLUMN cost_centre VARCHAR(60),
    ADD COLUMN bookable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN examination_capable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN readiness_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN readiness_locked_by VARCHAR(160),
    ADD COLUMN readiness_locked_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_facility_rooms_area_non_negative CHECK (area_sqm IS NULL OR area_sqm >= 0),
    ADD CONSTRAINT ck_facility_rooms_lock_provenance CHECK
        (readiness_locked = FALSE OR (readiness_locked_by IS NOT NULL AND readiness_locked_at IS NOT NULL));

-- Best-effort classification of rows created before space_type existed. Anything that does
-- not match a known pattern stays OTHER, which is honest: an unclassified space should show
-- as unclassified rather than be guessed into a type that changes whether it is bookable.
UPDATE facilities.facility_rooms
SET space_type = CASE
        WHEN upper(coalesce(room_type, '')) LIKE '%EXAM%' THEN 'EXAMINATION_HALL'
        WHEN upper(coalesce(room_type, '')) LIKE '%LECTURE%' THEN 'LECTURE_HALL'
        WHEN upper(coalesce(room_type, '')) LIKE '%MOOT%' THEN 'MOOT_COURTROOM'
        WHEN upper(coalesce(room_type, '')) LIKE '%AUDITOR%' THEN 'AUDITORIUM'
        WHEN upper(coalesce(room_type, '')) LIKE '%MEETING%' THEN 'MEETING_ROOM'
        WHEN upper(coalesce(room_type, '')) LIKE '%LAB%' THEN 'LABORATORY'
        WHEN upper(coalesce(room_type, '')) LIKE '%OFFICE%' THEN 'OFFICE'
        WHEN upper(coalesce(room_type, '')) LIKE '%STORE%' THEN 'STORE'
        WHEN upper(coalesce(room_type, '')) LIKE '%PLANT%' THEN 'PLANT_ROOM'
        ELSE 'OTHER'
    END;

UPDATE facilities.facility_rooms
SET bookable = space_type IN ('MEETING_ROOM', 'LECTURE_HALL', 'MOOT_COURTROOM', 'EXAMINATION_HALL',
                              'LABORATORY', 'AUDITORIUM'),
    examination_capable = space_type IN ('LECTURE_HALL', 'MOOT_COURTROOM', 'EXAMINATION_HALL', 'AUDITORIUM');

-- room_type now mirrors space_type for rows that had none, so the two never disagree.
UPDATE facilities.facility_rooms SET room_type = space_type WHERE room_type IS NULL;

CREATE INDEX ix_facility_rooms_site_space_type
    ON facilities.facility_rooms (site_code, space_type);
CREATE INDEX ix_facility_rooms_bookable
    ON facilities.facility_rooms (site_code, bookable)
    WHERE bookable = TRUE;
CREATE INDEX ix_facility_rooms_examination_capable
    ON facilities.facility_rooms (site_code, examination_capable)
    WHERE examination_capable = TRUE;
CREATE INDEX ix_facility_rooms_readiness_updated
    ON facilities.facility_rooms (site_code, readiness_updated_at);

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-04 — device reference integration fields.
--
-- external_reference is the vendor's own identifier, held as a value and never as a
-- foreign key: their database is not ours to join against, and a renumbering on their
-- side must not break our estate.
--
-- status_reported_at is the vendor's observation time, not our receipt time. The
-- staleness warning is about how old the *observation* is; using receipt time would
-- report a six-hour-old reading as fresh.
-- -------------------------------------------------------------------------------------
ALTER TABLE facilities.device_references
    ADD COLUMN external_reference VARCHAR(160),
    ADD COLUMN status_reported_at TIMESTAMPTZ;

CREATE INDEX ix_device_references_external
    ON facilities.device_references (vendor, external_reference)
    WHERE external_reference IS NOT NULL;
CREATE INDEX ix_device_references_status_reported
    ON facilities.device_references (site_code, status_reported_at);

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-01 — zone nesting.
-- -------------------------------------------------------------------------------------
ALTER TABLE facilities.zones
    ADD COLUMN parent_zone_id UUID REFERENCES facilities.zones(id),
    ADD CONSTRAINT ck_zones_parent_not_self CHECK (parent_zone_id IS NULL OR parent_zone_id <> id);

CREATE INDEX ix_zones_parent ON facilities.zones (parent_zone_id);

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-01 — zone membership.
-- Heterogeneous by (type, id) rather than four join tables: a zone covers buildings,
-- floors, rooms and devices, and "what is in this zone" must stay one query for S162a
-- life-safety and S174 recipient-zone resolution.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_zone_memberships (
    id UUID PRIMARY KEY,
    zone_id UUID NOT NULL REFERENCES facilities.zones(id),
    member_type VARCHAR(20) NOT NULL,
    member_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    added_by VARCHAR(160) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_facility_zone_member_type CHECK (member_type IN ('BUILDING', 'FLOOR', 'ROOM', 'DEVICE'))
);

CREATE UNIQUE INDEX ux_facility_zone_membership
    ON facilities.facility_zone_memberships (zone_id, member_type, member_id);
CREATE INDEX ix_facility_zone_membership_member
    ON facilities.facility_zone_memberships (member_type, member_id);
CREATE INDEX ix_facility_zone_membership_site
    ON facilities.facility_zone_memberships (site_code);

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-01 — the facility asset register.
--
-- §21.1: "One asset may have many faults, work orders, schedules and closure evidence
-- records." This is what S153 raises work orders against.
--
-- asset_reference_id points at AVAMP-Lite by value and carries no foreign key: that is a
-- different service's schema and a different deployable.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_assets (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    asset_code VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(40) NOT NULL,
    criticality VARCHAR(20) NOT NULL,
    operational_status VARCHAR(30) NOT NULL,
    room_id UUID REFERENCES facilities.facility_rooms(id),
    location_code VARCHAR(120),
    manufacturer VARCHAR(160),
    model_number VARCHAR(120),
    serial_number VARCHAR(120),
    installed_on DATE,
    warranty_expires_on DATE,
    service_interval_days INTEGER,
    last_serviced_on DATE,
    custodian VARCHAR(160),
    device_reference_id UUID REFERENCES facilities.device_references(id),
    asset_reference_id UUID,
    status_notes VARCHAR(1000),
    status_changed_at TIMESTAMPTZ,
    lifecycle_status VARCHAR(20) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    record_version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    CONSTRAINT uq_facility_assets_site_asset_code UNIQUE (site_code, asset_code),
    CONSTRAINT ck_facility_assets_criticality CHECK (criticality IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_facility_assets_operational_status CHECK
        (operational_status IN ('OPERATIONAL', 'DEGRADED', 'UNDER_MAINTENANCE', 'OUT_OF_SERVICE',
                                'DECOMMISSIONED')),
    CONSTRAINT ck_facility_assets_lifecycle CHECK
        (lifecycle_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT ck_facility_assets_service_interval CHECK
        (service_interval_days IS NULL OR service_interval_days > 0),
    CONSTRAINT ck_facility_assets_warranty_after_install CHECK
        (installed_on IS NULL OR warranty_expires_on IS NULL OR warranty_expires_on >= installed_on)
);

CREATE INDEX ix_facility_assets_site_status
    ON facilities.facility_assets (site_code, operational_status);
CREATE INDEX ix_facility_assets_room
    ON facilities.facility_assets (room_id);
CREATE INDEX ix_facility_assets_category
    ON facilities.facility_assets (site_code, category);
-- The readiness engine's hot path: critical assets that are not operational.
CREATE INDEX ix_facility_assets_impairing
    ON facilities.facility_assets (site_code, criticality, operational_status)
    WHERE operational_status IN ('DEGRADED', 'UNDER_MAINTENANCE', 'OUT_OF_SERVICE');
CREATE INDEX ix_facility_assets_device_reference
    ON facilities.facility_assets (device_reference_id);

-- -------------------------------------------------------------------------------------
-- S153 extension point.
-- The link S152 exists to provide: a fault, and the work order raised from it, can name
-- the asset that failed. Nullable, because a fault can be about a space rather than a
-- machine ("the ceiling is leaking"). No S153 workflow is built here.
-- -------------------------------------------------------------------------------------
ALTER TABLE facilities.facility_faults
    ADD COLUMN facility_asset_id UUID REFERENCES facilities.facility_assets(id),
    ADD COLUMN room_id UUID REFERENCES facilities.facility_rooms(id);

ALTER TABLE facilities.work_orders
    ADD COLUMN facility_asset_id UUID REFERENCES facilities.facility_assets(id),
    ADD COLUMN room_id UUID REFERENCES facilities.facility_rooms(id);

CREATE INDEX ix_facility_faults_asset ON facilities.facility_faults (facility_asset_id);
CREATE INDEX ix_facility_faults_room ON facilities.facility_faults (room_id);
CREATE INDEX ix_work_orders_asset ON facilities.work_orders (facility_asset_id);
CREATE INDEX ix_work_orders_room ON facilities.work_orders (room_id);
