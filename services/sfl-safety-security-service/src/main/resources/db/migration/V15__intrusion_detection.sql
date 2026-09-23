-- S162 Intrusion Detection & Alarm Monitoring. Lands in safety_security (Hibernate's default schema
-- for this deployable), alongside the shared audit_log from V10 and the outbox scaffolded in V1, the
-- same way S160/S163 (V11/V12) and S160a (V14) do.

CREATE TABLE safety_security.intrusion_inbox_messages (
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
    CONSTRAINT uq_intrusion_inbox_source_key UNIQUE (source_system, idempotency_key)
);

CREATE TABLE safety_security.intrusion_signals (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    source             VARCHAR(80) NOT NULL,
    external_event_id  VARCHAR(200) NOT NULL,
    panel_id           VARCHAR(120) NOT NULL,
    zone_code          VARCHAR(80) NOT NULL,
    signal_type        VARCHAR(20) NOT NULL CHECK (signal_type IN
        ('ZONE_ALARM', 'TAMPER', 'FAULT', 'RESTORATION')),
    occurred_at        TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT uq_intrusion_signals_source_external UNIQUE (source, external_event_id)
);

CREATE INDEX ix_intrusion_signals_site_zone_occurred ON safety_security.intrusion_signals
    (site_code, zone_code, occurred_at DESC);

CREATE TABLE safety_security.intrusion_alarms (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(80) NOT NULL,
    panel_id            VARCHAR(120) NOT NULL,
    zone_code           VARCHAR(80) NOT NULL,
    alarm_type          VARCHAR(20) NOT NULL CHECK (alarm_type IN
        ('ZONE_ALARM', 'TAMPER', 'DEVICE_FAULT', 'ARM_FAILURE')),
    severity            VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status               VARCHAR(20) NOT NULL CHECK (status IN
        ('RAISED', 'ACKNOWLEDGED', 'ESCALATED', 'COALESCED', 'RESOLVED')),
    signal_count         INT NOT NULL DEFAULT 1,
    first_signal_at      TIMESTAMPTZ NOT NULL,
    last_signal_at       TIMESTAMPTZ NOT NULL,
    ack_due_at           TIMESTAMPTZ,
    acknowledged_at      TIMESTAMPTZ,
    acknowledged_by      VARCHAR(160),
    escalated_at         TIMESTAMPTZ,
    escalated_to         VARCHAR(40),
    resolved_at          TIMESTAMPTZ,
    resolved_by          VARCHAR(160),
    evidence_ref         VARCHAR(200),
    seeded_incident_id   UUID,
    created_by           VARCHAR(160) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    last_modified_by     VARCHAR(160) NOT NULL,
    last_modified_at     TIMESTAMPTZ NOT NULL,
    record_version       BIGINT NOT NULL DEFAULT 0,
    source_channel       VARCHAR(20) NOT NULL,
    correlation_id       VARCHAR(120)
);

CREATE INDEX ix_intrusion_alarms_site_status ON safety_security.intrusion_alarms (site_code, status);
CREATE INDEX ix_intrusion_alarms_open_lookup ON safety_security.intrusion_alarms
    (site_code, panel_id, zone_code, alarm_type, status);

CREATE TABLE safety_security.intrusion_panel_health (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    panel_id           VARCHAR(120) NOT NULL,
    zone_code          VARCHAR(80) NOT NULL,
    status             VARCHAR(20) NOT NULL CHECK (status IN
        ('ONLINE', 'OFFLINE', 'FAULT', 'TAMPERED', 'FLAPPING')),
    last_seen_at       TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT uq_intrusion_panel_health_site_panel UNIQUE (site_code, panel_id)
);

CREATE TABLE safety_security.intrusion_zones (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(80) NOT NULL,
    zone_code           VARCHAR(80) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    location_ref        VARCHAR(120),
    arm_schedule        VARCHAR(500) NOT NULL,
    protected_zone      BOOLEAN NOT NULL DEFAULT FALSE,
    armed               BOOLEAN NOT NULL DEFAULT FALSE,
    examination_mode    BOOLEAN NOT NULL DEFAULT FALSE,
    examination_until   TIMESTAMPTZ,
    created_by          VARCHAR(160) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    last_modified_by    VARCHAR(160) NOT NULL,
    last_modified_at    TIMESTAMPTZ NOT NULL,
    record_version      BIGINT NOT NULL DEFAULT 0,
    source_channel      VARCHAR(20) NOT NULL,
    correlation_id      VARCHAR(120),
    CONSTRAINT uq_intrusion_zones_site_code UNIQUE (site_code, zone_code)
);

CREATE TABLE safety_security.intrusion_disarm_overrides (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    zone_code          VARCHAR(80) NOT NULL,
    reason             VARCHAR(2000) NOT NULL,
    requested_by       VARCHAR(160) NOT NULL,
    approver_id        VARCHAR(160) NOT NULL,
    starts_at          TIMESTAMPTZ NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120)
);

CREATE INDEX ix_intrusion_disarm_overrides_status ON safety_security.intrusion_disarm_overrides
    (status, expires_at);
CREATE INDEX ix_intrusion_disarm_overrides_site_status ON safety_security.intrusion_disarm_overrides
    (site_code, status);

CREATE TABLE safety_security.intrusion_response_dispatches (
    id                      UUID PRIMARY KEY,
    site_code               VARCHAR(80) NOT NULL,
    alarm_id                UUID NOT NULL,
    monitoring_service      VARCHAR(200) NOT NULL,
    dispatch_requested_at   TIMESTAMPTZ NOT NULL,
    acknowledged_at         TIMESTAMPTZ,
    arrived_at              TIMESTAMPTZ,
    outcome                 VARCHAR(20) NOT NULL CHECK (outcome IN
        ('PENDING', 'CONFIRMED_INTRUSION', 'FALSE_ALARM', 'NO_RESPONSE', 'CANCELLED')),
    notes                   VARCHAR(2000),
    created_by              VARCHAR(160) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    last_modified_by        VARCHAR(160) NOT NULL,
    last_modified_at        TIMESTAMPTZ NOT NULL,
    record_version          BIGINT NOT NULL DEFAULT 0,
    source_channel          VARCHAR(20) NOT NULL,
    correlation_id          VARCHAR(120),
    CONSTRAINT uq_intrusion_response_dispatches_alarm UNIQUE (alarm_id)
);

CREATE INDEX ix_intrusion_response_dispatches_site_outcome ON safety_security.intrusion_response_dispatches
    (site_code, outcome);
