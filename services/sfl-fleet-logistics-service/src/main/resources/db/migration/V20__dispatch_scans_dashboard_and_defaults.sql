-- S171 optional scanner ingestion, dashboard read-model snapshot and configurable defaults.
CREATE TABLE fleet_logistics.scan_import_batches (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    batch_reference VARCHAR(120) NOT NULL,
    source_system VARCHAR(100) NOT NULL,
    dispatch_id UUID,
    total_rows INTEGER NOT NULL,
    accepted_rows INTEGER NOT NULL,
    mismatch_rows INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_scan_batch_reference UNIQUE (site_code, source_system, batch_reference),
    CONSTRAINT ck_scan_batch_counts CHECK (total_rows >= 0 AND accepted_rows >= 0 AND mismatch_rows >= 0)
);

CREATE TABLE fleet_logistics.scan_import_rows (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    row_reference VARCHAR(120) NOT NULL,
    scanned_code VARCHAR(160) NOT NULL,
    courier_item_id UUID,
    outcome VARCHAR(30) NOT NULL,
    message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_scan_row UNIQUE (batch_id, row_reference)
);

CREATE TABLE fleet_logistics.dispatch_dashboard_snapshots (
    id UUID PRIMARY KEY,
    scope_key VARCHAR(120) NOT NULL,
    site_code VARCHAR(40),
    generated_at TIMESTAMPTZ NOT NULL,
    stale BOOLEAN NOT NULL,
    in_transit_count INTEGER NOT NULL,
    open_exception_count INTEGER NOT NULL,
    custody_gap_count INTEGER NOT NULL,
    receipt_variance_count INTEGER NOT NULL,
    outstanding_return_count INTEGER NOT NULL,
    undelivered_count INTEGER NOT NULL,
    overdue_receipt_count INTEGER NOT NULL,
    sla_breach_count INTEGER NOT NULL,
    source_updated_at TIMESTAMPTZ,
    warnings TEXT
);

CREATE INDEX ix_scan_batch_site_date ON fleet_logistics.scan_import_batches(site_code, created_at DESC);
CREATE INDEX ix_scan_row_batch ON fleet_logistics.scan_import_rows(batch_id);
CREATE INDEX ix_scan_row_outcome ON fleet_logistics.scan_import_rows(site_code, outcome);
CREATE INDEX ix_dispatch_dashboard_scope ON fleet_logistics.dispatch_dashboard_snapshots(scope_key, generated_at DESC);

-- Configurable S171 SLA/threshold defaults (platform scope; site overrides insert non-null site_code rows).
INSERT INTO fleet_logistics.fleet_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, updated_by, updated_at)
VALUES
    (gen_random_uuid(), 'dispatch.undelivered.window', NULL, 'PT48H', 'DURATION', 'Inbound item undelivered/unclaimed escalation window.', now(), 'system', now()),
    (gen_random_uuid(), 'dispatch.outstanding-return.window', NULL, 'P3D', 'DURATION', 'Not-yet-returned item escalation window.', now(), 'system', now()),
    (gen_random_uuid(), 'dispatch.exception.sla.default', NULL, 'PT24H', 'DURATION', 'Base dispatch exception SLA (severity-adjusted).', now(), 'system', now()),
    (gen_random_uuid(), 'dispatch.dashboard.freshness-threshold', NULL, 'PT15M', 'DURATION', 'Dispatch dashboard stale-data threshold.', now(), 'system', now()),
    (gen_random_uuid(), 'dispatch.scheduling.undelivered-enabled', NULL, 'true', 'BOOLEAN', 'Enable undelivered-item sweep.', now(), 'system', now()),
    (gen_random_uuid(), 'dispatch.scheduling.outstanding-return-enabled', NULL, 'true', 'BOOLEAN', 'Enable outstanding-return sweep.', now(), 'system', now()),
    (gen_random_uuid(), 'dispatch.scheduling.sla-enabled', NULL, 'true', 'BOOLEAN', 'Enable exception SLA-escalation sweep.', now(), 'system', now()),
    (gen_random_uuid(), 'dispatch.scheduling.dashboard-enabled', NULL, 'true', 'BOOLEAN', 'Enable dashboard snapshot refresh.', now(), 'system', now()),
    (gen_random_uuid(), 'dispatch.scheduling.stale-integration-enabled', NULL, 'true', 'BOOLEAN', 'Enable stale-integration detection.', now(), 'system', now())
ON CONFLICT DO NOTHING;
