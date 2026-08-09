-- S174-03/05 governed evidence references, dashboard read-model snapshots and drill runs.
CREATE TABLE emergency_notification.evidence_references (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    related_activation_id UUID,
    evidence_type VARCHAR(60) NOT NULL,
    file_name VARCHAR(300) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    storage_reference VARCHAR(500) NOT NULL,
    sha256_hash VARCHAR(80) NOT NULL,
    retention_class VARCHAR(40) NOT NULL,
    legal_hold BOOLEAN NOT NULL DEFAULT false,
    uploaded_by VARCHAR(160) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    CONSTRAINT ck_evidence_retention CHECK (retention_class IN
        ('OPERATIONAL_1_YEAR','COMPLIANCE_7_YEARS','INCIDENT_10_YEARS','LEGAL_HOLD'))
);
CREATE INDEX ix_evidence_activation ON emergency_notification.evidence_references(related_activation_id);

CREATE TABLE emergency_notification.dashboard_snapshots (
    id UUID PRIMARY KEY,
    scope_key VARCHAR(120) NOT NULL,
    site_code VARCHAR(40),
    generated_at TIMESTAMPTZ NOT NULL,
    stale BOOLEAN NOT NULL,
    active_activation_count INTEGER NOT NULL,
    break_glass_count INTEGER NOT NULL,
    failed_recipient_count INTEGER NOT NULL,
    ack_pending_count INTEGER NOT NULL,
    escalated_count INTEGER NOT NULL,
    all_clear_pending_count INTEGER NOT NULL,
    drill_count INTEGER NOT NULL,
    source_updated_at TIMESTAMPTZ,
    warnings TEXT
);
CREATE INDEX ix_dashboard_scope ON emergency_notification.dashboard_snapshots(scope_key, generated_at DESC);

CREATE TABLE emergency_notification.drill_runs (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    drill_number VARCHAR(60) NOT NULL,
    scenario_id UUID,
    status VARCHAR(20) NOT NULL,
    target_recipients INTEGER NOT NULL DEFAULT 0,
    reached_recipients INTEGER NOT NULL DEFAULT 0,
    acknowledged_recipients INTEGER NOT NULL DEFAULT 0,
    activation_millis BIGINT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_drill_number UNIQUE (site_code, drill_number),
    CONSTRAINT ck_drill_status CHECK (status IN ('RUNNING','COMPLETED','CANCELLED'))
);
CREATE INDEX ix_drill_site ON emergency_notification.drill_runs(site_code, started_at DESC);
