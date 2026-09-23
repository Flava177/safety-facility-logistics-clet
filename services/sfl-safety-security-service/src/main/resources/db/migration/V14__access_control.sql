-- S160a Physical Access Control Integration. Lands in safety_security (Hibernate's default schema
-- for this deployable), alongside the shared audit_log from V10 and the outbox scaffolded in V1,
-- the same way S160 (V11) and S163 (V12) do.

CREATE TABLE safety_security.access_control_inbox_messages (
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
    CONSTRAINT uq_access_control_inbox_source_key UNIQUE (source_system, idempotency_key)
);

CREATE TABLE safety_security.access_events (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    source_system      VARCHAR(80) NOT NULL,
    external_event_id  VARCHAR(200) NOT NULL,
    reader_id          VARCHAR(120) NOT NULL,
    door_id            VARCHAR(120),
    zone_code          VARCHAR(80) NOT NULL,
    person_ref         VARCHAR(160),
    kind               VARCHAR(20) NOT NULL CHECK (kind IN
        ('GRANTED', 'DENIED', 'FORCED_OPEN', 'OVERRIDE', 'TAMPER')),
    direction          VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' CHECK (direction IN ('ENTRY', 'EXIT', 'UNKNOWN')),
    occurred_at        TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT uq_access_events_source_external UNIQUE (source_system, external_event_id)
);

CREATE INDEX ix_access_events_site_zone_occurred ON safety_security.access_events
    (site_code, zone_code, occurred_at DESC);
CREATE INDEX ix_access_events_person ON safety_security.access_events (site_code, zone_code, person_ref, occurred_at DESC);

CREATE TABLE safety_security.access_reader_health (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    reader_id          VARCHAR(120) NOT NULL,
    zone_code          VARCHAR(80) NOT NULL,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('ONLINE', 'OFFLINE', 'TAMPERED', 'DEGRADED')),
    last_seen_at       TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT uq_access_reader_health_site_reader UNIQUE (site_code, reader_id)
);

CREATE TABLE safety_security.access_zones (
    id                       UUID PRIMARY KEY,
    site_code                VARCHAR(80) NOT NULL,
    zone_code                VARCHAR(80) NOT NULL,
    name                     VARCHAR(200) NOT NULL,
    location_ref             VARCHAR(120),
    schedule                 VARCHAR(500) NOT NULL,
    door_groups_text         VARCHAR(2000),
    examination_mode         BOOLEAN NOT NULL DEFAULT FALSE,
    locked_door_groups_text  VARCHAR(500),
    examination_until        TIMESTAMPTZ,
    created_by               VARCHAR(160) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    last_modified_by         VARCHAR(160) NOT NULL,
    last_modified_at         TIMESTAMPTZ NOT NULL,
    record_version           BIGINT NOT NULL DEFAULT 0,
    source_channel           VARCHAR(20) NOT NULL,
    correlation_id           VARCHAR(120),
    CONSTRAINT uq_access_zones_site_code UNIQUE (site_code, zone_code)
);

CREATE TABLE safety_security.access_provisioning (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    person_ref         VARCHAR(160) NOT NULL,
    zone_code          VARCHAR(80) NOT NULL,
    basis              VARCHAR(20) NOT NULL CHECK (basis IN ('JOINER', 'MOVER', 'LEAVER', 'MANUAL')),
    reason             VARCHAR(2000),
    approver_id        VARCHAR(160),
    expires_at         TIMESTAMPTZ,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ NOT NULL,
    record_version     BIGINT NOT NULL DEFAULT 0,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120)
);

CREATE INDEX ix_access_provisioning_person ON safety_security.access_provisioning (site_code, person_ref);
CREATE INDEX ix_access_provisioning_status ON safety_security.access_provisioning (site_code, status);

CREATE TABLE safety_security.access_overrides (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    scope_ref          VARCHAR(200) NOT NULL,
    reason             VARCHAR(2000) NOT NULL,
    requested_by       VARCHAR(160) NOT NULL,
    approver_id        VARCHAR(160),
    break_glass        BOOLEAN NOT NULL DEFAULT FALSE,
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

CREATE INDEX ix_access_overrides_status ON safety_security.access_overrides (status, expires_at);
CREATE INDEX ix_access_overrides_site_status ON safety_security.access_overrides (site_code, status);

CREATE TABLE safety_security.access_exceptions (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(80) NOT NULL,
    event_id            UUID,
    reader_id           VARCHAR(120),
    zone_code           VARCHAR(80) NOT NULL,
    rule_code           VARCHAR(30) NOT NULL CHECK (rule_code IN
        ('REPEATED_DENIAL', 'FORCED_OPEN', 'TAILGATING', 'OUT_OF_HOURS', 'RESTRICTED_ZONE', 'READER_OFFLINE',
         'ANTI_PASSBACK')),
    severity            VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status               VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
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

CREATE INDEX ix_access_exceptions_site_status ON safety_security.access_exceptions (site_code, status);
