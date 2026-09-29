-- =============================================================================================
-- S158 Space Planning & Move Management (SRS CLET/DTI/CL9/SFL/SRS/2026/002, SRS-SFL-S158-01..04).
--
-- Nine new tables. One of them - space_allocations - belongs to S152, not S158: the SRS makes S152
-- "the authoritative current-state register" and S158 "a scenario layer under" it, but Phase 1 S152
-- held no allocation register (a room carries only cost_centre). A committed scenario has to update
-- something, and a register owned by the planner would be the "parallel register" the SRS rules out,
-- so it is added here as S152's and written only through masterdata's SpaceAllocationService. The
-- S158 gap report records this as a Phase 1 / SRS conflict.
--
-- Every table carries site_code and is covered by facilities.apply_site_scope_policies(), called last
-- (CORR-06). Foreign keys point only at facility_rooms (S152) and at this module's own tables; the
-- S176 project is held by value (ADR 0009 - no FKs between Phase 2 modules).
-- =============================================================================================

CREATE SEQUENCE IF NOT EXISTS facilities.space_plan_reference_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS facilities.space_change_request_reference_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------------------------
-- space_allocations - the S152 current-state allocation register.
--
-- Never updated in place except to end a row: a change ends the current rows for a room and inserts
-- new ones, so history is the table itself. "Current" is ended_at IS NULL. source_scenario_id is the
-- committed S158 scenario that put the row there, by value - S152 does not depend on S158.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.space_allocations (
    id                   UUID PRIMARY KEY,
    site_code            VARCHAR(40)  NOT NULL,
    room_id              UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code            VARCHAR(80)  NOT NULL,
    allocated_unit       VARCHAR(200) NOT NULL,
    headcount            INTEGER      NOT NULL,
    allocated_since      TIMESTAMPTZ  NOT NULL,
    source_scenario_id   UUID         NOT NULL,
    source_reference     VARCHAR(80),
    ended_at             TIMESTAMPTZ,
    ended_by_scenario_id UUID,
    created_by           VARCHAR(160) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    last_modified_by     VARCHAR(160) NOT NULL,
    last_modified_at     TIMESTAMPTZ  NOT NULL,
    record_version       BIGINT       NOT NULL DEFAULT 0,
    source_channel       VARCHAR(40)  NOT NULL,
    correlation_id       VARCHAR(120),
    CONSTRAINT ck_space_allocations_headcount CHECK (headcount >= 0),
    CONSTRAINT ck_space_allocations_ended CHECK (ended_at IS NULL OR ended_at >= allocated_since)
);
-- One current row per unit per room: two would be two answers to "how many of unit X are in OFF-101".
CREATE UNIQUE INDEX IF NOT EXISTS ux_space_allocations_current
    ON facilities.space_allocations (room_id, allocated_unit) WHERE ended_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_space_allocations_site_current
    ON facilities.space_allocations (site_code, room_code) WHERE ended_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_space_allocations_source ON facilities.space_allocations (source_scenario_id);
CREATE INDEX IF NOT EXISTS ix_space_allocations_ended_by ON facilities.space_allocations (ended_by_scenario_id);

-- ---------------------------------------------------------------------------------------------
-- space_scenarios - SRS-SFL-S158-01. A "space plan" is the lineage sharing plan_reference; each row
-- is one numbered version. Committing is recorded on the row (who, when, outcome) as well as audited.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.space_scenarios (
    id                       UUID PRIMARY KEY,
    site_code                VARCHAR(40)  NOT NULL,
    plan_reference           VARCHAR(40)  NOT NULL,
    version_number           INTEGER      NOT NULL,
    based_on_scenario_id     UUID REFERENCES facilities.space_scenarios (id),
    name                     VARCHAR(200) NOT NULL,
    description              VARCHAR(4000),
    space_change_request_id  UUID,
    status                   VARCHAR(20)  NOT NULL,
    commit_outcome           VARCHAR(20),
    commit_note              VARCHAR(2000),
    committed_by             VARCHAR(160),
    committed_at             TIMESTAMPTZ,
    applied_to_register_at   TIMESTAMPTZ,
    linked_project_id        UUID,
    linked_project_reference VARCHAR(80),
    handed_over_by           VARCHAR(160),
    handed_over_at           TIMESTAMPTZ,
    discarded_by             VARCHAR(160),
    discarded_at             TIMESTAMPTZ,
    discard_reason           VARCHAR(2000),
    created_by               VARCHAR(160) NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    last_modified_by         VARCHAR(160) NOT NULL,
    last_modified_at         TIMESTAMPTZ  NOT NULL,
    record_version           BIGINT       NOT NULL DEFAULT 0,
    source_channel           VARCHAR(40)  NOT NULL,
    correlation_id           VARCHAR(120),
    CONSTRAINT ck_space_scenarios_status CHECK (status IN ('DRAFT', 'COMMITTED', 'HANDED_OVER', 'DISCARDED')),
    CONSTRAINT ck_space_scenarios_outcome CHECK (commit_outcome IS NULL
        OR commit_outcome IN ('LIKE_FOR_LIKE', 'PHYSICAL_WORKS')),
    -- A commit is a named act: a committed row without who/when/outcome is a silent overwrite.
    CONSTRAINT ck_space_scenarios_commit_named CHECK (status NOT IN ('COMMITTED', 'HANDED_OVER')
        OR (committed_by IS NOT NULL AND committed_at IS NOT NULL AND commit_outcome IS NOT NULL)),
    CONSTRAINT ck_space_scenarios_version CHECK (version_number >= 1),
    CONSTRAINT ux_space_scenarios_plan_version UNIQUE (plan_reference, version_number)
);
CREATE INDEX IF NOT EXISTS ix_space_scenarios_site_status ON facilities.space_scenarios (site_code, status);

-- A scenario's lines. allocated_unit NULL with headcount 0 is a "vacate" line.
CREATE TABLE IF NOT EXISTS facilities.space_scenario_allocations (
    id                 UUID PRIMARY KEY,
    scenario_id        UUID         NOT NULL REFERENCES facilities.space_scenarios (id),
    site_code          VARCHAR(40)  NOT NULL,
    room_id            UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code          VARCHAR(80)  NOT NULL,
    allocated_unit     VARCHAR(200),
    headcount          INTEGER      NOT NULL,
    compliance_status  VARCHAR(20)  NOT NULL,
    compliance_detail  VARCHAR(2000),
    standard_id        UUID,
    standard_version   INTEGER,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    record_version     BIGINT       NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)  NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ck_space_scenario_allocations_headcount CHECK (headcount >= 0),
    CONSTRAINT ck_space_scenario_allocations_vacate CHECK (allocated_unit IS NOT NULL OR headcount = 0),
    CONSTRAINT ck_space_scenario_allocations_compliance CHECK (compliance_status IN
        ('COMPLIANT', 'NON_COMPLIANT', 'NOT_EVALUATED'))
);
CREATE INDEX IF NOT EXISTS ix_space_scenario_allocations_scenario
    ON facilities.space_scenario_allocations (scenario_id, room_id);

-- ---------------------------------------------------------------------------------------------
-- occupancy_standards - SRS-SFL-S158-02. Versioned per site and space type; the active version is the
-- one not superseded, and there is at most one.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.occupancy_standards (
    id                       UUID PRIMARY KEY,
    site_code                VARCHAR(40)   NOT NULL,
    space_type               VARCHAR(40)   NOT NULL,
    version_number           INTEGER       NOT NULL,
    max_capacity_percent     INTEGER,
    min_area_per_person_sqm  NUMERIC(8, 2),
    note                     VARCHAR(2000),
    effective_from           TIMESTAMPTZ   NOT NULL,
    superseded_at            TIMESTAMPTZ,
    created_by               VARCHAR(160)  NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL,
    last_modified_by         VARCHAR(160)  NOT NULL,
    last_modified_at         TIMESTAMPTZ   NOT NULL,
    record_version           BIGINT        NOT NULL DEFAULT 0,
    source_channel           VARCHAR(40)   NOT NULL,
    correlation_id           VARCHAR(120),
    CONSTRAINT ck_occupancy_standards_space_type CHECK (space_type IN ('OFFICE', 'MEETING_ROOM', 'LECTURE_HALL',
        'MOOT_COURTROOM', 'EXAMINATION_HALL', 'LABORATORY', 'LIBRARY', 'AUDITORIUM', 'STORE', 'PLANT_ROOM',
        'CIRCULATION', 'SANITARY', 'RECEPTION', 'CAFETERIA', 'ACCOMMODATION', 'OTHER')),
    -- A standard that checks nothing would report compliant - "compliant by default" by another route.
    CONSTRAINT ck_occupancy_standards_criterion CHECK (max_capacity_percent IS NOT NULL
        OR min_area_per_person_sqm IS NOT NULL),
    CONSTRAINT ck_occupancy_standards_percent CHECK (max_capacity_percent IS NULL
        OR max_capacity_percent BETWEEN 1 AND 200),
    CONSTRAINT ck_occupancy_standards_area CHECK (min_area_per_person_sqm IS NULL OR min_area_per_person_sqm > 0),
    CONSTRAINT ux_occupancy_standards_version UNIQUE (site_code, space_type, version_number)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_occupancy_standards_active
    ON facilities.occupancy_standards (site_code, space_type) WHERE superseded_at IS NULL;

-- occupancy_overrides - the reason and the accountable approver (S158-02 validation).
CREATE TABLE IF NOT EXISTS facilities.occupancy_overrides (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)   NOT NULL,
    scenario_id        UUID          NOT NULL REFERENCES facilities.space_scenarios (id),
    room_id            UUID          NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code          VARCHAR(80)   NOT NULL,
    headcount_covered  INTEGER       NOT NULL,
    compliance_detail  VARCHAR(2000),
    reason             VARCHAR(2000) NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    requested_by       VARCHAR(160)  NOT NULL,
    requested_at       TIMESTAMPTZ   NOT NULL,
    approved_by        VARCHAR(160),
    approved_at        TIMESTAMPTZ,
    withdrawn_at       TIMESTAMPTZ,
    created_by         VARCHAR(160)  NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    last_modified_by   VARCHAR(160)  NOT NULL,
    last_modified_at   TIMESTAMPTZ   NOT NULL,
    record_version     BIGINT        NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)   NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ck_occupancy_overrides_status CHECK (status IN ('PENDING', 'APPROVED', 'WITHDRAWN')),
    CONSTRAINT ck_occupancy_overrides_reason CHECK (length(btrim(reason)) > 0),
    -- The database half of "an accountable approver": an approved row names somebody else.
    CONSTRAINT ck_occupancy_overrides_approver CHECK (status <> 'APPROVED'
        OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND approved_by <> requested_by))
);
CREATE INDEX IF NOT EXISTS ix_occupancy_overrides_scenario ON facilities.occupancy_overrides (scenario_id, room_id);

-- ---------------------------------------------------------------------------------------------
-- space_utilisation_snapshots and space_utilisation_signals - SRS-SFL-S158-03. The metric is defined
-- in UtilisationPolicy and the S158 runbook; the SRS does not define it.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.space_utilisation_snapshots (
    id                     UUID PRIMARY KEY,
    site_code              VARCHAR(40)  NOT NULL,
    room_id                UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code              VARCHAR(80)  NOT NULL,
    period_start           TIMESTAMPTZ  NOT NULL,
    period_end             TIMESTAMPTZ  NOT NULL,
    capacity               INTEGER,
    bookable               BOOLEAN      NOT NULL,
    booking_count          INTEGER      NOT NULL,
    taken_up_count         INTEGER      NOT NULL,
    no_show_count          INTEGER      NOT NULL,
    booked_minutes         BIGINT       NOT NULL,
    used_minutes           BIGINT       NOT NULL,
    available_minutes      BIGINT       NOT NULL,
    frequency_rate         NUMERIC(8, 4),
    occupancy_rate         NUMERIC(8, 4),
    utilisation_rate       NUMERIC(8, 4),
    planned_headcount      INTEGER      NOT NULL,
    planned_occupancy_rate NUMERIC(8, 4),
    recorded_at            TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(160) NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    last_modified_by       VARCHAR(160) NOT NULL,
    last_modified_at       TIMESTAMPTZ  NOT NULL,
    record_version         BIGINT       NOT NULL DEFAULT 0,
    source_channel         VARCHAR(40)  NOT NULL,
    correlation_id         VARCHAR(120),
    CONSTRAINT ck_space_utilisation_snapshots_period CHECK (period_end > period_start),
    -- Idempotency: a re-run of a period replaces its figures rather than adding a second row.
    CONSTRAINT ux_space_utilisation_snapshots_period UNIQUE (room_id, period_start, period_end)
);
CREATE INDEX IF NOT EXISTS ix_space_utilisation_snapshots_site_period
    ON facilities.space_utilisation_snapshots (site_code, period_end);

CREATE TABLE IF NOT EXISTS facilities.space_utilisation_signals (
    id                      UUID PRIMARY KEY,
    site_code               VARCHAR(40)  NOT NULL,
    room_id                 UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code               VARCHAR(80)  NOT NULL,
    kind                    VARCHAR(30)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    raised_at               TIMESTAMPTZ  NOT NULL,
    raised_for_period_start TIMESTAMPTZ  NOT NULL,
    raised_for_period_end   TIMESTAMPTZ  NOT NULL,
    latest_period_end       TIMESTAMPTZ  NOT NULL,
    latest_utilisation_rate NUMERIC(8, 4),
    threshold_rate          NUMERIC(8, 4),
    detail                  VARCHAR(2000),
    cleared_at              TIMESTAMPTZ,
    created_by              VARCHAR(160) NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    last_modified_by        VARCHAR(160) NOT NULL,
    last_modified_at        TIMESTAMPTZ  NOT NULL,
    record_version          BIGINT       NOT NULL DEFAULT 0,
    source_channel          VARCHAR(40)  NOT NULL,
    correlation_id          VARCHAR(120),
    CONSTRAINT ck_space_utilisation_signals_kind CHECK (kind IN ('UNDER_UTILISED', 'PLANNED_ACTUAL_GAP')),
    CONSTRAINT ck_space_utilisation_signals_status CHECK (status IN ('ACTIVE', 'CLEARED'))
);
-- One active signal per room per kind: two concurrent runs cannot raise the same signal twice.
CREATE UNIQUE INDEX IF NOT EXISTS ux_space_utilisation_signals_active
    ON facilities.space_utilisation_signals (room_id, kind) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS ix_space_utilisation_signals_site
    ON facilities.space_utilisation_signals (site_code, status);

-- ---------------------------------------------------------------------------------------------
-- space_change_requests - SRS-SFL-S158-04. linked_project_id is S176's, by value.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.space_change_requests (
    id                       UUID PRIMARY KEY,
    reference                VARCHAR(40)   NOT NULL,
    site_code                VARCHAR(40)   NOT NULL,
    requesting_unit          VARCHAR(200)  NOT NULL,
    justification            VARCHAR(4000) NOT NULL,
    target_room_id           UUID REFERENCES facilities.facility_rooms (id),
    target_room_code         VARCHAR(80),
    target_area_description  VARCHAR(2000),
    required_headcount       INTEGER,
    urgency                  VARCHAR(20)   NOT NULL,
    status                   VARCHAR(20)   NOT NULL,
    requested_by             VARCHAR(160)  NOT NULL,
    requested_at             TIMESTAMPTZ   NOT NULL,
    decided_by               VARCHAR(160),
    decided_at               TIMESTAMPTZ,
    decision_reason          VARCHAR(2000),
    outcome_type             VARCHAR(30),
    linked_scenario_id       UUID REFERENCES facilities.space_scenarios (id),
    linked_project_id        UUID,
    linked_project_reference VARCHAR(80),
    resolved_by              VARCHAR(160),
    resolved_at              TIMESTAMPTZ,
    resolution_note          VARCHAR(2000),
    created_by               VARCHAR(160)  NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL,
    last_modified_by         VARCHAR(160)  NOT NULL,
    last_modified_at         TIMESTAMPTZ   NOT NULL,
    record_version           BIGINT        NOT NULL DEFAULT 0,
    source_channel           VARCHAR(40)   NOT NULL,
    correlation_id           VARCHAR(120),
    CONSTRAINT ck_space_change_requests_urgency CHECK (urgency IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_space_change_requests_status CHECK (status IN
        ('SUBMITTED', 'APPROVED', 'DECLINED', 'ACTIONED', 'RESOLVED')),
    CONSTRAINT ck_space_change_requests_outcome CHECK (outcome_type IS NULL
        OR outcome_type IN ('SCENARIO', 'CONSTRUCTION_PROJECT')),
    CONSTRAINT ck_space_change_requests_target CHECK (target_room_id IS NOT NULL
        OR target_area_description IS NOT NULL),
    -- "Unlinked Resolution" held by the database as well as the domain.
    CONSTRAINT ck_space_change_requests_resolution_linked CHECK (status <> 'RESOLVED'
        OR linked_scenario_id IS NOT NULL OR linked_project_id IS NOT NULL),
    CONSTRAINT ck_space_change_requests_decider CHECK (decided_by IS NULL OR decided_by <> requested_by),
    CONSTRAINT ux_space_change_requests_reference UNIQUE (reference)
);
CREATE INDEX IF NOT EXISTS ix_space_change_requests_site_status
    ON facilities.space_change_requests (site_code, status);
CREATE INDEX IF NOT EXISTS ix_space_change_requests_requester
    ON facilities.space_change_requests (requested_by);

-- ---------------------------------------------------------------------------------------------
-- Runtime configuration defaults (Configuration Without Code). Site-scoped rows override these.
-- ---------------------------------------------------------------------------------------------
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version,
     updated_by, updated_at)
SELECT gen_random_uuid(), seed.config_key, NULL, seed.config_value, seed.value_type, seed.description,
       NOW(), 0, 'system', NOW()
FROM (VALUES
    ('space-planning.utilisation.under-threshold-percent', '20', 'INTEGER',
     'A space whose utilisation rate over a complete period is under this is on the under-utilisation list (S158-03).'),
    ('space-planning.utilisation.period-days', '7', 'INTEGER',
     'Length of a utilisation reporting period in days, aligned to Monday 2024-01-01 UTC.'),
    ('space-planning.utilisation.available-hours-per-day', '10', 'INTEGER',
     'Bookable hours per available day - the denominator of the frequency rate.'),
    ('space-planning.utilisation.available-days', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 'STRING',
     'Days of the week that count towards available hours.'),
    ('space-planning.signal.gap-threshold-percent', '30', 'INTEGER',
     'Planned occupancy exceeding observed utilisation by at least this counts as a gap period.'),
    ('space-planning.signal.persistence-periods', '2', 'INTEGER',
     'Consecutive gap periods before a planned-versus-actual gap is surfaced as a planning signal.')
) AS seed(config_key, config_value, value_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM facilities.facility_runtime_configuration existing
    WHERE existing.config_key = seed.config_key
      AND existing.site_code IS NULL
      AND existing.effective_to IS NULL
);

SELECT facilities.apply_site_scope_policies();
