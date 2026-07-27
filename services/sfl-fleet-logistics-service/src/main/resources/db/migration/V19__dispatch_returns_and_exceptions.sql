-- S171 return-leg reconciliation and the accountable dispatch exception/case workflow.
CREATE TABLE fleet_logistics.return_reconciliations (
    id UUID PRIMARY KEY,
    dispatch_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    expected_count INTEGER NOT NULL,
    returned_count INTEGER NOT NULL,
    shortfall INTEGER NOT NULL,
    extras INTEGER NOT NULL,
    broken_seals INTEGER NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    notes VARCHAR(1000),
    evidence_id UUID,
    reconciled_by VARCHAR(160) NOT NULL,
    reconciled_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_return_counts CHECK (expected_count >= 0 AND returned_count >= 0 AND shortfall >= 0
        AND extras >= 0 AND broken_seals >= 0),
    CONSTRAINT ck_return_outcome CHECK (outcome IN ('MATCHED','DISCREPANCY'))
);

CREATE TABLE fleet_logistics.dispatch_exception_cases (
    id UUID PRIMARY KEY,
    exception_number VARCHAR(60) NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    occurrence_key VARCHAR(200) NOT NULL,
    courier_item_id UUID,
    dispatch_id UUID,
    handover_id UUID,
    receipt_id UUID,
    trip_id UUID,
    exception_type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    security_relevant BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(30) NOT NULL,
    assignee VARCHAR(160),
    sla_due_at TIMESTAMPTZ NOT NULL,
    explanation VARCHAR(2000),
    evidence_id UUID,
    manager_decision VARCHAR(20),
    closure_reason VARCHAR(500),
    escalation_level INTEGER NOT NULL DEFAULT 0,
    detected_rules JSONB,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_dispatch_exception_number UNIQUE (site_code, exception_number),
    CONSTRAINT uq_dispatch_exception_occurrence UNIQUE (site_code, occurrence_key),
    CONSTRAINT ck_dispatch_exception_escalation CHECK (escalation_level >= 0)
);

CREATE TABLE fleet_logistics.dispatch_exception_case_history (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor VARCHAR(160) NOT NULL,
    comment VARCHAR(2000),
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(120)
);

CREATE INDEX ix_return_dispatch ON fleet_logistics.return_reconciliations(dispatch_id, reconciled_at DESC);
CREATE INDEX ix_return_site_outcome ON fleet_logistics.return_reconciliations(site_code, outcome, reconciled_at);
CREATE INDEX ix_dispatch_exception_site_status ON fleet_logistics.dispatch_exception_cases(site_code, status, sla_due_at);
CREATE INDEX ix_dispatch_exception_type ON fleet_logistics.dispatch_exception_cases(site_code, exception_type, severity);
CREATE INDEX ix_dispatch_exception_dispatch ON fleet_logistics.dispatch_exception_cases(dispatch_id);
CREATE INDEX ix_dispatch_exception_item ON fleet_logistics.dispatch_exception_cases(courier_item_id);
CREATE INDEX ix_dispatch_exception_history_case ON fleet_logistics.dispatch_exception_case_history(case_id, occurred_at);
