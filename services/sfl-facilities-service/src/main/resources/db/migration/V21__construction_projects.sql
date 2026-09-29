-- =============================================================================================
-- S176 Construction Project Management (SRS CLET/DTI/CL9/SFL/SRS/2026/002, SRS-SFL-S176-01..04).
--
-- Fourteen new tables, nothing existing altered. Every table carries site_code NOT NULL and is put
-- under the fail-closed site_scope_read policy by the last statement in this file (CORR-06).
--
-- The rules that matter most are stated twice on purpose - once in the domain, where a caller gets
-- a readable refusal, and once here, where no code path can get round them:
--
--   * a project cannot be in or past IN_PROGRESS without an approval record        (S176-01)
--   * an escalated variation cannot be APPROVED without an escalated approver       (S176-03)
--   * nobody approves a variation they submitted                                   (S176-03)
--   * a COMPLETE handover must have applied at least one S152 register change      (S176-04)
--   * milestone and baseline revisions are append-only                              (S176-01)
--
-- Cross-module references are held by value: the S158 space-change request and scenario, the S153
-- work order a defect raised, the S164 permit, the S133 vendor and the S223 funding source. The only
-- foreign keys leaving this module point at S152's facility_rooms, which is the register a handover
-- writes into.
-- =============================================================================================

CREATE SEQUENCE IF NOT EXISTS facilities.construction_project_reference_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS facilities.construction_variation_reference_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS facilities.construction_defect_reference_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------------------------
-- construction_projects - SRS-SFL-S176-01.
--
-- work_types and affected_room_ids are comma-separated values rather than child tables. Both are
-- small, both are read whole with the project, and neither is ever queried by element; a child table
-- would need its own site_code and policy for no reader's benefit.
--
-- A PROPOSED project (the S158 intake) has no budget, funding or work types yet - that is what
-- registering it defines - so those columns are nullable and ck_construction_projects_defined
-- requires them for every state past PROPOSED except CANCELLED.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_projects (
    id                         UUID PRIMARY KEY,
    project_reference          VARCHAR(40)   NOT NULL,
    site_code                  VARCHAR(40)   NOT NULL,
    title                      VARCHAR(200)  NOT NULL,
    scope                      VARCHAR(4000) NOT NULL,
    work_types                 VARCHAR(400),
    budget_baseline            NUMERIC(18, 2),
    currency                   VARCHAR(3),
    baseline_revision          INTEGER       NOT NULL DEFAULT 0,
    funding_source_reference   VARCHAR(120),
    funding_source_name        VARCHAR(200),
    project_manager_id         VARCHAR(160),
    status                     VARCHAR(30)   NOT NULL,
    origin                     VARCHAR(30)   NOT NULL,
    space_change_request_id    UUID,
    committed_scenario_id      UUID,
    affected_room_ids          VARCHAR(4000),
    requesting_unit            VARCHAR(200),
    justification              VARCHAR(4000),
    approval_id                UUID,
    started_at                 TIMESTAMPTZ,
    practical_completion_at    TIMESTAMPTZ,
    handed_over_at             TIMESTAMPTZ,
    defects_liability_ends_on  DATE,
    closed_at                  TIMESTAMPTZ,
    cancellation_reason        VARCHAR(2000),
    registered_by              VARCHAR(160)  NOT NULL,
    registered_at              TIMESTAMPTZ   NOT NULL,
    created_by                 VARCHAR(160)  NOT NULL,
    created_at                 TIMESTAMPTZ   NOT NULL,
    last_modified_by           VARCHAR(160)  NOT NULL,
    last_modified_at           TIMESTAMPTZ   NOT NULL,
    record_version             BIGINT        NOT NULL DEFAULT 0,
    source_channel             VARCHAR(40)   NOT NULL,
    correlation_id             VARCHAR(120),
    CONSTRAINT ux_construction_projects_reference UNIQUE (project_reference),
    CONSTRAINT ck_construction_projects_status CHECK (status IN ('PROPOSED', 'REGISTERED', 'APPROVED',
        'IN_PROGRESS', 'PRACTICAL_COMPLETION', 'HANDED_OVER', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_construction_projects_origin CHECK (origin IN ('DIRECT', 'S158_SPACE_CHANGE')),
    CONSTRAINT ck_construction_projects_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_construction_projects_baseline CHECK (budget_baseline IS NULL OR budget_baseline > 0),
    CONSTRAINT ck_construction_projects_defined CHECK (status IN ('PROPOSED', 'CANCELLED')
        OR (budget_baseline IS NOT NULL AND currency IS NOT NULL AND funding_source_reference IS NOT NULL
            AND work_types IS NOT NULL AND project_manager_id IS NOT NULL)),
    -- The S176-01 approval gate, as the database sees it. The service refuses the start with
    -- PROJECT_APPROVAL_MISSING first; this is what holds if a future code path forgets to ask.
    CONSTRAINT ck_construction_projects_approved_before_start CHECK (
        status NOT IN ('IN_PROGRESS', 'PRACTICAL_COMPLETION', 'HANDED_OVER', 'CLOSED') OR approval_id IS NOT NULL),
    CONSTRAINT ck_construction_projects_cancel_reason CHECK (status <> 'CANCELLED' OR cancellation_reason IS NOT NULL)
);

-- One S176 project per approved S158 space-change request: the intake is idempotent on it.
CREATE UNIQUE INDEX IF NOT EXISTS ux_construction_projects_space_change
    ON facilities.construction_projects (space_change_request_id)
    WHERE space_change_request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_construction_projects_site_status
    ON facilities.construction_projects (site_code, status);

-- ---------------------------------------------------------------------------------------------
-- construction_project_approvals - the accountable approver's sign-off, as its own record.
--
-- A record rather than a status for the same reason booking_approvals is one: "approved" is a fact
-- about who signed and what they signed. baseline_revision is what they signed - a baseline revised
-- afterwards is not covered by it, and the project returns to REGISTERED for a fresh sign-off.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_project_approvals (
    id                UUID PRIMARY KEY,
    project_id        UUID           NOT NULL REFERENCES facilities.construction_projects (id),
    site_code         VARCHAR(40)    NOT NULL,
    approver_id       VARCHAR(160)   NOT NULL,
    baseline_revision INTEGER        NOT NULL,
    baseline_amount   NUMERIC(18, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    note              VARCHAR(2000),
    approved_at       TIMESTAMPTZ    NOT NULL,
    created_by        VARCHAR(160)   NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    last_modified_by  VARCHAR(160)   NOT NULL,
    last_modified_at  TIMESTAMPTZ    NOT NULL,
    record_version    BIGINT         NOT NULL DEFAULT 0,
    source_channel    VARCHAR(40)    NOT NULL,
    correlation_id    VARCHAR(120)
);
CREATE INDEX IF NOT EXISTS ix_construction_project_approvals_project
    ON facilities.construction_project_approvals (project_id, approved_at);

-- ---------------------------------------------------------------------------------------------
-- construction_milestones - the current target of each milestone. Its history is below.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_milestones (
    id               UUID PRIMARY KEY,
    project_id       UUID         NOT NULL REFERENCES facilities.construction_projects (id),
    site_code        VARCHAR(40)  NOT NULL,
    milestone_code   VARCHAR(40)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    target_date      DATE         NOT NULL,
    revision         INTEGER      NOT NULL,
    achieved_on      DATE,
    created_by       VARCHAR(160) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    record_version   BIGINT       NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)  NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ux_construction_milestones_code UNIQUE (project_id, milestone_code),
    CONSTRAINT ck_construction_milestones_revision CHECK (revision >= 1)
);

-- ---------------------------------------------------------------------------------------------
-- construction_project_revisions - SRS-SFL-S176-01 "milestone dates and budget revisions are
-- versioned, not overwritten".
--
-- Every baseline and every milestone target ever set, one row each, revision 1 being the original.
-- Append-only, and the trigger below makes that a property of the table rather than a habit of the
-- adapter: an UPDATE or DELETE is refused whoever issues it, sfl_app or the owner.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_project_revisions (
    id               UUID PRIMARY KEY,
    project_id       UUID           NOT NULL REFERENCES facilities.construction_projects (id),
    site_code        VARCHAR(40)    NOT NULL,
    subject          VARCHAR(30)    NOT NULL,
    subject_id       UUID,
    subject_code     VARCHAR(40)    NOT NULL,
    revision         INTEGER        NOT NULL,
    amount           NUMERIC(18, 2),
    currency         VARCHAR(3),
    target_date      DATE,
    reason           VARCHAR(2000),
    revised_by       VARCHAR(160)   NOT NULL,
    revised_at       TIMESTAMPTZ    NOT NULL,
    created_by       VARCHAR(160)   NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    last_modified_by VARCHAR(160)   NOT NULL,
    last_modified_at TIMESTAMPTZ    NOT NULL,
    record_version   BIGINT         NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)    NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ux_construction_project_revisions UNIQUE (project_id, subject, subject_code, revision),
    CONSTRAINT ck_construction_project_revisions_subject CHECK (subject IN ('BUDGET_BASELINE', 'MILESTONE_TARGET')),
    CONSTRAINT ck_construction_project_revisions_baseline CHECK (
        subject <> 'BUDGET_BASELINE' OR (amount IS NOT NULL AND currency IS NOT NULL)),
    CONSTRAINT ck_construction_project_revisions_milestone CHECK (
        subject <> 'MILESTONE_TARGET' OR (target_date IS NOT NULL AND subject_id IS NOT NULL)),
    -- Revision 1 is the original; every later one says why it moved.
    CONSTRAINT ck_construction_project_revisions_reason CHECK (revision = 1 OR reason IS NOT NULL)
);

CREATE OR REPLACE FUNCTION facilities.construction_revisions_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'construction_project_revisions is append-only (SRS-SFL-S176-01): revisions are versioned, never overwritten';
END
$$;

DROP TRIGGER IF EXISTS tr_construction_revisions_append_only ON facilities.construction_project_revisions;
CREATE TRIGGER tr_construction_revisions_append_only
    BEFORE UPDATE OR DELETE ON facilities.construction_project_revisions
    FOR EACH ROW EXECUTE FUNCTION facilities.construction_revisions_append_only();

-- ---------------------------------------------------------------------------------------------
-- construction_contractors and their competencies - SRS-SFL-S176-02.
--
-- Registered per site, because the site's HSE unit is who verifies them and RLS scopes by site. A
-- contractor working at two sites is two rows until the Vendor Master (S133) is integrated and
-- vendor_reference can tie them together - recorded in the S176 gap report.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_contractors (
    id                         UUID PRIMARY KEY,
    site_code                  VARCHAR(40)  NOT NULL,
    contractor_code            VARCHAR(40)  NOT NULL,
    name                       VARCHAR(200) NOT NULL,
    vendor_reference           VARCHAR(120),
    insurance_provider         VARCHAR(200),
    insurance_policy_reference VARCHAR(120),
    insurance_expires_on       DATE         NOT NULL,
    lifecycle_status           VARCHAR(20)  NOT NULL,
    created_by                 VARCHAR(160) NOT NULL,
    created_at                 TIMESTAMPTZ  NOT NULL,
    last_modified_by           VARCHAR(160) NOT NULL,
    last_modified_at           TIMESTAMPTZ  NOT NULL,
    record_version             BIGINT       NOT NULL DEFAULT 0,
    source_channel             VARCHAR(40)  NOT NULL,
    correlation_id             VARCHAR(120),
    CONSTRAINT ck_construction_contractors_lifecycle CHECK (
        lifecycle_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_construction_contractors_code
    ON facilities.construction_contractors (site_code, contractor_code)
    WHERE lifecycle_status <> 'ARCHIVED';

CREATE TABLE IF NOT EXISTS facilities.construction_contractor_competencies (
    id                    UUID PRIMARY KEY,
    contractor_id         UUID         NOT NULL REFERENCES facilities.construction_contractors (id),
    site_code             VARCHAR(40)  NOT NULL,
    certification_code    VARCHAR(60)  NOT NULL,
    description           VARCHAR(500),
    certificate_reference VARCHAR(120),
    expires_on            DATE         NOT NULL,
    created_by            VARCHAR(160) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    last_modified_by      VARCHAR(160) NOT NULL,
    last_modified_at      TIMESTAMPTZ  NOT NULL,
    record_version        BIGINT       NOT NULL DEFAULT 0,
    source_channel        VARCHAR(40)  NOT NULL,
    correlation_id        VARCHAR(120),
    CONSTRAINT ux_construction_competencies_code UNIQUE (contractor_id, certification_code)
);

CREATE TABLE IF NOT EXISTS facilities.construction_project_contractors (
    id               UUID PRIMARY KEY,
    project_id       UUID         NOT NULL REFERENCES facilities.construction_projects (id),
    contractor_id    UUID         NOT NULL REFERENCES facilities.construction_contractors (id),
    site_code        VARCHAR(40)  NOT NULL,
    role             VARCHAR(30)  NOT NULL,
    created_by       VARCHAR(160) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    record_version   BIGINT       NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)  NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ux_construction_project_contractors UNIQUE (project_id, contractor_id),
    CONSTRAINT ck_construction_project_contractors_role CHECK (
        role IN ('MAIN_CONTRACTOR', 'SUBCONTRACTOR', 'CONSULTANT'))
);

-- ---------------------------------------------------------------------------------------------
-- construction_site_access_grants - the local record of a contractor site-access request.
--
-- Access Control (S160a) and Visitor Management (S160) live in SSEMP and nothing there consumes the
-- request yet. enforcement says so on every row: RECORDED_NOT_ENFORCED means this grant, and any
-- suspension of it, exists here and in the outbox and has reached no door.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_site_access_grants (
    id                UUID PRIMARY KEY,
    contractor_id     UUID          NOT NULL REFERENCES facilities.construction_contractors (id),
    project_id        UUID REFERENCES facilities.construction_projects (id),
    site_code         VARCHAR(40)   NOT NULL,
    access_scope      VARCHAR(500)  NOT NULL,
    valid_from        TIMESTAMPTZ   NOT NULL,
    valid_to          TIMESTAMPTZ   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    refusal_reason    VARCHAR(2000),
    suspended_at      TIMESTAMPTZ,
    suspension_reason VARCHAR(2000),
    enforcement       VARCHAR(40)   NOT NULL,
    dispatch_provider VARCHAR(80),
    requested_by      VARCHAR(160)  NOT NULL,
    requested_at      TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(160)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    last_modified_by  VARCHAR(160)  NOT NULL,
    last_modified_at  TIMESTAMPTZ   NOT NULL,
    record_version    BIGINT        NOT NULL DEFAULT 0,
    source_channel    VARCHAR(40)   NOT NULL,
    correlation_id    VARCHAR(120),
    CONSTRAINT ck_construction_access_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REFUSED')),
    CONSTRAINT ck_construction_access_enforcement CHECK (enforcement IN ('RECORDED_NOT_ENFORCED', 'ENFORCED')),
    CONSTRAINT ck_construction_access_window CHECK (valid_to > valid_from),
    -- "Refused/suspended with reason shown" (S176-02): a refusal or suspension with no reason is not one.
    CONSTRAINT ck_construction_access_refusal CHECK (status <> 'REFUSED' OR refusal_reason IS NOT NULL),
    CONSTRAINT ck_construction_access_suspension CHECK (
        status <> 'SUSPENDED' OR (suspension_reason IS NOT NULL AND suspended_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_construction_access_active
    ON facilities.construction_site_access_grants (contractor_id)
    WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------------------------------
-- construction_permit_projection - what S164 (SSEMP) has said about each permit.
--
-- Fed only by the sfl.ssemp.permit-*.v1 events. S164 is not built and nothing publishes them, so in
-- this release the table is empty and every project whose work type requires a permit is refused
-- the start - which is the fail-closed outcome S176-01 wants, not a defect. validity and work type
-- are nullable because a suspension can arrive before the issue it refers to.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_permit_projection (
    id                   UUID PRIMARY KEY,
    permit_id            VARCHAR(80)   NOT NULL,
    permit_reference     VARCHAR(80),
    site_code            VARCHAR(40)   NOT NULL,
    work_type            VARCHAR(60),
    status               VARCHAR(20)   NOT NULL,
    valid_from           TIMESTAMPTZ,
    valid_to             TIMESTAMPTZ,
    contractor_reference VARCHAR(120),
    origin_reference     VARCHAR(120),
    last_event_type      VARCHAR(120)  NOT NULL,
    last_event_at        TIMESTAMPTZ   NOT NULL,
    last_message_id      UUID,
    created_by           VARCHAR(160)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    last_modified_by     VARCHAR(160)  NOT NULL,
    last_modified_at     TIMESTAMPTZ   NOT NULL,
    record_version       BIGINT        NOT NULL DEFAULT 0,
    source_channel       VARCHAR(40)   NOT NULL,
    correlation_id       VARCHAR(120),
    CONSTRAINT ux_construction_permit_projection UNIQUE (permit_id),
    CONSTRAINT ck_construction_permit_status CHECK (status IN ('ISSUED', 'SUSPENDED', 'CLOSED'))
);

-- A project manager's claim that a permit covers this project's work. No foreign key to the
-- projection: the link may be recorded before S164's event arrives, and is simply not current until
-- it does.
CREATE TABLE IF NOT EXISTS facilities.construction_project_permits (
    id               UUID PRIMARY KEY,
    project_id       UUID         NOT NULL REFERENCES facilities.construction_projects (id),
    site_code        VARCHAR(40)  NOT NULL,
    permit_id        VARCHAR(80)  NOT NULL,
    work_type        VARCHAR(60)  NOT NULL,
    created_by       VARCHAR(160) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    record_version   BIGINT       NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)  NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ux_construction_project_permits UNIQUE (project_id, permit_id)
);

-- ---------------------------------------------------------------------------------------------
-- construction_variations - SRS-SFL-S176-03.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_variations (
    id                     UUID PRIMARY KEY,
    variation_reference    VARCHAR(40)    NOT NULL,
    project_id             UUID           NOT NULL REFERENCES facilities.construction_projects (id),
    site_code              VARCHAR(40)    NOT NULL,
    change_description     VARCHAR(4000)  NOT NULL,
    cost_delta             NUMERIC(18, 2) NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    justification          VARCHAR(4000)  NOT NULL,
    status                 VARCHAR(20)    NOT NULL,
    escalation_required    BOOLEAN        NOT NULL DEFAULT FALSE,
    escalation_reason      VARCHAR(1000),
    submitted_by           VARCHAR(160)   NOT NULL,
    submitted_at           TIMESTAMPTZ    NOT NULL,
    decided_by             VARCHAR(160),
    decided_at             TIMESTAMPTZ,
    decision_note          VARCHAR(2000),
    escalated_approved_by  VARCHAR(160),
    escalated_approved_at  TIMESTAMPTZ,
    cumulative_percent     NUMERIC(9, 4),
    created_by             VARCHAR(160)   NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL,
    last_modified_by       VARCHAR(160)   NOT NULL,
    last_modified_at       TIMESTAMPTZ    NOT NULL,
    record_version         BIGINT         NOT NULL DEFAULT 0,
    source_channel         VARCHAR(40)    NOT NULL,
    correlation_id         VARCHAR(120),
    CONSTRAINT ux_construction_variations_reference UNIQUE (variation_reference),
    CONSTRAINT ck_construction_variations_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_construction_variations_delta CHECK (cost_delta <> 0),
    CONSTRAINT ck_construction_variations_decided CHECK (status = 'SUBMITTED' OR decided_by IS NOT NULL),
    CONSTRAINT ck_construction_variations_rejection CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL),
    -- The escalation rule and the separation of duties, where no code path can skip them.
    CONSTRAINT ck_construction_variations_escalated CHECK (
        status <> 'APPROVED' OR NOT escalation_required OR escalated_approved_by IS NOT NULL),
    CONSTRAINT ck_construction_variations_not_self_decided CHECK (
        decided_by IS NULL OR decided_by <> submitted_by),
    CONSTRAINT ck_construction_variations_not_self_escalated CHECK (
        escalated_approved_by IS NULL OR escalated_approved_by <> submitted_by)
);
CREATE INDEX IF NOT EXISTS ix_construction_variations_project
    ON facilities.construction_variations (project_id, status);

-- ---------------------------------------------------------------------------------------------
-- construction_handovers and the S152 register changes each one applied - SRS-SFL-S176-04.
--
-- An INCOMPLETE row is kept on purpose: "flagged as incomplete, not silently accepted as done" needs
-- the flag to exist somewhere a dashboard can count it.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_handovers (
    id                           UUID PRIMARY KEY,
    project_id                   UUID          NOT NULL REFERENCES facilities.construction_projects (id),
    site_code                    VARCHAR(40)   NOT NULL,
    outcome                      VARCHAR(20)   NOT NULL,
    incomplete_reason            VARCHAR(2000),
    handover_date                DATE          NOT NULL,
    notes                        VARCHAR(4000),
    register_change_count        INTEGER       NOT NULL DEFAULT 0,
    scenario_id                  UUID,
    scenario_confirmation        VARCHAR(30)   NOT NULL,
    scenario_confirmation_detail VARCHAR(1000),
    recorded_by                  VARCHAR(160)  NOT NULL,
    recorded_at                  TIMESTAMPTZ   NOT NULL,
    created_by                   VARCHAR(160)  NOT NULL,
    created_at                   TIMESTAMPTZ   NOT NULL,
    last_modified_by             VARCHAR(160)  NOT NULL,
    last_modified_at             TIMESTAMPTZ   NOT NULL,
    record_version               BIGINT        NOT NULL DEFAULT 0,
    source_channel               VARCHAR(40)   NOT NULL,
    correlation_id               VARCHAR(120),
    CONSTRAINT ck_construction_handovers_outcome CHECK (outcome IN ('COMPLETE', 'INCOMPLETE')),
    CONSTRAINT ck_construction_handovers_scenario CHECK (
        scenario_confirmation IN ('NOT_APPLICABLE', 'CONFIRMED', 'UNRESOLVED')),
    CONSTRAINT ck_construction_handovers_register CHECK (outcome <> 'COMPLETE' OR register_change_count > 0),
    CONSTRAINT ck_construction_handovers_reason CHECK (outcome <> 'INCOMPLETE' OR incomplete_reason IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_construction_handovers_project
    ON facilities.construction_handovers (project_id, recorded_at);

CREATE TABLE IF NOT EXISTS facilities.construction_handover_register_changes (
    id               UUID PRIMARY KEY,
    handover_id      UUID         NOT NULL REFERENCES facilities.construction_handovers (id),
    project_id       UUID         NOT NULL REFERENCES facilities.construction_projects (id),
    site_code        VARCHAR(40)  NOT NULL,
    action           VARCHAR(20)  NOT NULL,
    room_id          UUID         NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code        VARCHAR(80)  NOT NULL,
    room_version     BIGINT       NOT NULL,
    created_by       VARCHAR(160) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    record_version   BIGINT       NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)  NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ck_construction_register_change_action CHECK (action IN ('CREATED', 'UPDATED'))
);

-- ---------------------------------------------------------------------------------------------
-- construction_defects - defects-liability items, each raised as a tagged S153 work order.
--
-- work_order_id is S153's id held by value: S153's tables belong to another module, and this
-- module's reading of the work order's state goes through AutomatedWorkOrderIntake.find.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.construction_defects (
    id                UUID PRIMARY KEY,
    defect_reference  VARCHAR(40)   NOT NULL,
    project_id        UUID          NOT NULL REFERENCES facilities.construction_projects (id),
    contractor_id     UUID          NOT NULL REFERENCES facilities.construction_contractors (id),
    site_code         VARCHAR(40)   NOT NULL,
    description       VARCHAR(4000) NOT NULL,
    room_id           UUID REFERENCES facilities.facility_rooms (id),
    location_code     VARCHAR(80),
    priority          VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    work_order_id     UUID,
    work_order_number VARCHAR(40),
    fault_number      VARCHAR(40),
    work_order_status VARCHAR(30),
    deferral_reason   VARCHAR(2000),
    resolved_by       VARCHAR(160),
    resolved_at       TIMESTAMPTZ,
    raised_by         VARCHAR(160)  NOT NULL,
    raised_at         TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(160)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    last_modified_by  VARCHAR(160)  NOT NULL,
    last_modified_at  TIMESTAMPTZ   NOT NULL,
    record_version    BIGINT        NOT NULL DEFAULT 0,
    source_channel    VARCHAR(40)   NOT NULL,
    correlation_id    VARCHAR(120),
    CONSTRAINT ux_construction_defects_reference UNIQUE (defect_reference),
    CONSTRAINT ck_construction_defects_status CHECK (status IN ('OPEN', 'CLOSED', 'DEFERRED')),
    CONSTRAINT ck_construction_defects_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    -- "Explicitly deferred with reason" (S176-04): a deferral without one is not explicit.
    CONSTRAINT ck_construction_defects_deferral CHECK (status <> 'DEFERRED' OR deferral_reason IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_construction_defects_open
    ON facilities.construction_defects (project_id)
    WHERE status = 'OPEN';

-- ---------------------------------------------------------------------------------------------
-- Runtime configuration (Configuration Without Code). Read at evaluation time by
-- ConstructionConfiguration, which falls back to these same values when a row is absent.
-- ---------------------------------------------------------------------------------------------
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version,
     updated_by, updated_at)
SELECT gen_random_uuid(), seed.config_key, NULL, seed.config_value, seed.value_type, seed.description,
       NOW(), 0, 'system', NOW()
FROM (VALUES
    ('construction.work-types',
     'GENERAL_BUILDING,FIT_OUT,REFURBISHMENT,DEMOLITION,MECHANICAL,PLUMBING,ROOFING,HOT_WORK,WORK_AT_HEIGHT,CONFINED_SPACE,ELECTRICAL_ISOLATION,EXCAVATION',
     'STRING', 'The work types a construction project may declare. An unknown type is refused, so a typo cannot evade the permit rule.'),
    ('construction.permit-required.work-types',
     'HOT_WORK,WORK_AT_HEIGHT,CONFINED_SPACE,ELECTRICAL_ISOLATION,EXCAVATION',
     'STRING', 'Work types that need a current linked S164 permit before a project may start (SRS-SFL-S176-01).'),
    ('construction.variation.escalation-threshold-percent', '10', 'INTEGER',
     'Cumulative approved variations above this percentage of the approved baseline need escalated approval (SRS-SFL-S176-03).'),
    ('construction.defects-liability.period-days', '365', 'INTEGER',
     'Length of the defects-liability period, counted from handover (SRS-SFL-S176-04).'),
    ('construction.compliance.expiry-warning-days', '30', 'INTEGER',
     'Contractor insurance or competency expiring within this many days is shown as a dashboard warning.'),
    ('construction.sweep.batch', '200', 'INTEGER',
     'Rows processed per compliance-suspension or defect-sync sweep.')
) AS seed(config_key, config_value, value_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM facilities.facility_runtime_configuration existing
    WHERE existing.config_key = seed.config_key
      AND existing.site_code IS NULL
      AND existing.effective_to IS NULL
);

SELECT facilities.apply_site_scope_policies();
