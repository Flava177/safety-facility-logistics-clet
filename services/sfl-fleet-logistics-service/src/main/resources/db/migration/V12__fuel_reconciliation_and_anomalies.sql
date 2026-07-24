CREATE TABLE fleet_logistics.fuel_reconciliations (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES fleet_logistics.fuel_transactions(id),
    policy_id UUID REFERENCES fleet_logistics.fuel_policies(id),
    policy_version INTEGER,
    outcome VARCHAR(30) NOT NULL,
    calculated_consumption NUMERIC(19,4),
    evaluated_at TIMESTAMPTZ NOT NULL,
    evaluated_by VARCHAR(160) NOT NULL,
    rule_results JSONB NOT NULL,
    correlation_id VARCHAR(120) NOT NULL
);

CREATE TABLE fleet_logistics.fuel_anomaly_cases (
    id UUID PRIMARY KEY,
    anomaly_number VARCHAR(50) NOT NULL UNIQUE,
    site_code VARCHAR(40) NOT NULL,
    transaction_id UUID REFERENCES fleet_logistics.fuel_transactions(id),
    logbook_id UUID REFERENCES fleet_logistics.driver_logbooks(id),
    vehicle_id UUID,
    driver_id UUID,
    trip_id UUID,
    anomaly_type VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    material BOOLEAN NOT NULL,
    status VARCHAR(40) NOT NULL,
    assignee VARCHAR(160),
    sla_due_at TIMESTAMPTZ NOT NULL,
    explanation VARCHAR(2000),
    evidence_id UUID,
    manager_decision VARCHAR(40),
    closure_reason VARCHAR(1000),
    escalation_level INTEGER NOT NULL DEFAULT 0,
    detected_rules JSONB NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fuel_anomaly_rule UNIQUE NULLS NOT DISTINCT (transaction_id, logbook_id, anomaly_type),
    CONSTRAINT ck_fuel_anomaly_escalation CHECK (escalation_level >= 0)
);

CREATE INDEX ix_fuel_anomaly_queue ON fleet_logistics.fuel_anomaly_cases(site_code, status, sla_due_at);
CREATE INDEX ix_fuel_reconciliation_transaction ON fleet_logistics.fuel_reconciliations(transaction_id, evaluated_at DESC);
