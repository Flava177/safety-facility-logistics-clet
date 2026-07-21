-- =====================================================================================
-- SRS-SFL-S166-05 — Dashboards and Reports
-- =====================================================================================

CREATE TABLE fleet_logistics.fleet_dashboard_snapshots (
    id UUID PRIMARY KEY,
    generated_at TIMESTAMPTZ NOT NULL,
    scope_key VARCHAR(240) NOT NULL,
    site_code VARCHAR(40),
    stale BOOLEAN NOT NULL,
    warnings VARCHAR(2000),
    vehicles_available BIGINT NOT NULL,
    expired_compliance BIGINT NOT NULL,
    service_due BIGINT NOT NULL,
    assignment_conflicts BIGINT NOT NULL,
    readiness_blockers BIGINT NOT NULL,
    open_workflow_items BIGINT NOT NULL,
    escalated_workflow_items BIGINT NOT NULL,
    integration_dead_letters BIGINT NOT NULL,
    vehicles BIGINT NOT NULL,
    compliance_documents BIGINT NOT NULL,
    trips BIGINT NOT NULL,
    workflow_items BIGINT NOT NULL,
    latest_service_records BIGINT NOT NULL,
    recent_vehicle_locations BIGINT NOT NULL,
    snapshot_as_of TIMESTAMPTZ NOT NULL,
    freshest_source_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_fleet_dashboard_snapshots_scope_generated
    ON fleet_logistics.fleet_dashboard_snapshots (scope_key, generated_at DESC);
