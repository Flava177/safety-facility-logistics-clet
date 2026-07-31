-- =============================================================================================
-- S153 CMMS — bringing the pre-S152 maintenance spine onto the platform, and building on it.
--
-- The existing facility_faults and work_orders tables are ALTERED, not replaced. They hold real
-- rows in every environment this has ever run in, and the endpoint paths that read them are printed
-- in the S153 guide. Dropping and recreating would have been shorter to write and would have thrown
-- away the only fault history CLET has.
--
-- Every added column is nullable or defaulted first, backfilled, and only then constrained. A NOT
-- NULL added in one step against a non-empty table fails, and fails at deploy time rather than in
-- review.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- Number sequences.
--
-- Both numbers were previously derived from the first eight characters of a random UUID, which is
-- neither ordered nor collision-proof, and read as noise on a printed job sheet. A sequence gives
-- FLT-CLET-HQ-000123: sortable, sayable over a radio, and unique without coordination. Gaps after a
-- rollback are harmless; duplicates are not, which is why this is a sequence and not a row count.
-- ---------------------------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS facilities.fault_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS facilities.work_order_number_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------------------------
-- facility_faults
-- ---------------------------------------------------------------------------------------------
ALTER TABLE facilities.facility_faults
    ADD COLUMN IF NOT EXISTS room_id UUID,
    ADD COLUMN IF NOT EXISTS asset_id UUID,
    ADD COLUMN IF NOT EXISTS triaged_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS triaged_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS triage_notes VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS duplicate_of_fault_id UUID,
    ADD COLUMN IF NOT EXISTS sla_due_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS escalation_level INTEGER,
    ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS blocker_raised BOOLEAN,
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolution_notes VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS record_version BIGINT,
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(40),
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(120);

-- location_code was VARCHAR(80) and is now 120, matching the estate's own location codes.
ALTER TABLE facilities.facility_faults ALTER COLUMN location_code TYPE VARCHAR(120);
ALTER TABLE facilities.facility_faults ALTER COLUMN location_code DROP NOT NULL;

-- Backfill. The provenance a pre-S152 row never had is reconstructed from what it did have:
-- the reporter and the report time. That is honest — it says who created the record and when —
-- and it is better than a synthetic 'migration' actor, which would erase the only actor known.
UPDATE facilities.facility_faults
SET escalation_level = COALESCE(escalation_level, 0),
    blocker_raised   = COALESCE(blocker_raised, FALSE),
    lifecycle_status = COALESCE(lifecycle_status, 'ACTIVE'),
    created_by       = COALESCE(created_by, reported_by),
    created_at       = COALESCE(created_at, reported_at),
    last_modified_by = COALESCE(last_modified_by, reported_by),
    last_modified_at = COALESCE(last_modified_at, reported_at),
    record_version   = COALESCE(record_version, 0),
    -- Pre-S152 faults arrived through the retired static page, which was a browser.
    source_channel   = COALESCE(source_channel, 'WEB');

-- Link existing faults to the estate where the location code already names a room at that site.
-- Left unlinked where it does not: a fault against 'CAR-PARK-B' has no room and inventing one would
-- put a readiness blocker on a space that does not exist.
UPDATE facilities.facility_faults f
SET room_id = r.id
FROM facilities.facility_rooms r
WHERE f.room_id IS NULL
  AND r.site_code = f.site_code
  AND r.room_code = UPPER(f.location_code);

ALTER TABLE facilities.facility_faults
    ALTER COLUMN escalation_level SET NOT NULL,
    ALTER COLUMN escalation_level SET DEFAULT 0,
    ALTER COLUMN blocker_raised SET NOT NULL,
    ALTER COLUMN blocker_raised SET DEFAULT FALSE,
    ALTER COLUMN lifecycle_status SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN last_modified_by SET NOT NULL,
    ALTER COLUMN last_modified_at SET NOT NULL,
    ALTER COLUMN record_version SET NOT NULL,
    ALTER COLUMN record_version SET DEFAULT 0,
    ALTER COLUMN source_channel SET NOT NULL;

ALTER TABLE facilities.facility_faults
    DROP CONSTRAINT IF EXISTS ck_facility_faults_located;
ALTER TABLE facilities.facility_faults
    ADD CONSTRAINT ck_facility_faults_located
        CHECK (room_id IS NOT NULL OR location_code IS NOT NULL);

ALTER TABLE facilities.facility_faults
    DROP CONSTRAINT IF EXISTS ck_facility_faults_escalation;
ALTER TABLE facilities.facility_faults
    ADD CONSTRAINT ck_facility_faults_escalation CHECK (escalation_level >= 0);

CREATE INDEX IF NOT EXISTS ix_facility_faults_room ON facilities.facility_faults (room_id);
CREATE INDEX IF NOT EXISTS ix_facility_faults_asset ON facilities.facility_faults (asset_id);
CREATE INDEX IF NOT EXISTS ix_facility_faults_reported_by ON facilities.facility_faults (reported_by);
-- Partial: the escalation sweep only ever reads open, overdue rows, and the closed ones outnumber
-- them within a year.
CREATE INDEX IF NOT EXISTS ix_facility_faults_sla_open
    ON facilities.facility_faults (sla_due_at)
    WHERE status IN ('REPORTED', 'TRIAGED', 'WORK_ORDER_CREATED');

-- ---------------------------------------------------------------------------------------------
-- work_orders
-- ---------------------------------------------------------------------------------------------
ALTER TABLE facilities.work_orders
    ADD COLUMN IF NOT EXISTS work_order_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS schedule_id UUID,
    ADD COLUMN IF NOT EXISTS room_id UUID,
    ADD COLUMN IF NOT EXISTS asset_id UUID,
    ADD COLUMN IF NOT EXISTS description VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS vendor_id UUID,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS hold_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS held_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS total_held_seconds BIGINT,
    ADD COLUMN IF NOT EXISTS sla_due_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS escalation_level INTEGER,
    ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS evidence_required INTEGER,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completion_notes VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS closed_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS created_by_actor VARCHAR(160),
    ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(160),
    ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS record_version BIGINT,
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(40),
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(120);

ALTER TABLE facilities.work_orders ALTER COLUMN location_code TYPE VARCHAR(120);
ALTER TABLE facilities.work_orders ALTER COLUMN location_code DROP NOT NULL;
ALTER TABLE facilities.work_orders ALTER COLUMN facility_fault_id DROP NOT NULL;
ALTER TABLE facilities.work_orders ALTER COLUMN fault_number DROP NOT NULL;

-- The old table already had created_by and created_at with exactly the meaning the embeddable wants,
-- so they are reused rather than duplicated; created_by_actor above exists only to hold the value
-- while the column is renamed, and is dropped at the end of this block.
UPDATE facilities.work_orders
SET work_order_type    = COALESCE(work_order_type, 'CORRECTIVE'),
    total_held_seconds = COALESCE(total_held_seconds, 0),
    escalation_level   = COALESCE(escalation_level, 0),
    -- Pre-S153 orders were closed with a note and no evidence. Requiring it retrospectively would
    -- make every historic row unclosable; the rule applies from here forward.
    evidence_required  = COALESCE(evidence_required, 0),
    lifecycle_status   = COALESCE(lifecycle_status, 'ACTIVE'),
    last_modified_by   = COALESCE(last_modified_by, created_by),
    last_modified_at   = COALESCE(last_modified_at, COALESCE(closed_at, assigned_at, created_at)),
    record_version     = COALESCE(record_version, 0),
    source_channel     = COALESCE(source_channel, 'WEB'),
    completed_at       = COALESCE(completed_at, closed_at),
    closed_by          = COALESCE(closed_by, CASE WHEN closed_at IS NOT NULL THEN created_by END);

ALTER TABLE facilities.work_orders DROP COLUMN IF EXISTS created_by_actor;

UPDATE facilities.work_orders w
SET room_id = r.id
FROM facilities.facility_rooms r
WHERE w.room_id IS NULL
  AND r.site_code = w.site_code
  AND r.room_code = UPPER(w.location_code);

-- Carry the fault's room across for any order whose own location code did not resolve.
UPDATE facilities.work_orders w
SET room_id = f.room_id
FROM facilities.facility_faults f
WHERE w.room_id IS NULL
  AND w.facility_fault_id = f.id
  AND f.room_id IS NOT NULL;

ALTER TABLE facilities.work_orders
    ALTER COLUMN work_order_type SET NOT NULL,
    ALTER COLUMN total_held_seconds SET NOT NULL,
    ALTER COLUMN total_held_seconds SET DEFAULT 0,
    ALTER COLUMN escalation_level SET NOT NULL,
    ALTER COLUMN escalation_level SET DEFAULT 0,
    ALTER COLUMN evidence_required SET NOT NULL,
    ALTER COLUMN evidence_required SET DEFAULT 0,
    ALTER COLUMN lifecycle_status SET NOT NULL,
    ALTER COLUMN last_modified_by SET NOT NULL,
    ALTER COLUMN last_modified_at SET NOT NULL,
    ALTER COLUMN record_version SET NOT NULL,
    ALTER COLUMN record_version SET DEFAULT 0,
    ALTER COLUMN source_channel SET NOT NULL;

ALTER TABLE facilities.work_orders
    DROP CONSTRAINT IF EXISTS ck_work_orders_corrective_has_fault;
ALTER TABLE facilities.work_orders
    ADD CONSTRAINT ck_work_orders_corrective_has_fault
        CHECK (work_order_type <> 'CORRECTIVE' OR facility_fault_id IS NOT NULL);

ALTER TABLE facilities.work_orders
    DROP CONSTRAINT IF EXISTS ck_work_orders_preventive_has_asset;
ALTER TABLE facilities.work_orders
    ADD CONSTRAINT ck_work_orders_preventive_has_asset
        CHECK (work_order_type <> 'PREVENTIVE' OR asset_id IS NOT NULL);

ALTER TABLE facilities.work_orders
    DROP CONSTRAINT IF EXISTS ck_work_orders_counters;
ALTER TABLE facilities.work_orders
    ADD CONSTRAINT ck_work_orders_counters
        CHECK (escalation_level >= 0 AND evidence_required >= 0 AND total_held_seconds >= 0);

-- The old unique index assumed every order answered a fault. It must now allow the many preventive
-- orders that answer none, so it becomes partial.
DROP INDEX IF EXISTS facilities.ux_work_orders_facility_fault;
CREATE UNIQUE INDEX IF NOT EXISTS ux_work_orders_facility_fault
    ON facilities.work_orders (facility_fault_id)
    WHERE facility_fault_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_work_orders_room ON facilities.work_orders (room_id);
CREATE INDEX IF NOT EXISTS ix_work_orders_asset ON facilities.work_orders (asset_id);
CREATE INDEX IF NOT EXISTS ix_work_orders_vendor ON facilities.work_orders (vendor_id);
CREATE INDEX IF NOT EXISTS ix_work_orders_schedule ON facilities.work_orders (schedule_id);
CREATE INDEX IF NOT EXISTS ix_work_orders_sla_open
    ON facilities.work_orders (sla_due_at)
    WHERE status NOT IN ('CLOSED', 'CANCELLED', 'COMPLETED');

-- ---------------------------------------------------------------------------------------------
-- maintenance_vendors
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.maintenance_vendors (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(40)  NOT NULL,
    vendor_code         VARCHAR(80)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    specialisation      VARCHAR(200),
    contact_name        VARCHAR(200),
    contact_email       VARCHAR(200),
    contact_phone       VARCHAR(60),
    response_hours      INTEGER,
    contract_reference  VARCHAR(120),
    contract_expires_on DATE,
    -- The procurement system's identifier for the same company. A value, never a foreign key: this
    -- service does not own supplier data and must not look like it does.
    external_vendor_id  VARCHAR(120),
    lifecycle_status    VARCHAR(20)  NOT NULL,
    created_by          VARCHAR(160) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    last_modified_by    VARCHAR(160) NOT NULL,
    last_modified_at    TIMESTAMPTZ  NOT NULL,
    record_version      BIGINT       NOT NULL DEFAULT 0,
    source_channel      VARCHAR(40)  NOT NULL,
    correlation_id      VARCHAR(120),
    CONSTRAINT ck_maintenance_vendors_response CHECK (response_hours IS NULL OR response_hours > 0)
);

-- Partial: an archived vendor releases its code, matching RecordLifecycleStatus.occupiesIdentifier.
CREATE UNIQUE INDEX IF NOT EXISTS ux_maintenance_vendors_code
    ON facilities.maintenance_vendors (site_code, vendor_code)
    WHERE lifecycle_status <> 'ARCHIVED';

-- ---------------------------------------------------------------------------------------------
-- preventive_schedules
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.preventive_schedules (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)  NOT NULL,
    schedule_code      VARCHAR(80)  NOT NULL,
    name               VARCHAR(200) NOT NULL,
    description        VARCHAR(2000),
    asset_id           UUID         NOT NULL REFERENCES facilities.facility_assets (id),
    room_id            UUID,
    interval_days      INTEGER      NOT NULL,
    lead_time_days     INTEGER      NOT NULL,
    priority           VARCHAR(20)  NOT NULL,
    work_order_type    VARCHAR(20)  NOT NULL,
    next_due_on        DATE         NOT NULL,
    -- The cycle most recently generated for. This column is the idempotency: a run that has already
    -- generated for next_due_on generates nothing, however often it is triggered.
    last_generated_for DATE,
    last_generated_at  TIMESTAMPTZ,
    last_work_order_id UUID,
    lifecycle_status   VARCHAR(20)  NOT NULL,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    record_version     BIGINT       NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)  NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ck_preventive_schedules_interval CHECK (interval_days > 0),
    -- A lead time at or beyond the interval raises the next order before the last could be done, so
    -- the queue fills with overlapping duplicates forever. Enforced in the aggregate too; here as
    -- well because a migration or a direct insert does not go through the aggregate.
    CONSTRAINT ck_preventive_schedules_lead CHECK (lead_time_days >= 0 AND lead_time_days < interval_days),
    CONSTRAINT ck_preventive_schedules_type CHECK (work_order_type <> 'CORRECTIVE')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_preventive_schedules_code
    ON facilities.preventive_schedules (site_code, schedule_code)
    WHERE lifecycle_status <> 'ARCHIVED';
CREATE INDEX IF NOT EXISTS ix_preventive_schedules_asset
    ON facilities.preventive_schedules (asset_id);
CREATE INDEX IF NOT EXISTS ix_preventive_schedules_due
    ON facilities.preventive_schedules (next_due_on)
    WHERE lifecycle_status = 'ACTIVE';

-- ---------------------------------------------------------------------------------------------
-- work_order_parts
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.work_order_parts (
    id            UUID PRIMARY KEY,
    work_order_id UUID          NOT NULL REFERENCES facilities.work_orders (id),
    part_code     VARCHAR(80)   NOT NULL,
    description   VARCHAR(400)  NOT NULL,
    quantity      INTEGER       NOT NULL,
    unit_cost     NUMERIC(14,2),
    currency      VARCHAR(3)    NOT NULL DEFAULT 'GHS',
    supplier      VARCHAR(200),
    recorded_by   VARCHAR(160)  NOT NULL,
    recorded_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_work_order_parts_quantity CHECK (quantity > 0),
    CONSTRAINT ck_work_order_parts_cost CHECK (unit_cost IS NULL OR unit_cost >= 0)
);

CREATE INDEX IF NOT EXISTS ix_work_order_parts_order
    ON facilities.work_order_parts (work_order_id);

-- ---------------------------------------------------------------------------------------------
-- maintenance_evidence
--
-- By reference only. There is no bytea column and there must never be one: the architecture standard
-- stores evidence references and hashes, and the bytes live in the document/object-storage service.
--
-- content_hash is VARCHAR(64) with a length CHECK, not CHAR(64). Hibernate validates CHAR(n) against
-- a different JDBC type and refuses to start — found in the S152 round by running the service against
-- a real PostgreSQL rather than by any test.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.maintenance_evidence (
    id              UUID PRIMARY KEY,
    work_order_id   UUID         NOT NULL REFERENCES facilities.work_orders (id),
    site_code       VARCHAR(40)  NOT NULL,
    evidence_type   VARCHAR(30)  NOT NULL,
    file_reference  VARCHAR(500) NOT NULL,
    file_name       VARCHAR(300),
    media_type      VARCHAR(120),
    size_bytes      BIGINT,
    content_hash    VARCHAR(64)  NOT NULL,
    retention_class VARCHAR(30)  NOT NULL,
    legal_hold      BOOLEAN      NOT NULL DEFAULT FALSE,
    notes           VARCHAR(2000),
    uploaded_by     VARCHAR(160) NOT NULL,
    uploaded_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_maintenance_evidence_hash CHECK (char_length(content_hash) = 64),
    CONSTRAINT ck_maintenance_evidence_size CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE INDEX IF NOT EXISTS ix_maintenance_evidence_order
    ON facilities.maintenance_evidence (work_order_id);
-- Two uploads of the same file to one work order are a mistake, not a second piece of evidence.
CREATE UNIQUE INDEX IF NOT EXISTS ux_maintenance_evidence_hash
    ON facilities.maintenance_evidence (work_order_id, content_hash);
-- The disposal sweep, when it is built, reads exactly this.
CREATE INDEX IF NOT EXISTS ix_maintenance_evidence_retention
    ON facilities.maintenance_evidence (retention_class, uploaded_at)
    WHERE legal_hold = FALSE;

-- ---------------------------------------------------------------------------------------------
-- Default S153 runtime configuration.
--
-- Inserted so the module works on a fresh database rather than failing to compute a deadline because
-- nobody ran a seed script. Site-scoped values override these; see MaintenanceConfiguration.
-- ---------------------------------------------------------------------------------------------
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version,
     updated_by, updated_at)
SELECT gen_random_uuid(), seed.config_key, NULL, seed.config_value, seed.value_type, seed.description,
       NOW(), 0, 'system', NOW()
FROM (VALUES
    ('maintenance.sla.resolution.critical', 'PT4H',  'DURATION', 'Time to resolve a critical fault.'),
    ('maintenance.sla.resolution.high',     'PT24H', 'DURATION', 'Time to resolve a high-priority fault.'),
    ('maintenance.sla.resolution.medium',   'P3D',   'DURATION', 'Time to resolve a medium-priority fault.'),
    ('maintenance.sla.resolution.low',      'P14D',  'DURATION', 'Time to resolve a low-priority fault.'),
    ('maintenance.sla.response.critical',   'PT30M', 'DURATION', 'Time to acknowledge a critical fault.'),
    ('maintenance.sla.response.high',       'PT2H',  'DURATION', 'Time to acknowledge a high-priority fault.'),
    ('maintenance.sla.response.medium',     'PT8H',  'DURATION', 'Time to acknowledge a medium-priority fault.'),
    ('maintenance.sla.response.low',        'PT24H', 'DURATION', 'Time to acknowledge a low-priority fault.'),
    ('maintenance.sla.examination-factor',  '0.5',   'DECIMAL',  'SLA multiplier while a site is in examination mode.'),
    ('maintenance.escalation.interval',     'PT4H',  'DURATION', 'Time between successive escalation levels.'),
    ('maintenance.escalation.max-level',    '3',     'INTEGER',  'Top of the escalation ladder.'),
    ('maintenance.readiness.blocker-threshold', 'HIGH', 'STRING', 'Fault priority at which a space is blocked.'),
    ('maintenance.closure.evidence-threshold',  'HIGH', 'STRING', 'Fault priority at which closure evidence is required.'),
    ('maintenance.closure.evidence-count',  '1',     'INTEGER',  'Evidence items required to close, above the threshold.'),
    ('maintenance.preventive.generation-batch', '200', 'INTEGER', 'Schedules processed per generation run.')
) AS seed(config_key, config_value, value_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM facilities.facility_runtime_configuration existing
    WHERE existing.config_key = seed.config_key
      AND existing.site_code IS NULL
      AND existing.effective_to IS NULL
);
