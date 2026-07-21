-- =====================================================================================
-- SRS-SFL-S166-02 — Execute Fleet Workflow: the queue, its immutable history and the
-- configurable SLA rules escalation is evaluated against.
-- =====================================================================================

CREATE TABLE fleet_logistics.fleet_workflow_items (
    id UUID PRIMARY KEY,
    workflow_number VARCHAR(40) NOT NULL,
    workflow_type VARCHAR(60) NOT NULL,
    related_record_type VARCHAR(80),
    related_record_id VARCHAR(160),
    site_code VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    operating_mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    assignee VARCHAR(160),
    sla_due_at TIMESTAMPTZ,
    response_due_at TIMESTAMPTZ,
    escalation_level INTEGER NOT NULL DEFAULT 0,
    first_response_at TIMESTAMPTZ,
    status_before_hold VARCHAR(30),
    hold_reason VARCHAR(1000),
    closure_reason VARCHAR(1000),
    closure_evidence_id UUID,
    closed_at TIMESTAMPTZ,
    closed_by VARCHAR(160),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_workflow_escalation_level CHECK (escalation_level >= 0),
    -- SRS-SFL-S166-02: closure needs both a reason and evidence.
    CONSTRAINT ck_fleet_workflow_closure CHECK (status <> 'CLOSED'
        OR (closure_reason IS NOT NULL AND closure_evidence_id IS NOT NULL)),
    CONSTRAINT ck_fleet_workflow_cancellation CHECK (status <> 'CANCELLED' OR closure_reason IS NOT NULL),
    CONSTRAINT ck_fleet_workflow_hold CHECK (status <> 'ON_HOLD' OR hold_reason IS NOT NULL)
);

CREATE UNIQUE INDEX ux_fleet_workflow_site_number
    ON fleet_logistics.fleet_workflow_items (site_code, workflow_number);

CREATE INDEX ix_fleet_workflow_site_status_due
    ON fleet_logistics.fleet_workflow_items (site_code, status, sla_due_at);
CREATE INDEX ix_fleet_workflow_assignee
    ON fleet_logistics.fleet_workflow_items (assignee, status)
    WHERE assignee IS NOT NULL;
-- The escalation sweep reads only live items, so it gets its own partial index.
CREATE INDEX ix_fleet_workflow_live_due
    ON fleet_logistics.fleet_workflow_items (sla_due_at)
    WHERE status NOT IN ('CLOSED', 'CANCELLED');
CREATE INDEX ix_fleet_workflow_related_record
    ON fleet_logistics.fleet_workflow_items (related_record_type, related_record_id);

-- -------------------------------------------------------------------------------------
-- Append-only history
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.fleet_workflow_transitions (
    id UUID PRIMARY KEY,
    workflow_item_id UUID NOT NULL REFERENCES fleet_logistics.fleet_workflow_items (id) ON DELETE RESTRICT,
    sequence BIGINT NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    action VARCHAR(40) NOT NULL,
    actor_id VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(2000),
    correlation_id VARCHAR(120)
);

CREATE UNIQUE INDEX ux_fleet_workflow_transition_sequence
    ON fleet_logistics.fleet_workflow_transitions (workflow_item_id, sequence);
CREATE INDEX ix_fleet_workflow_transition_item
    ON fleet_logistics.fleet_workflow_transitions (workflow_item_id, occurred_at);

CREATE TRIGGER trg_fleet_workflow_transitions_append_only
    BEFORE UPDATE OR DELETE ON fleet_logistics.fleet_workflow_transitions
    FOR EACH ROW EXECUTE FUNCTION fleet_logistics.fleet_append_only_guard();

CREATE TABLE fleet_logistics.fleet_workflow_comments (
    id UUID PRIMARY KEY,
    workflow_item_id UUID NOT NULL REFERENCES fleet_logistics.fleet_workflow_items (id) ON DELETE RESTRICT,
    author VARCHAR(160) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(120)
);

CREATE INDEX ix_fleet_workflow_comment_item
    ON fleet_logistics.fleet_workflow_comments (workflow_item_id, occurred_at);

CREATE TRIGGER trg_fleet_workflow_comments_append_only
    BEFORE UPDATE OR DELETE ON fleet_logistics.fleet_workflow_comments
    FOR EACH ROW EXECUTE FUNCTION fleet_logistics.fleet_append_only_guard();

-- -------------------------------------------------------------------------------------
-- SLA rules. A NULL dimension means "any", so a broad default and a narrow site exception
-- can coexist; the most specific matching rule wins.
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.fleet_sla_rules (
    id UUID PRIMARY KEY,
    rule_reference VARCHAR(120) NOT NULL,
    workflow_type VARCHAR(60),
    priority VARCHAR(20),
    severity VARCHAR(20),
    site_code VARCHAR(40),
    operating_mode VARCHAR(30),
    response_minutes INTEGER NOT NULL,
    resolution_minutes INTEGER NOT NULL,
    escalation_role VARCHAR(60) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_fleet_sla_targets CHECK (resolution_minutes >= response_minutes AND response_minutes > 0),
    CONSTRAINT ck_fleet_sla_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX ux_fleet_sla_rule_reference_effective
    ON fleet_logistics.fleet_sla_rules (rule_reference)
    WHERE effective_to IS NULL;
CREATE INDEX ix_fleet_sla_effective
    ON fleet_logistics.fleet_sla_rules (effective_from, effective_to);

INSERT INTO fleet_logistics.fleet_sla_rules
    (id, rule_reference, workflow_type, priority, severity, site_code, operating_mode,
     response_minutes, resolution_minutes, escalation_role, effective_from, updated_by, updated_at)
VALUES
    -- Platform default: everything is answered within 4 hours and resolved within 24.
    (gen_random_uuid(), 'default', NULL, NULL, NULL, NULL, NULL,
     240, 1440, 'FLEET_MANAGER', now(), 'system', now()),
    -- Urgent work is answered within the hour.
    (gen_random_uuid(), 'urgent-priority', NULL, 'URGENT', NULL, NULL, NULL,
     60, 240, 'FLEET_MANAGER', now(), 'system', now()),
    -- A critical vehicle defect grounds a vehicle; it is answered in 30 minutes.
    (gen_random_uuid(), 'critical-vehicle-defect', 'VEHICLE_DEFECT', NULL, 'CRITICAL', NULL, NULL,
     30, 240, 'FLEET_MANAGER', now(), 'system', now()),
    -- Emergency operating mode overrides everything else.
    (gen_random_uuid(), 'emergency-operating-mode', NULL, NULL, NULL, NULL, 'EMERGENCY',
     15, 120, 'FLEET_MANAGER', now(), 'system', now()),
    -- Compliance renewals have a working-week horizon.
    (gen_random_uuid(), 'compliance-renewal', 'COMPLIANCE_RENEWAL', NULL, NULL, NULL, NULL,
     480, 7200, 'FLEET_LOGISTICS_OFFICER', now(), 'system', now()),
    -- An integration failure is an administrator's problem, not an officer's.
    (gen_random_uuid(), 'integration-failure', 'INTEGRATION_FAILURE', NULL, NULL, NULL, NULL,
     120, 480, 'DTI_ADMIN', now(), 'system', now());
