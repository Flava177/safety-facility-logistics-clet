-- =============================================================================================
-- S173 Event Logistics & Set-Up Workflow - SRS-SFL-S173-01..04.
--
-- Seven new tables. Nothing existing is altered. Cross-module and cross-service identifiers are held
-- by value: S159 booking and allocation ids, S153 work order ids, S169 reservation ids, S078 event
-- references and S165 assessment ids are all text columns with no foreign key. The only foreign key
-- outside this module is to facility_rooms, which S152 owns, exactly as V10 does.
--
-- Every table carries site_code NOT NULL and the last statement applies the RLS policies (CORR-06).
-- =============================================================================================

-- Set-up task references: EV-MAIN-000123. Same reasoning as the booking sequence in V10.
CREATE SEQUENCE IF NOT EXISTS facilities.event_setup_task_reference_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------------------------
-- event_setup_tasks
--
-- s078_event_reference is NOT NULL and unique: SRS-SFL-S173-01 "A set-up task cannot exist without a
-- resolvable S078 event reference", and one S078 event is one set-up task however many hand-offs
-- arrive for it. Unique across sites, not per site, because S078's references are its own and a
-- second site claiming the same event is an error to refuse, not a second task.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.event_setup_tasks (
    id                        UUID PRIMARY KEY,
    site_code                 VARCHAR(40)   NOT NULL,
    task_reference            VARCHAR(40)   NOT NULL,
    s078_event_reference      VARCHAR(120)  NOT NULL,
    title                     VARCHAR(200)  NOT NULL,
    event_category            VARCHAR(60)   NOT NULL,
    starts_at                 TIMESTAMPTZ   NOT NULL,
    ends_at                   TIMESTAMPTZ   NOT NULL,
    room_id                   UUID          NOT NULL REFERENCES facilities.facility_rooms (id),
    room_code                 VARCHAR(80)   NOT NULL,
    expected_attendance       INTEGER       NOT NULL,
    stated_requirements       VARCHAR(4000),
    external_contractors      BOOLEAN       NOT NULL,
    temporary_structures      BOOLEAN       NOT NULL,
    status                    VARCHAR(20)   NOT NULL,
    coordinator_id            VARCHAR(160),
    risk_assessment_id        VARCHAR(120),
    risk_assessment_version   INTEGER,
    risk_assessment_linked_by VARCHAR(160),
    risk_assessment_linked_at TIMESTAMPTZ,
    confirmed_by              VARCHAR(160),
    confirmed_at              TIMESTAMPTZ,
    completed_by              VARCHAR(160),
    completed_at              TIMESTAMPTZ,
    closure_reason            VARCHAR(2000),
    handoff_count             INTEGER       NOT NULL,
    last_handoff_at           TIMESTAMPTZ   NOT NULL,
    created_by                VARCHAR(160)  NOT NULL,
    created_at                TIMESTAMPTZ   NOT NULL,
    last_modified_by          VARCHAR(160)  NOT NULL,
    last_modified_at          TIMESTAMPTZ   NOT NULL,
    record_version            BIGINT        NOT NULL DEFAULT 0,
    source_channel            VARCHAR(40)   NOT NULL,
    correlation_id            VARCHAR(120),
    CONSTRAINT ck_event_setup_tasks_status CHECK (status IN ('OPEN', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_event_setup_tasks_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_event_setup_tasks_attendance CHECK (expected_attendance >= 0),
    CONSTRAINT ck_event_setup_tasks_reference CHECK (length(btrim(s078_event_reference)) > 0),
    -- A confirmation records who and when; a version travels with an assessment id.
    CONSTRAINT ck_event_setup_tasks_confirmed CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
    CONSTRAINT ck_event_setup_tasks_risk_link CHECK ((risk_assessment_id IS NULL) = (risk_assessment_version IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_event_setup_tasks_s078_reference
    ON facilities.event_setup_tasks (s078_event_reference);
CREATE UNIQUE INDEX IF NOT EXISTS ux_event_setup_tasks_reference
    ON facilities.event_setup_tasks (task_reference);
CREATE INDEX IF NOT EXISTS ix_event_setup_tasks_site_start
    ON facilities.event_setup_tasks (site_code, starts_at);
CREATE INDEX IF NOT EXISTS ix_event_setup_tasks_live_start
    ON facilities.event_setup_tasks (starts_at)
    WHERE status IN ('OPEN', 'CONFIRMED');

-- ---------------------------------------------------------------------------------------------
-- event_resource_requests
--
-- Typed lines, not free text (S173-01). MANUAL_COORDINATION is a status of its own, never FULFILLED
-- (S173-02): the CHECK below refuses an accepted-by on anything but a manual item, so "accepted" cannot
-- be smuggled onto a system request either.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.event_resource_requests (
    id                        UUID PRIMARY KEY,
    site_code                 VARCHAR(40)   NOT NULL,
    setup_task_id             UUID          NOT NULL REFERENCES facilities.event_setup_tasks (id),
    resource_type             VARCHAR(30)   NOT NULL,
    owning_system             VARCHAR(10)   NOT NULL,
    description               VARCHAR(1000) NOT NULL,
    quantity                  INTEGER       NOT NULL,
    bookable_resource_id      UUID,
    needed_from               TIMESTAMPTZ   NOT NULL,
    needed_to                 TIMESTAMPTZ   NOT NULL,
    status                    VARCHAR(30)   NOT NULL,
    external_reference        VARCHAR(120),
    external_parent_reference VARCHAR(120),
    status_detail             VARCHAR(2000),
    competing_commitment      VARCHAR(1000),
    route_attempts            INTEGER       NOT NULL DEFAULT 0,
    manual_accepted_by        VARCHAR(160),
    manual_arranged_with      VARCHAR(300),
    manual_accepted_at        TIMESTAMPTZ,
    template_line_id          UUID,
    requested_by              VARCHAR(160)  NOT NULL,
    created_by                VARCHAR(160)  NOT NULL,
    created_at                TIMESTAMPTZ   NOT NULL,
    last_modified_by          VARCHAR(160)  NOT NULL,
    last_modified_at          TIMESTAMPTZ   NOT NULL,
    record_version            BIGINT        NOT NULL DEFAULT 0,
    source_channel            VARCHAR(40)   NOT NULL,
    correlation_id            VARCHAR(120),
    CONSTRAINT ck_event_requests_type CHECK (resource_type IN ('VENUE', 'AV', 'STAGING', 'SECURITY', 'SIGNAGE',
        'PRE_EVENT_MAINTENANCE', 'CLEANING', 'CATERING')),
    CONSTRAINT ck_event_requests_owner CHECK (owning_system IN ('S159', 'S153', 'S169', 'S172')),
    CONSTRAINT ck_event_requests_status CHECK (status IN ('REQUESTED', 'CONFIRMED', 'CONFLICTED', 'FULFILLED',
        'MANUAL_COORDINATION', 'CANCELLED')),
    CONSTRAINT ck_event_requests_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_event_requests_window CHECK (needed_to > needed_from),
    CONSTRAINT ck_event_requests_manual CHECK (manual_accepted_by IS NULL OR status IN ('MANUAL_COORDINATION',
        'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS ix_event_requests_task
    ON facilities.event_resource_requests (setup_task_id);
CREATE INDEX IF NOT EXISTS ix_event_requests_external
    ON facilities.event_resource_requests (external_reference);
CREATE INDEX IF NOT EXISTS ix_event_requests_external_parent
    ON facilities.event_resource_requests (external_parent_reference);

-- ---------------------------------------------------------------------------------------------
-- event_handoffs - the hand-off register, accepted and rejected (S173-01 key object).
--
-- Accepted rows are unique per source and idempotency key, so a duplicate answers as before.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.event_handoffs (
    id                   UUID PRIMARY KEY,
    site_code            VARCHAR(40)   NOT NULL,
    s078_event_reference VARCHAR(120)  NOT NULL,
    s078_status          VARCHAR(20)   NOT NULL,
    outcome              VARCHAR(20)   NOT NULL,
    action               VARCHAR(30),
    setup_task_id        UUID,
    inbox_id             UUID,
    source_system        VARCHAR(80)   NOT NULL,
    idempotency_key      VARCHAR(200)  NOT NULL,
    rejection_detail     VARCHAR(1000),
    received_at          TIMESTAMPTZ   NOT NULL,
    created_by           VARCHAR(160)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    last_modified_by     VARCHAR(160)  NOT NULL,
    last_modified_at     TIMESTAMPTZ   NOT NULL,
    record_version       BIGINT        NOT NULL DEFAULT 0,
    source_channel       VARCHAR(40)   NOT NULL,
    correlation_id       VARCHAR(120),
    CONSTRAINT ck_event_handoffs_outcome CHECK (outcome IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_event_handoffs_action CHECK (action IS NULL OR action IN ('CREATED', 'UPDATED', 'UNCHANGED',
        'CANCELLED', 'IGNORED_TASK_CLOSED')),
    -- An accepted hand-off did something to a task; a rejection says why and created nothing.
    CONSTRAINT ck_event_handoffs_accepted CHECK (outcome <> 'ACCEPTED' OR (action IS NOT NULL AND setup_task_id IS NOT NULL)),
    CONSTRAINT ck_event_handoffs_rejected CHECK (outcome <> 'REJECTED' OR (rejection_detail IS NOT NULL
        AND setup_task_id IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_event_handoffs_accepted
    ON facilities.event_handoffs (source_system, idempotency_key)
    WHERE outcome = 'ACCEPTED';
CREATE INDEX IF NOT EXISTS ix_event_handoffs_reference
    ON facilities.event_handoffs (s078_event_reference, received_at);

-- ---------------------------------------------------------------------------------------------
-- event_readiness_escalations - S173-04. One per resource request: the evidence the completion rule
-- checks for, and the notification intent (no IFIMP provider exists; status stays RECORDED).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.event_readiness_escalations (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(40)  NOT NULL,
    setup_task_id       UUID         NOT NULL REFERENCES facilities.event_setup_tasks (id),
    resource_request_id UUID         NOT NULL REFERENCES facilities.event_resource_requests (id),
    request_status      VARCHAR(30)  NOT NULL,
    event_starts_at     TIMESTAMPTZ  NOT NULL,
    escalated_at        TIMESTAMPTZ  NOT NULL,
    window_minutes      BIGINT       NOT NULL,
    notified_to         VARCHAR(160) NOT NULL,
    notification_status VARCHAR(20)  NOT NULL,
    before_event_start  BOOLEAN      NOT NULL,
    created_by          VARCHAR(160) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    last_modified_by    VARCHAR(160) NOT NULL,
    last_modified_at    TIMESTAMPTZ  NOT NULL,
    record_version      BIGINT       NOT NULL DEFAULT 0,
    source_channel      VARCHAR(40)  NOT NULL,
    correlation_id      VARCHAR(120),
    CONSTRAINT ck_event_escalations_status CHECK (request_status IN ('REQUESTED', 'CONFLICTED', 'MANUAL_COORDINATION')),
    CONSTRAINT ck_event_escalations_notification CHECK (notification_status IN ('RECORDED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_event_escalations_request
    ON facilities.event_readiness_escalations (resource_request_id);
CREATE INDEX IF NOT EXISTS ix_event_escalations_task
    ON facilities.event_readiness_escalations (setup_task_id);

-- ---------------------------------------------------------------------------------------------
-- event_reconciliation_lines - S173-04 post-event reconciliation, one per resource request.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.event_reconciliation_lines (
    id                   UUID PRIMARY KEY,
    site_code            VARCHAR(40)   NOT NULL,
    setup_task_id        UUID          NOT NULL REFERENCES facilities.event_setup_tasks (id),
    resource_request_id  UUID          NOT NULL REFERENCES facilities.event_resource_requests (id),
    resource_type        VARCHAR(30)   NOT NULL,
    request_status       VARCHAR(30)   NOT NULL,
    requested_quantity   INTEGER       NOT NULL,
    delivered_quantity   INTEGER,
    outcome              VARCHAR(20)   NOT NULL,
    notes                VARCHAR(2000),
    recorded_by          VARCHAR(160)  NOT NULL,
    recorded_at          TIMESTAMPTZ   NOT NULL,
    created_by           VARCHAR(160)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    last_modified_by     VARCHAR(160)  NOT NULL,
    last_modified_at     TIMESTAMPTZ   NOT NULL,
    record_version       BIGINT        NOT NULL DEFAULT 0,
    source_channel       VARCHAR(40)   NOT NULL,
    correlation_id       VARCHAR(120),
    CONSTRAINT ck_event_reconciliation_outcome CHECK (outcome IN ('DELIVERED', 'PARTIAL', 'NOT_DELIVERED')),
    CONSTRAINT ck_event_reconciliation_delivered CHECK (delivered_quantity IS NULL OR delivered_quantity >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_event_reconciliation_request
    ON facilities.event_reconciliation_lines (resource_request_id);
CREATE INDEX IF NOT EXISTS ix_event_reconciliation_task
    ON facilities.event_reconciliation_lines (setup_task_id);

-- ---------------------------------------------------------------------------------------------
-- event_template_lines - lessons fed back from reconciliation gaps (S173-04).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.event_template_lines (
    id                   UUID PRIMARY KEY,
    site_code            VARCHAR(40)   NOT NULL,
    event_category       VARCHAR(60)   NOT NULL,
    resource_type        VARCHAR(30)   NOT NULL,
    description          VARCHAR(1000) NOT NULL,
    quantity             INTEGER       NOT NULL,
    bookable_resource_id UUID,
    lesson               VARCHAR(2000) NOT NULL,
    gap_count            INTEGER       NOT NULL,
    last_gap_at          TIMESTAMPTZ   NOT NULL,
    last_gap_task_id     UUID,
    created_by           VARCHAR(160)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    last_modified_by     VARCHAR(160)  NOT NULL,
    last_modified_at     TIMESTAMPTZ   NOT NULL,
    record_version       BIGINT        NOT NULL DEFAULT 0,
    source_channel       VARCHAR(40)   NOT NULL,
    correlation_id       VARCHAR(120),
    CONSTRAINT ck_event_templates_type CHECK (resource_type IN ('VENUE', 'AV', 'STAGING', 'SECURITY', 'SIGNAGE',
        'PRE_EVENT_MAINTENANCE', 'CLEANING', 'CATERING')),
    CONSTRAINT ck_event_templates_counts CHECK (gap_count >= 1 AND quantity >= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_event_templates_line
    ON facilities.event_template_lines (site_code, event_category, resource_type);

-- ---------------------------------------------------------------------------------------------
-- event_risk_assessments - S173's projection of S165 (SSEMP), S173-03.
--
-- Fed only by sfl.ssemp.risk-assessment-*.v1, which nothing publishes yet: this table is empty in
-- every current deployment, and every higher-risk confirmation is refused. Holds currency inputs
-- only; the assessment itself stays in S165.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.event_risk_assessments (
    assessment_id    VARCHAR(120) NOT NULL,
    version          INTEGER      NOT NULL,
    site_code        VARCHAR(40)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    risk_level       VARCHAR(20)  NOT NULL,
    review_due_at    TIMESTAMPTZ,
    author_id        VARCHAR(160),
    signed_off_by    VARCHAR(160),
    last_event_type  VARCHAR(120) NOT NULL,
    last_event_at    TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(160) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    record_version   BIGINT       NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)  NOT NULL,
    correlation_id   VARCHAR(120),
    PRIMARY KEY (assessment_id, version),
    CONSTRAINT ck_event_risk_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT ck_event_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- ---------------------------------------------------------------------------------------------
-- Default S173 runtime configuration. Site-scoped values override these; see
-- EventLogisticsConfiguration. S172 is seeded unavailable: it is Phase 3 (SRS 3.6).
-- ---------------------------------------------------------------------------------------------
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version,
     updated_by, updated_at)
SELECT gen_random_uuid(), seed.config_key, NULL, seed.config_value, seed.value_type, seed.description,
       NOW(), 0, 'system', NOW()
FROM (VALUES
    ('event-logistics.risk.attendance-threshold', '500', 'INTEGER',
     'Expected attendance at or above this makes an event higher-risk (S173-03).'),
    ('event-logistics.risk.external-contractors', 'true', 'BOOLEAN',
     'Events involving external contractors are higher-risk (S173-03).'),
    ('event-logistics.risk.temporary-structures', 'true', 'BOOLEAN',
     'Events with temporary structures are higher-risk (S173-03).'),
    ('event-logistics.risk.categories', 'CONCERT,EXHIBITION,GRADUATION,SPORTS', 'STRING',
     'S078 event categories that are higher-risk whatever their size (S173-03).'),
    ('event-logistics.escalation.window', 'PT48H', 'DURATION',
     'How long before an event starts an unresolved resource request escalates (S173-04).'),
    ('event-logistics.template.gap-threshold', '2', 'INTEGER',
     'Reconciliation gaps after which a template line pre-populates future decompositions (S173-04).'),
    ('event-logistics.readiness.upcoming-days', '30', 'INTEGER',
     'How far ahead the upcoming-events readiness view looks by default.'),
    ('event-logistics.sweep.batch', '200', 'INTEGER', 'Rows processed per escalation or synchronisation sweep.'),
    ('event-logistics.owning-system.S159.available', 'true', 'BOOLEAN',
     'S159 Room and Resource Booking takes event requests.'),
    ('event-logistics.owning-system.S153.available', 'true', 'BOOLEAN',
     'S153 CMMS takes pre-event maintenance requests.'),
    ('event-logistics.owning-system.S169.available', 'true', 'BOOLEAN',
     'S169 Cleaning takes event cleaning reservations.'),
    ('event-logistics.owning-system.S172.available', 'false', 'BOOLEAN',
     'S172 Catering is Phase 3 and not built; catering is recorded as manual coordination.')
) AS seed(config_key, config_value, value_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM facilities.facility_runtime_configuration existing
    WHERE existing.config_key = seed.config_key
      AND existing.site_code IS NULL
      AND existing.effective_to IS NULL
);

SELECT facilities.apply_site_scope_policies();
