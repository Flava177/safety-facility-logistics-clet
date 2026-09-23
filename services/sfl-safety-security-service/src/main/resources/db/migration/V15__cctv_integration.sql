-- S161 CCTV / Video Management System Integration. Lands in safety_security (Hibernate's default
-- schema for this deployable), alongside the shared audit_log from V10 and the outbox scaffolded in
-- V1, the same way S160 (V11), S163 (V12) and S160a (V14) do.

CREATE TABLE safety_security.cctv_inbox_messages (
    id                UUID PRIMARY KEY,
    source_system     VARCHAR(80) NOT NULL,
    idempotency_key   VARCHAR(200) NOT NULL,
    event_type        VARCHAR(120) NOT NULL,
    site_scope        VARCHAR(80),
    payload_hash      VARCHAR(64) NOT NULL,
    raw_payload       TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL,
    processed_at      TIMESTAMPTZ,
    correlation_id    VARCHAR(120),
    CONSTRAINT uq_cctv_inbox_source_key UNIQUE (source_system, idempotency_key)
);

CREATE TABLE safety_security.cctv_cameras (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    camera_id          VARCHAR(120) NOT NULL,
    name               VARCHAR(200) NOT NULL,
    location_ref       VARCHAR(120),
    coverage_area      VARCHAR(500),
    examination_area   BOOLEAN NOT NULL DEFAULT FALSE,
    recording_status   VARCHAR(20) NOT NULL CHECK (recording_status IN ('RECORDING', 'NOT_RECORDING', 'UNKNOWN')),
    health_status      VARCHAR(20) NOT NULL CHECK (health_status IN ('ONLINE', 'OFFLINE', 'FAULT')),
    last_health_at     TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT uq_cctv_cameras_site_code UNIQUE (site_code, camera_id)
);

CREATE INDEX ix_cctv_cameras_site_health ON safety_security.cctv_cameras (site_code, health_status);

CREATE TABLE safety_security.cctv_evidence_requests (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    camera_ids         VARCHAR(2000) NOT NULL,
    location_ref       VARCHAR(120),
    window_start       TIMESTAMPTZ NOT NULL,
    window_end         TIMESTAMPTZ NOT NULL,
    purpose            VARCHAR(2000) NOT NULL,
    case_ref           UUID,
    requested_by       VARCHAR(160) NOT NULL,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    decided_by         VARCHAR(160),
    decided_at         TIMESTAMPTZ,
    decision_notes     VARCHAR(2000),
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120)
);

CREATE INDEX ix_cctv_evidence_requests_site_status ON safety_security.cctv_evidence_requests (site_code, status);

CREATE TABLE safety_security.cctv_evidence_items (
    id                  UUID PRIMARY KEY,
    request_id          UUID NOT NULL,
    site_code           VARCHAR(80) NOT NULL,
    camera_id           VARCHAR(120) NOT NULL,
    window_start        TIMESTAMPTZ NOT NULL,
    window_end          TIMESTAMPTZ NOT NULL,
    export_handle       VARCHAR(300) NOT NULL,
    hash                VARCHAR(64) NOT NULL,
    provenance          VARCHAR(2000) NOT NULL,
    case_ref            UUID,
    copied_raw_video    BOOLEAN NOT NULL DEFAULT FALSE,
    copy_approval_ref   VARCHAR(200),
    status              VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'PURGED')),
    created_by          VARCHAR(160) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    last_modified_by    VARCHAR(160) NOT NULL,
    last_modified_at    TIMESTAMPTZ NOT NULL,
    record_version      BIGINT NOT NULL DEFAULT 0,
    source_channel      VARCHAR(20) NOT NULL,
    correlation_id      VARCHAR(120)
);

CREATE INDEX ix_cctv_evidence_items_request ON safety_security.cctv_evidence_items (request_id);
CREATE INDEX ix_cctv_evidence_items_status ON safety_security.cctv_evidence_items (status);

CREATE TABLE safety_security.cctv_evidence_access_log (
    id                 UUID PRIMARY KEY,
    evidence_item_id   UUID NOT NULL,
    actor_id           VARCHAR(160) NOT NULL,
    action             VARCHAR(20) NOT NULL CHECK (action IN ('VIEWED', 'DOWNLOADED', 'EXPORTED')),
    occurred_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_cctv_evidence_access_log_item ON safety_security.cctv_evidence_access_log
    (evidence_item_id, occurred_at DESC);

CREATE TABLE safety_security.cctv_analytics_alerts (
    id                   UUID PRIMARY KEY,
    site_code            VARCHAR(80) NOT NULL,
    camera_id            VARCHAR(120) NOT NULL,
    type                 VARCHAR(20) NOT NULL CHECK (type IN
        ('MOTION', 'LINE_CROSSING', 'LOITERING', 'TAMPER')),
    severity             VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status               VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    occurred_at          TIMESTAMPTZ NOT NULL,
    seeded_incident_id   UUID,
    siem_forwarded_at    TIMESTAMPTZ,
    created_by           VARCHAR(160) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    last_modified_by     VARCHAR(160) NOT NULL,
    last_modified_at     TIMESTAMPTZ NOT NULL,
    record_version       BIGINT NOT NULL DEFAULT 0,
    source_channel       VARCHAR(20) NOT NULL,
    correlation_id       VARCHAR(120)
);

CREATE INDEX ix_cctv_analytics_alerts_site_status ON safety_security.cctv_analytics_alerts (site_code, status);

CREATE TABLE safety_security.cctv_retention_policies (
    id                   UUID PRIMARY KEY,
    site_code            VARCHAR(80) NOT NULL,
    scope                VARCHAR(20) NOT NULL CHECK (scope IN ('CAMERA', 'ZONE')),
    scope_ref            VARCHAR(120) NOT NULL,
    retention_days       INT NOT NULL,
    legal_hold           BOOLEAN NOT NULL DEFAULT FALSE,
    legal_hold_reason    VARCHAR(2000),
    created_by           VARCHAR(160) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    last_modified_by     VARCHAR(160) NOT NULL,
    last_modified_at     TIMESTAMPTZ NOT NULL,
    record_version       BIGINT NOT NULL DEFAULT 0,
    source_channel       VARCHAR(20) NOT NULL,
    correlation_id       VARCHAR(120),
    CONSTRAINT uq_cctv_retention_policies_scope UNIQUE (site_code, scope, scope_ref)
);

CREATE TABLE safety_security.cctv_disclosures (
    id                 UUID PRIMARY KEY,
    evidence_item_id   UUID NOT NULL,
    site_code          VARCHAR(80) NOT NULL,
    purpose            VARCHAR(2000) NOT NULL,
    recipient          VARCHAR(300) NOT NULL,
    requested_by       VARCHAR(160) NOT NULL,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    decided_by         VARCHAR(160),
    decided_at         TIMESTAMPTZ,
    hash               VARCHAR(64),
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120)
);

CREATE INDEX ix_cctv_disclosures_site ON safety_security.cctv_disclosures (site_code, status);

CREATE TABLE safety_security.cctv_live_view_sessions (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    operator_id        VARCHAR(160) NOT NULL,
    camera_ids         VARCHAR(2000) NOT NULL,
    started_at         TIMESTAMPTZ NOT NULL,
    ended_at           TIMESTAMPTZ
);

CREATE INDEX ix_cctv_live_view_sessions_site ON safety_security.cctv_live_view_sessions (site_code, started_at DESC);
