-- =====================================================================================
-- S152 CAFM / IWMS — the readiness model.
--
-- Replaces "readiness is a string somebody typed" with a model that can answer *why* a
-- space is not ready. S159 room booking and the S152-05 dashboard both read it, and
-- S162a life-safety will raise into it.
--
--   SRS-SFL-S152-01  readiness profile as a maintained record
--   SRS-SFL-S152-02  runtime-configurable rules, evaluated at assessment time
--   SRS-SFL-S152-05  blockers by severity, unavailable spaces, stale readiness data
--   NFR 23.8         readiness checklists runtime-configurable and versioned
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Checklists. Applicability is by space type and operating mode, both nullable meaning
-- "any" — one short routine list over every room, one long examination list over halls.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_readiness_checklists (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    checklist_code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    space_type VARCHAR(40),
    operating_mode VARCHAR(20),
    version INTEGER NOT NULL DEFAULT 1,
    lifecycle_status VARCHAR(20) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    record_version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    CONSTRAINT uq_facility_readiness_checklists_site_code UNIQUE (site_code, checklist_code),
    CONSTRAINT ck_facility_readiness_checklists_mode CHECK
        (operating_mode IS NULL OR operating_mode IN ('ROUTINE', 'EXAMINATION')),
    CONSTRAINT ck_facility_readiness_checklists_lifecycle CHECK
        (lifecycle_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT ck_facility_readiness_checklists_version CHECK (version >= 1)
);

CREATE INDEX ix_facility_readiness_checklists_applicability
    ON facilities.facility_readiness_checklists (site_code, space_type, operating_mode)
    WHERE lifecycle_status = 'ACTIVE';

-- -------------------------------------------------------------------------------------
-- Checklist items.
--
-- severity_if_failed is declared on the item, not chosen by the assessor: an assessor
-- records pass or fail, and how much a failure counts was decided when the checklist was
-- approved. That is what keeps two officers assessing the same hall to the same standard.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_readiness_checklist_items (
    id UUID PRIMARY KEY,
    checklist_id UUID NOT NULL REFERENCES facilities.facility_readiness_checklists(id) ON DELETE CASCADE,
    item_code VARCHAR(60) NOT NULL,
    description VARCHAR(500) NOT NULL,
    severity_if_failed VARCHAR(20) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    weight INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_facility_readiness_items_checklist_code UNIQUE (checklist_id, item_code),
    CONSTRAINT ck_facility_readiness_items_severity CHECK
        (severity_if_failed IN ('CRITICAL', 'MAJOR', 'MINOR', 'ADVISORY')),
    CONSTRAINT ck_facility_readiness_items_weight CHECK (weight >= 0)
);

CREATE INDEX ix_facility_readiness_items_checklist
    ON facilities.facility_readiness_checklist_items (checklist_id, sort_order);

-- -------------------------------------------------------------------------------------
-- Assessments. Append-only: an assessment is a statement about a space at a moment,
-- signed by a named assessor, and amending one after the fact destroys the only thing it
-- is good for. A changed space gets a new assessment.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_readiness_assessments (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES facilities.facility_rooms(id),
    site_code VARCHAR(40) NOT NULL,
    checklist_id UUID REFERENCES facilities.facility_readiness_checklists(id),
    checklist_code VARCHAR(60),
    checklist_version INTEGER NOT NULL DEFAULT 0,
    operating_mode VARCHAR(20) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    score INTEGER NOT NULL,
    notes VARCHAR(2000),
    assessed_by VARCHAR(160) NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(120),
    CONSTRAINT ck_facility_readiness_assessments_mode CHECK (operating_mode IN ('ROUTINE', 'EXAMINATION')),
    CONSTRAINT ck_facility_readiness_assessments_outcome CHECK
        (outcome IN ('UNKNOWN', 'READY', 'DEGRADED', 'BLOCKED')),
    CONSTRAINT ck_facility_readiness_assessments_score CHECK (score BETWEEN 0 AND 100)
);

CREATE INDEX ix_facility_readiness_assessments_room
    ON facilities.facility_readiness_assessments (room_id, assessed_at DESC);
CREATE INDEX ix_facility_readiness_assessments_site
    ON facilities.facility_readiness_assessments (site_code, assessed_at DESC);
CREATE INDEX ix_facility_readiness_assessments_outcome
    ON facilities.facility_readiness_assessments (site_code, outcome);

CREATE TRIGGER trg_facility_readiness_assessments_append_only
    BEFORE UPDATE OR DELETE ON facilities.facility_readiness_assessments
    FOR EACH ROW EXECUTE FUNCTION facilities.facility_append_only_guard();

-- -------------------------------------------------------------------------------------
-- Assessment items.
--
-- The item's code, description, severity and weight are copied from the checklist rather
-- than only referenced. The checklist is versioned and will change; a result from March
-- must remain readable against the question that was asked in March.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_readiness_assessment_items (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES facilities.facility_readiness_assessments(id) ON DELETE CASCADE,
    checklist_item_id UUID,
    item_code VARCHAR(60) NOT NULL,
    description VARCHAR(500) NOT NULL,
    severity_if_failed VARCHAR(20) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    weight INTEGER NOT NULL DEFAULT 1,
    passed BOOLEAN NOT NULL,
    comment VARCHAR(1000),
    CONSTRAINT ck_facility_readiness_assessment_items_severity CHECK
        (severity_if_failed IN ('CRITICAL', 'MAJOR', 'MINOR', 'ADVISORY'))
);

CREATE INDEX ix_facility_readiness_assessment_items_assessment
    ON facilities.facility_readiness_assessment_items (assessment_id);

-- -------------------------------------------------------------------------------------
-- Blockers — the reasons a space is not ready.
--
-- The useful record. "BLOCKED" tells an operator to go and find out why; "fire door will
-- not latch — CRITICAL, open 2 hours" tells them what to do. Resolved rather than deleted,
-- because §21.2 protects examination-continuity records from deletion and a blocker
-- cleared just before an examination is what a review asks about.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_readiness_blockers (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES facilities.facility_rooms(id),
    site_code VARCHAR(40) NOT NULL,
    assessment_id UUID REFERENCES facilities.facility_readiness_assessments(id),
    source VARCHAR(30) NOT NULL,
    source_reference VARCHAR(160),
    severity VARCHAR(20) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    raised_by VARCHAR(160) NOT NULL,
    raised_at TIMESTAMPTZ NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by VARCHAR(160),
    resolved_at TIMESTAMPTZ,
    resolution_notes VARCHAR(1000),
    CONSTRAINT ck_facility_readiness_blockers_source CHECK
        (source IN ('CHECKLIST_ITEM', 'ASSET', 'WORK_ORDER', 'MANUAL')),
    CONSTRAINT ck_facility_readiness_blockers_severity CHECK
        (severity IN ('CRITICAL', 'MAJOR', 'MINOR', 'ADVISORY')),
    -- A resolved blocker must say who cleared it, when, and why. An unexplained closure
    -- leaves a reviewer unable to tell a fix from a dismissal.
    CONSTRAINT ck_facility_readiness_blockers_resolution CHECK
        (resolved = FALSE OR (resolved_by IS NOT NULL AND resolved_at IS NOT NULL
                              AND resolution_notes IS NOT NULL))
);

-- The readiness engine's hot path: open blockers for a space, worst first.
CREATE INDEX ix_facility_readiness_blockers_open
    ON facilities.facility_readiness_blockers (room_id, severity)
    WHERE resolved = FALSE;
CREATE INDEX ix_facility_readiness_blockers_site_open
    ON facilities.facility_readiness_blockers (site_code, severity, raised_at)
    WHERE resolved = FALSE;
CREATE INDEX ix_facility_readiness_blockers_assessment
    ON facilities.facility_readiness_blockers (assessment_id);
-- Lets an asset status change find and clear the blockers it raised, without a scan.
CREATE INDEX ix_facility_readiness_blockers_source_reference
    ON facilities.facility_readiness_blockers (source, source_reference)
    WHERE resolved = FALSE;

-- -------------------------------------------------------------------------------------
-- A starting checklist per operating mode, seeded as platform defaults for the sites that
-- already exist. Without one, the first assessment has nothing to assess against and the
-- feature reads as broken rather than as unconfigured.
-- -------------------------------------------------------------------------------------
DO $$
DECLARE
    site RECORD;
    routine_id UUID;
    exam_id UUID;
BEGIN
    FOR site IN SELECT site_code FROM facilities.sites LOOP
        routine_id := gen_random_uuid();
        exam_id := gen_random_uuid();

        INSERT INTO facilities.facility_readiness_checklists
            (id, site_code, checklist_code, name, description, space_type, operating_mode, version,
             lifecycle_status, created_by, created_at, last_modified_by, last_modified_at, record_version,
             source_channel)
        VALUES
            (routine_id, site.site_code, 'ROUTINE-BASE', 'Routine space readiness',
             'Baseline checks applied to any space during routine operations.', NULL, 'ROUTINE', 1,
             'ACTIVE', 'system', now(), 'system', now(), 0, 'SYSTEM'),
            (exam_id, site.site_code, 'EXAM-HALL', 'Examination hall readiness',
             'Checks a hall must pass before it can host an examination.', 'EXAMINATION_HALL', 'EXAMINATION', 1,
             'ACTIVE', 'system', now(), 'system', now(), 0, 'SYSTEM');

        INSERT INTO facilities.facility_readiness_checklist_items
            (id, checklist_id, item_code, description, severity_if_failed, mandatory, weight, sort_order)
        VALUES
            (gen_random_uuid(), routine_id, 'ACCESS', 'Space is accessible and unobstructed',
             'MAJOR', TRUE, 2, 10),
            (gen_random_uuid(), routine_id, 'LIGHTING', 'Lighting is working throughout the space',
             'MAJOR', TRUE, 2, 20),
            (gen_random_uuid(), routine_id, 'POWER', 'Power outlets are live and undamaged',
             'MAJOR', TRUE, 2, 30),
            (gen_random_uuid(), routine_id, 'CLEANLINESS', 'Space is clean and waste has been cleared',
             'MINOR', FALSE, 1, 40),
            (gen_random_uuid(), routine_id, 'FIRE-EGRESS', 'Fire exits are unobstructed and doors latch',
             'CRITICAL', TRUE, 3, 50),
            (gen_random_uuid(), exam_id, 'FIRE-EGRESS', 'Fire exits are unobstructed and doors latch',
             'CRITICAL', TRUE, 3, 10),
            (gen_random_uuid(), exam_id, 'SEATING', 'Seating is laid out to the approved examination plan',
             'CRITICAL', TRUE, 3, 20),
            (gen_random_uuid(), exam_id, 'POWER-BACKUP', 'Backup power is available and tested',
             'CRITICAL', TRUE, 3, 30),
            (gen_random_uuid(), exam_id, 'CLOCK-PA', 'Clock and public address are working',
             'MAJOR', TRUE, 2, 40),
            (gen_random_uuid(), exam_id, 'SIGNAGE', 'Examination signage is displayed',
             'MINOR', FALSE, 1, 50),
            (gen_random_uuid(), exam_id, 'DEVICE-SWEEP', 'Prohibited-device sweep completed',
             'CRITICAL', TRUE, 3, 60);
    END LOOP;
END $$;
