-- S171 unbroken chain-of-custody handovers (append-only) and destination receipt confirmation (edge-capable).
CREATE TABLE fleet_logistics.custody_handovers (
    id UUID PRIMARY KEY,
    dispatch_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    hop VARCHAR(30) NOT NULL,
    sequence_no INTEGER NOT NULL,
    transferring_custodian VARCHAR(160) NOT NULL,
    receiving_custodian VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    seal_state VARCHAR(20) NOT NULL,
    verified_count INTEGER,
    notes VARCHAR(1000),
    evidence_id UUID,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    CONSTRAINT uq_custody_sequence UNIQUE (dispatch_id, sequence_no),
    CONSTRAINT ck_custody_verified_count CHECK (verified_count IS NULL OR verified_count >= 0)
);

CREATE TABLE fleet_logistics.dispatch_receipts (
    id UUID PRIMARY KEY,
    dispatch_id UUID NOT NULL,
    site_code VARCHAR(40) NOT NULL,
    seal_state VARCHAR(20) NOT NULL,
    seal_verified BOOLEAN NOT NULL,
    expected_count INTEGER NOT NULL,
    verified_count INTEGER NOT NULL,
    recipient_name VARCHAR(200) NOT NULL,
    signature_evidence_id UUID,
    outcome VARCHAR(20) NOT NULL,
    variance_type VARCHAR(30),
    captured_at TIMESTAMPTZ NOT NULL,
    edge_captured BOOLEAN NOT NULL DEFAULT false,
    capture_correlation_id VARCHAR(120) NOT NULL,
    reconciled_at TIMESTAMPTZ,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_by VARCHAR(160) NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    source_channel VARCHAR(40) NOT NULL,
    audit_correlation_id VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_dispatch_receipt_capture UNIQUE (dispatch_id, capture_correlation_id),
    CONSTRAINT ck_dispatch_receipt_counts CHECK (expected_count >= 0 AND verified_count >= 0),
    CONSTRAINT ck_dispatch_receipt_outcome CHECK (outcome IN ('CLEAN','VARIANCE'))
);

CREATE INDEX ix_custody_dispatch ON fleet_logistics.custody_handovers(dispatch_id, sequence_no);
CREATE INDEX ix_custody_site_date ON fleet_logistics.custody_handovers(site_code, occurred_at DESC);
CREATE INDEX ix_dispatch_receipt_dispatch ON fleet_logistics.dispatch_receipts(dispatch_id, captured_at DESC);
CREATE INDEX ix_dispatch_receipt_variance ON fleet_logistics.dispatch_receipts(site_code, outcome, captured_at);
