-- The tamper-evident audit chain for the safety_security schema. Shared by every module that lands
-- here (S160 first consumer, S160a-S163 as they are built), unlike emergency_notification's own
-- audit_events table which S174 owns outright. See platform.infrastructure.persistence.AuditAdapter.

CREATE TABLE safety_security.audit_log (
    id             UUID PRIMARY KEY,
    sequence_no    BIGINT NOT NULL,
    actor          VARCHAR(160) NOT NULL,
    action         VARCHAR(120) NOT NULL,
    resource_type  VARCHAR(120) NOT NULL,
    resource_id    VARCHAR(120) NOT NULL,
    site_scope     VARCHAR(80),
    before_value   JSONB,
    after_value    JSONB,
    source_channel VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(120),
    reason         VARCHAR(2000),
    occurred_at    TIMESTAMPTZ NOT NULL,
    previous_hash  VARCHAR(64),
    record_hash    VARCHAR(64) NOT NULL
);

CREATE UNIQUE INDEX ux_safety_security_audit_log_sequence ON safety_security.audit_log (sequence_no);
CREATE INDEX ix_safety_security_audit_log_resource ON safety_security.audit_log (resource_type, resource_id);
