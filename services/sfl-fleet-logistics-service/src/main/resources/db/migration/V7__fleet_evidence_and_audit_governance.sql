-- =====================================================================================
-- SRS-SFL-S166-03 — Evidence and Audit Trail
-- =====================================================================================

CREATE TABLE fleet_logistics.fleet_evidence_references (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    related_record_type VARCHAR(80) NOT NULL,
    related_record_id VARCHAR(160) NOT NULL,
    evidence_type VARCHAR(80) NOT NULL,
    file_name VARCHAR(240) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    storage_reference VARCHAR(500) NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    retention_class VARCHAR(40) NOT NULL,
    retention_expires_at TIMESTAMPTZ,
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_evidence_retention_required CHECK (retention_class <> ''),
    CONSTRAINT ck_fleet_evidence_hash_sha256 CHECK (sha256_hash ~ '^[A-Fa-f0-9]{64}$'),
    CONSTRAINT ck_fleet_evidence_retention_window CHECK (
        retention_expires_at IS NULL OR retention_expires_at >= created_at
    )
);

CREATE INDEX ix_fleet_evidence_related_record
    ON fleet_logistics.fleet_evidence_references (related_record_type, related_record_id, created_at DESC);
CREATE INDEX ix_fleet_evidence_site_retention
    ON fleet_logistics.fleet_evidence_references (site_code, retention_class, retention_expires_at);

CREATE TABLE fleet_logistics.fleet_evidence_export_requests (
    id UUID PRIMARY KEY,
    evidence_id UUID NOT NULL REFERENCES fleet_logistics.fleet_evidence_references (id) ON DELETE RESTRICT,
    site_code VARCHAR(40) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_by VARCHAR(160) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    decided_by VARCHAR(160),
    decided_at TIMESTAMPTZ,
    decision_reason VARCHAR(1000),
    exported_by VARCHAR(160),
    exported_at TIMESTAMPTZ,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT ck_fleet_export_decision CHECK (
        status NOT IN ('APPROVED', 'REJECTED', 'EXPORTED')
        OR (decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NOT NULL)
    ),
    CONSTRAINT ck_fleet_export_approval CHECK (
        status <> 'EXPORTED' OR (exported_by IS NOT NULL AND exported_at IS NOT NULL AND decided_by IS NOT NULL)
    ),
    CONSTRAINT ck_fleet_export_approval_separation CHECK (
        decided_by IS NULL OR lower(decided_by) <> lower(requested_by)
    )
);

CREATE INDEX ix_fleet_evidence_export_evidence
    ON fleet_logistics.fleet_evidence_export_requests (evidence_id, requested_at DESC);
CREATE INDEX ix_fleet_evidence_export_site_status
    ON fleet_logistics.fleet_evidence_export_requests (site_code, status, requested_at DESC);
