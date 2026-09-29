-- =============================================================================================
-- S169 Cleaning & Janitorial Schedule Management (SRS CLET/DTI/CL9/SFL/SRS/2026/002 §3.1).
--
-- Twelve new tables on the S152 estate. Nothing existing is altered: the S159 half of S169-01 (the
-- booking's cleaning_requirement column) landed in V15, and S169 reads it through the booking module's
-- lifecycle observer rather than through a join.
--
-- The rules in this file that Java alone cannot hold:
--
--   * One task per routine-schedule occurrence per room (ux_cleaning_tasks_schedule_occurrence). The
--     generation sweep checks before it writes; this is what makes a second sweep, or two instances
--     sweeping at once, a no-op rather than a duplicated clean (SRS-SFL-S169-01).
--   * A booking-origin task always carries its booking back-reference
--     (ck_cleaning_tasks_booking_reference). SRS-SFL-S169-01's validation rule, stated where no code
--     path can skip it.
--   * Photo evidence is a reference plus a SHA-256 content hash, never bytes
--     (ck_cleaning_task_items_photo_*). There is no object store in this build.
--   * One SLA breach per task per breach type (ux_cleaning_sla_breaches_task_type), so a re-evaluation
--     cannot double-count a vendor's scorecard.
--
-- Every table carries site_code NOT NULL and the seven standard columns, and the migration ends with
-- facilities.apply_site_scope_policies() so RLS exists from the first migration (CORR-06).
--
-- Cross-module identifiers are held by value: booking_id (S159), reservation event_reference (S173),
-- vendor_master_reference (S133). The only foreign keys are to facility_rooms (S152) and to this
-- module's own tables.
-- =============================================================================================

-- Task numbers: CT-MAIN-000123. Sortable and sayable, like BK- and WO- references.
CREATE SEQUENCE IF NOT EXISTS facilities.cleaning_task_number_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------------------------
-- cleaning_vendor_master_references - the honest stand-in for Vendor Master (S133).
--
-- S133 is an external system not built in this programme. Until it is, a cleaning vendor can be
-- registered only against a reference an authorised user has recorded here as known to S133. The
-- adapter that reads this table says so in its name and its responses; nothing reports S133 as
-- integrated.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_vendor_master_references (
    id                      UUID PRIMARY KEY,
    site_code               VARCHAR(40)  NOT NULL,
    vendor_master_reference VARCHAR(80)  NOT NULL,
    legal_name              VARCHAR(200) NOT NULL,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    evidence_note           VARCHAR(1000),
    created_by              VARCHAR(160) NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    last_modified_by        VARCHAR(160) NOT NULL,
    last_modified_at        TIMESTAMPTZ  NOT NULL,
    record_version          BIGINT       NOT NULL DEFAULT 0,
    source_channel          VARCHAR(40)  NOT NULL,
    correlation_id          VARCHAR(120)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_vendor_master_refs
    ON facilities.cleaning_vendor_master_references (site_code, vendor_master_reference);

-- ---------------------------------------------------------------------------------------------
-- cleaning_vendors - a Vendor Master vendor engaged for cleaning at a site.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_vendors (
    id                      UUID PRIMARY KEY,
    site_code               VARCHAR(40)  NOT NULL,
    vendor_master_reference VARCHAR(80)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    created_by              VARCHAR(160) NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    last_modified_by        VARCHAR(160) NOT NULL,
    last_modified_at        TIMESTAMPTZ  NOT NULL,
    record_version          BIGINT       NOT NULL DEFAULT 0,
    source_channel          VARCHAR(40)  NOT NULL,
    correlation_id          VARCHAR(120),
    CONSTRAINT ck_cleaning_vendors_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_vendors_reference
    ON facilities.cleaning_vendors (site_code, vendor_master_reference);

-- ---------------------------------------------------------------------------------------------
-- cleaning_vendor_sla_terms - contracted SLA terms, versioned (SRS-SFL-S169-03).
--
-- Never updated in place except to close a version (effective_to). A breach is judged against the
-- version in force when the task was raised, so a renegotiated contract does not rewrite last month's
-- scorecard.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_vendor_sla_terms (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)  NOT NULL,
    vendor_id          UUID         NOT NULL REFERENCES facilities.cleaning_vendors (id),
    terms_version      INTEGER      NOT NULL,
    response_minutes   INTEGER      NOT NULL,
    completion_minutes INTEGER      NOT NULL,
    quality_floor      NUMERIC(3,2) NOT NULL,
    effective_from     TIMESTAMPTZ  NOT NULL,
    effective_to       TIMESTAMPTZ,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    record_version     BIGINT       NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)  NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ck_cleaning_sla_terms_positive CHECK (response_minutes > 0 AND completion_minutes > 0),
    CONSTRAINT ck_cleaning_sla_terms_floor CHECK (quality_floor >= 1 AND quality_floor <= 5),
    CONSTRAINT ck_cleaning_sla_terms_window CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_sla_terms_version
    ON facilities.cleaning_vendor_sla_terms (vendor_id, terms_version);
-- One version in force per vendor. Creating a new one closes the old one in the same transaction.
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_sla_terms_current
    ON facilities.cleaning_vendor_sla_terms (vendor_id) WHERE effective_to IS NULL;

-- ---------------------------------------------------------------------------------------------
-- cleaning_schedules - routine schedules by site, space type (optionally one room) and frequency.
--
-- days_of_week is a comma-separated list of ISO day names (MONDAY,...), and times_of_day of HH:MM
-- values, interpreted in the time zone runtime configuration names (cleaning.schedule.time-zone).
-- Stored as text rather than arrays so the entity maps with no custom type; the CHECKs keep the
-- frequency and the day list consistent.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_schedules (
    id                UUID PRIMARY KEY,
    site_code         VARCHAR(40)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    space_type        VARCHAR(40)  NOT NULL,
    room_id           UUID REFERENCES facilities.facility_rooms (id),
    frequency         VARCHAR(20)  NOT NULL,
    days_of_week      VARCHAR(120),
    times_of_day      VARCHAR(200) NOT NULL,
    duration_minutes  INTEGER      NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by        VARCHAR(160) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    last_modified_by  VARCHAR(160) NOT NULL,
    last_modified_at  TIMESTAMPTZ  NOT NULL,
    record_version    BIGINT       NOT NULL DEFAULT 0,
    source_channel    VARCHAR(40)  NOT NULL,
    correlation_id    VARCHAR(120),
    CONSTRAINT ck_cleaning_schedules_frequency CHECK (frequency IN ('DAILY', 'WEEKLY', 'SPECIFIC_WEEKDAYS')),
    CONSTRAINT ck_cleaning_schedules_days CHECK (frequency = 'DAILY' OR days_of_week IS NOT NULL),
    CONSTRAINT ck_cleaning_schedules_duration CHECK (duration_minutes BETWEEN 5 AND 1440)
);
CREATE INDEX IF NOT EXISTS ix_cleaning_schedules_site ON facilities.cleaning_schedules (site_code, active);

-- ---------------------------------------------------------------------------------------------
-- cleaning_checklist_templates / _items - the standard checklist per space type (SRS-SFL-S169-02).
--
-- Versioned: a new template for the same site and space type supersedes the active one. A task copies
-- the items when it is raised, so editing a template never changes what a cleaner already signed for.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_checklist_templates (
    id                UUID PRIMARY KEY,
    site_code         VARCHAR(40)  NOT NULL,
    space_type        VARCHAR(40)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    template_version  INTEGER      NOT NULL,
    active            BOOLEAN      NOT NULL,
    created_by        VARCHAR(160) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    last_modified_by  VARCHAR(160) NOT NULL,
    last_modified_at  TIMESTAMPTZ  NOT NULL,
    record_version    BIGINT       NOT NULL DEFAULT 0,
    source_channel    VARCHAR(40)  NOT NULL,
    correlation_id    VARCHAR(120)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_templates_version
    ON facilities.cleaning_checklist_templates (site_code, space_type, template_version);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_templates_active
    ON facilities.cleaning_checklist_templates (site_code, space_type) WHERE active;

CREATE TABLE IF NOT EXISTS facilities.cleaning_checklist_template_items (
    id                UUID PRIMARY KEY,
    site_code         VARCHAR(40)  NOT NULL,
    template_id       UUID         NOT NULL REFERENCES facilities.cleaning_checklist_templates (id),
    item_code         VARCHAR(60)  NOT NULL,
    label             VARCHAR(300) NOT NULL,
    sequence_no       INTEGER      NOT NULL,
    photo_required    BOOLEAN      NOT NULL,
    created_by        VARCHAR(160) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    last_modified_by  VARCHAR(160) NOT NULL,
    last_modified_at  TIMESTAMPTZ  NOT NULL,
    record_version    BIGINT       NOT NULL DEFAULT 0,
    source_channel    VARCHAR(40)  NOT NULL,
    correlation_id    VARCHAR(120)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_template_items_code
    ON facilities.cleaning_checklist_template_items (template_id, item_code);

-- ---------------------------------------------------------------------------------------------
-- cleaning_tasks - one clean of one room in one window, whatever raised it.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_tasks (
    id                             UUID PRIMARY KEY,
    task_number                    VARCHAR(40)  NOT NULL,
    site_code                      VARCHAR(40)  NOT NULL,
    room_id                        UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code                      VARCHAR(80)  NOT NULL,
    space_type                     VARCHAR(40)  NOT NULL,
    origin                         VARCHAR(30)  NOT NULL,
    title                          VARCHAR(200) NOT NULL,
    description                    VARCHAR(2000),
    schedule_id                    UUID REFERENCES facilities.cleaning_schedules (id),
    occurrence_start               TIMESTAMPTZ,
    -- S159 booking back-reference, by value (S169-01 validation).
    booking_id                     UUID,
    booking_reference              VARCHAR(40),
    -- S173 reference, by value (S169-04).
    reservation_id                 UUID,
    event_reference                VARCHAR(120),
    window_start                   TIMESTAMPTZ  NOT NULL,
    due_by                         TIMESTAMPTZ  NOT NULL,
    status                         VARCHAR(20)  NOT NULL,
    requested_by                   VARCHAR(160) NOT NULL,
    requested_at                   TIMESTAMPTZ  NOT NULL,
    assignee_type                  VARCHAR(20),
    assigned_to                    VARCHAR(160),
    vendor_id                      UUID REFERENCES facilities.cleaning_vendors (id),
    assigned_at                    TIMESTAMPTZ,
    started_at                     TIMESTAMPTZ,
    completed_at                   TIMESTAMPTZ,
    completed_by                   VARCHAR(160),
    completion_notes               VARCHAR(2000),
    -- What the vendor said, kept apart from completed_at and never copied into it (S169-03).
    vendor_reported_completed_at   TIMESTAMPTZ,
    completion_discrepancy_seconds BIGINT,
    cancelled_at                   TIMESTAMPTZ,
    cancellation_reason            VARCHAR(2000),
    checklist_template_id          UUID,
    checklist_template_version     INTEGER,
    created_by                     VARCHAR(160) NOT NULL,
    created_at                     TIMESTAMPTZ  NOT NULL,
    last_modified_by               VARCHAR(160) NOT NULL,
    last_modified_at               TIMESTAMPTZ  NOT NULL,
    record_version                 BIGINT       NOT NULL DEFAULT 0,
    source_channel                 VARCHAR(40)  NOT NULL,
    correlation_id                 VARCHAR(120),
    CONSTRAINT ck_cleaning_tasks_origin CHECK (origin IN
        ('ROUTINE', 'BOOKING_SETUP', 'BOOKING_TEARDOWN', 'REACTIVE', 'EVENT', 'ADHOC')),
    CONSTRAINT ck_cleaning_tasks_status CHECK (status IN
        ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_cleaning_tasks_assignee_type CHECK (assignee_type IS NULL OR assignee_type IN ('STAFF', 'VENDOR')),
    CONSTRAINT ck_cleaning_tasks_window CHECK (due_by > window_start),
    CONSTRAINT ck_cleaning_tasks_routine CHECK
        (origin <> 'ROUTINE' OR (schedule_id IS NOT NULL AND occurrence_start IS NOT NULL)),
    CONSTRAINT ck_cleaning_tasks_booking_reference CHECK
        (origin NOT IN ('BOOKING_SETUP', 'BOOKING_TEARDOWN') OR (booking_id IS NOT NULL AND booking_reference IS NOT NULL)),
    CONSTRAINT ck_cleaning_tasks_event CHECK (origin <> 'EVENT' OR event_reference IS NOT NULL),
    CONSTRAINT ck_cleaning_tasks_vendor CHECK (assignee_type IS DISTINCT FROM 'VENDOR' OR vendor_id IS NOT NULL),
    CONSTRAINT ck_cleaning_tasks_completed CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT ck_cleaning_tasks_cancelled CHECK (status <> 'CANCELLED' OR cancellation_reason IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_tasks_number ON facilities.cleaning_tasks (task_number);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_tasks_schedule_occurrence
    ON facilities.cleaning_tasks (schedule_id, room_id, occurrence_start) WHERE schedule_id IS NOT NULL;
-- One live setup and one live teardown clean per booking. A cancelled one does not count, so a
-- cancelled-then-recreated task is possible; two live ones for the same booking are not.
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_tasks_booking_origin
    ON facilities.cleaning_tasks (booking_id, origin)
    WHERE booking_id IS NOT NULL AND status <> 'CANCELLED' AND origin IN ('BOOKING_SETUP', 'BOOKING_TEARDOWN');
CREATE INDEX IF NOT EXISTS ix_cleaning_tasks_site_window ON facilities.cleaning_tasks (site_code, window_start);
CREATE INDEX IF NOT EXISTS ix_cleaning_tasks_live
    ON facilities.cleaning_tasks (site_code, due_by) WHERE status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS');
CREATE INDEX IF NOT EXISTS ix_cleaning_tasks_assigned ON facilities.cleaning_tasks (assigned_to, status);
CREATE INDEX IF NOT EXISTS ix_cleaning_tasks_vendor ON facilities.cleaning_tasks (vendor_id, requested_at);
CREATE INDEX IF NOT EXISTS ix_cleaning_tasks_booking ON facilities.cleaning_tasks (booking_id);

-- ---------------------------------------------------------------------------------------------
-- cleaning_task_checklist_items - the checklist a task carries, copied from its template.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_task_checklist_items (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)  NOT NULL,
    task_id            UUID         NOT NULL REFERENCES facilities.cleaning_tasks (id),
    item_code          VARCHAR(60)  NOT NULL,
    label              VARCHAR(300) NOT NULL,
    sequence_no        INTEGER      NOT NULL,
    photo_required     BOOLEAN      NOT NULL,
    done               BOOLEAN      NOT NULL DEFAULT FALSE,
    done_by            VARCHAR(160),
    done_at            TIMESTAMPTZ,
    photo_reference    VARCHAR(500),
    photo_content_hash VARCHAR(64),
    notes              VARCHAR(1000),
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    record_version     BIGINT       NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)  NOT NULL,
    correlation_id     VARCHAR(120),
    -- A reference with no hash cannot be verified; a hash with no reference points at nothing.
    CONSTRAINT ck_cleaning_task_items_photo_pair CHECK ((photo_reference IS NULL) = (photo_content_hash IS NULL)),
    CONSTRAINT ck_cleaning_task_items_photo_hash CHECK
        (photo_content_hash IS NULL OR photo_content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cleaning_task_items_done CHECK (NOT done OR (done_by IS NOT NULL AND done_at IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_task_items_code
    ON facilities.cleaning_task_checklist_items (task_id, item_code);

-- ---------------------------------------------------------------------------------------------
-- cleaning_feedback - occupant rating of a completed clean. One per occupant per task.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_feedback (
    id                UUID PRIMARY KEY,
    site_code         VARCHAR(40)  NOT NULL,
    task_id           UUID         NOT NULL REFERENCES facilities.cleaning_tasks (id),
    room_id           UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    vendor_id         UUID REFERENCES facilities.cleaning_vendors (id),
    rating            INTEGER      NOT NULL,
    comment           VARCHAR(1000),
    submitted_by      VARCHAR(160) NOT NULL,
    submitted_at      TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(160) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    last_modified_by  VARCHAR(160) NOT NULL,
    last_modified_at  TIMESTAMPTZ  NOT NULL,
    record_version    BIGINT       NOT NULL DEFAULT 0,
    source_channel    VARCHAR(40)  NOT NULL,
    correlation_id    VARCHAR(120),
    CONSTRAINT ck_cleaning_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_feedback_task_submitter
    ON facilities.cleaning_feedback (task_id, submitted_by);
CREATE INDEX IF NOT EXISTS ix_cleaning_feedback_room ON facilities.cleaning_feedback (room_id, submitted_at);
CREATE INDEX IF NOT EXISTS ix_cleaning_feedback_vendor ON facilities.cleaning_feedback (vendor_id, submitted_at);
CREATE INDEX IF NOT EXISTS ix_cleaning_feedback_site ON facilities.cleaning_feedback (site_code, submitted_at);

-- ---------------------------------------------------------------------------------------------
-- cleaning_low_rating_flags - repeated low ratings surfaced for supervisor review (S169-02).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_low_rating_flags (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)  NOT NULL,
    subject_type       VARCHAR(20)  NOT NULL,
    subject_id         UUID         NOT NULL,
    subject_label      VARCHAR(200) NOT NULL,
    low_rating_count   INTEGER      NOT NULL,
    window_start       TIMESTAMPTZ  NOT NULL,
    last_feedback_id   UUID         NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    flagged_at         TIMESTAMPTZ  NOT NULL,
    reviewed_by        VARCHAR(160),
    reviewed_at        TIMESTAMPTZ,
    review_notes       VARCHAR(2000),
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    record_version     BIGINT       NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)  NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ck_cleaning_flags_subject CHECK (subject_type IN ('SPACE', 'VENDOR')),
    CONSTRAINT ck_cleaning_flags_status CHECK (status IN ('OPEN', 'REVIEWED')),
    CONSTRAINT ck_cleaning_flags_reviewed CHECK
        (status <> 'REVIEWED' OR (reviewed_by IS NOT NULL AND review_notes IS NOT NULL))
);
-- One open flag per subject: a sixth low rating updates the open flag rather than raising a second.
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_flags_open
    ON facilities.cleaning_low_rating_flags (site_code, subject_type, subject_id) WHERE status = 'OPEN';

-- ---------------------------------------------------------------------------------------------
-- cleaning_sla_breaches - computed, never self-reported (S169-03).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_sla_breaches (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)  NOT NULL,
    vendor_id          UUID         NOT NULL REFERENCES facilities.cleaning_vendors (id),
    task_id            UUID         NOT NULL REFERENCES facilities.cleaning_tasks (id),
    sla_terms_id       UUID         NOT NULL REFERENCES facilities.cleaning_vendor_sla_terms (id),
    breach_type        VARCHAR(20)  NOT NULL,
    contracted_value   NUMERIC(10,2) NOT NULL,
    actual_value       NUMERIC(10,2) NOT NULL,
    basis              VARCHAR(500) NOT NULL,
    recorded_at        TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    record_version     BIGINT       NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)  NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ck_cleaning_breaches_type CHECK (breach_type IN ('RESPONSE', 'COMPLETION', 'QUALITY'))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_sla_breaches_task_type
    ON facilities.cleaning_sla_breaches (task_id, breach_type);
CREATE INDEX IF NOT EXISTS ix_cleaning_sla_breaches_vendor
    ON facilities.cleaning_sla_breaches (vendor_id, recorded_at);

-- ---------------------------------------------------------------------------------------------
-- cleaning_capacity_reservations - what S173 asked for and what it was told (S169-04).
--
-- A CONFLICT row is kept as well as a RESERVED one: "the conflict and its cause are returned" is an
-- acceptance criterion, and a conflict nobody can find afterwards cannot be reviewed.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.cleaning_capacity_reservations (
    id                     UUID PRIMARY KEY,
    site_code              VARCHAR(40)  NOT NULL,
    room_id                UUID REFERENCES facilities.facility_rooms (id),
    location_code          VARCHAR(80),
    window_from            TIMESTAMPTZ  NOT NULL,
    window_to              TIMESTAMPTZ  NOT NULL,
    scope                  VARCHAR(500),
    event_reference        VARCHAR(120) NOT NULL,
    requested_by           VARCHAR(160) NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    task_id                UUID REFERENCES facilities.cleaning_tasks (id),
    competing_task_id      UUID REFERENCES facilities.cleaning_tasks (id),
    competing_commitment   VARCHAR(600),
    competing_from         TIMESTAMPTZ,
    competing_to           TIMESTAMPTZ,
    released_at            TIMESTAMPTZ,
    release_reason         VARCHAR(1000),
    created_by             VARCHAR(160) NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    last_modified_by       VARCHAR(160) NOT NULL,
    last_modified_at       TIMESTAMPTZ  NOT NULL,
    record_version         BIGINT       NOT NULL DEFAULT 0,
    source_channel         VARCHAR(40)  NOT NULL,
    correlation_id         VARCHAR(120),
    CONSTRAINT ck_cleaning_reservations_status CHECK (status IN ('RESERVED', 'CONFLICT', 'RELEASED')),
    CONSTRAINT ck_cleaning_reservations_window CHECK (window_to > window_from),
    CONSTRAINT ck_cleaning_reservations_reserved CHECK (status <> 'RESERVED' OR task_id IS NOT NULL),
    -- The acceptance criterion as a constraint: a conflict always names its competing commitment.
    CONSTRAINT ck_cleaning_reservations_conflict CHECK
        (status <> 'CONFLICT' OR (competing_task_id IS NOT NULL AND competing_commitment IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cleaning_reservations_live_event
    ON facilities.cleaning_capacity_reservations (site_code, event_reference) WHERE status = 'RESERVED';

-- ---------------------------------------------------------------------------------------------
-- Default S169 runtime configuration. Site-scoped values override these; see CleaningConfiguration.
-- ---------------------------------------------------------------------------------------------
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version,
     updated_by, updated_at)
SELECT gen_random_uuid(), seed.config_key, NULL, seed.config_value, seed.value_type, seed.description,
       NOW(), 0, 'system', NOW()
FROM (VALUES
    ('cleaning.schedule.horizon-days', '7', 'INTEGER',
     'How far ahead the routine generation sweep materialises cleaning tasks.'),
    ('cleaning.schedule.time-zone', 'Africa/Accra', 'STRING',
     'Time zone routine schedule times of day are interpreted in.'),
    ('cleaning.booking.task-minutes', '30', 'INTEGER',
     'Length of a booking-triggered setup or teardown clean when the booking buffer is shorter.'),
    ('cleaning.reactive.target', 'PT4H', 'DURATION',
     'How long after it is raised a reactive cleaning request is due; overdue after that.'),
    ('cleaning.capacity.crews', '2', 'INTEGER',
     'Cleaning crews available at a site at any one time, for capacity offered to S173.'),
    ('cleaning.feedback.low-rating-max', '2', 'INTEGER',
     'A rating at or below this counts as low.'),
    ('cleaning.feedback.low-rating-repeat-count', '3', 'INTEGER',
     'Low ratings for the same space or vendor within the window that surface a flag for review.'),
    ('cleaning.feedback.low-rating-window', 'P30D', 'DURATION',
     'The window repeated low ratings are counted over.'),
    ('cleaning.sla.discrepancy-tolerance', 'PT5M', 'DURATION',
     'A vendor-reported completion time further than this from the recorded one is a discrepancy.'),
    ('cleaning.sweep.batch', '500', 'INTEGER', 'Rows processed per S169 sweep.')
) AS seed(config_key, config_value, value_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM facilities.facility_runtime_configuration existing
    WHERE existing.config_key = seed.config_key
      AND existing.site_code IS NULL
      AND existing.effective_to IS NULL
);

SELECT facilities.apply_site_scope_policies();
