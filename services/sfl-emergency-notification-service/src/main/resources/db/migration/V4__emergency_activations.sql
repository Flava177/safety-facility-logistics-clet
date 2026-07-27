-- S174-02 workflow: the emergency notification activation aggregate and its transition history.
CREATE TABLE emergency_notification.notification_activations (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    activation_number VARCHAR(60) NOT NULL,
    scenario_id UUID,
    template_id UUID,
    audience_group_ids TEXT,
    recipient_zone_ids TEXT,
    channels TEXT,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    incident_reference VARCHAR(120),
    approved_by VARCHAR(160),
    approved_at TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    after_action_approved_by VARCHAR(160),
    after_action_approved_at TIMESTAMPTZ,
    after_action_justification VARCHAR(1000),
    all_clear_at TIMESTAMPTZ,
    closure_reason VARCHAR(500),
    delivery_summary VARCHAR(1000),
    acknowledgement_summary VARCHAR(1000),
    closure_evidence_id UUID,
    escalation_level INTEGER NOT NULL DEFAULT 0,
    degraded_mode BOOLEAN NOT NULL DEFAULT false,
    fallback_path VARCHAR(200),
    fast_lane_millis BIGINT,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_activation_number UNIQUE (site_code, activation_number),
    CONSTRAINT ck_activation_mode CHECK (mode IN ('ROUTINE','BREAK_GLASS','DEGRADED')),
    CONSTRAINT ck_activation_escalation CHECK (escalation_level >= 0)
);
CREATE INDEX ix_activation_site_date ON emergency_notification.notification_activations(site_code, created_at DESC);
CREATE INDEX ix_activation_status ON emergency_notification.notification_activations(site_code, status, created_at);
CREATE INDEX ix_activation_scenario ON emergency_notification.notification_activations(scenario_id);

CREATE TABLE emergency_notification.activation_history (
    id UUID PRIMARY KEY,
    activation_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor VARCHAR(160) NOT NULL,
    comment VARCHAR(2000),
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(120)
);
CREATE INDEX ix_activation_history ON emergency_notification.activation_history(activation_id, occurred_at);
