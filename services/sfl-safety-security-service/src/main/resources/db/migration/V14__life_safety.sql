-- S162a Fire-Safety & Life-Safety Monitoring. Lands in safety_security (Hibernate's default schema
-- for this deployable), alongside the shared audit_log from V10 and S160/S163's tables from V11/V12.
--
-- Observe-only by design (SRS-SFL-S162a-04): no table here represents a command sent to the
-- certified fire/life-safety system - only what this module has observed, recorded or scheduled
-- itself. seeded_incident_id-style FKs to other modules are deliberately absent; cross-module
-- references (S163 incidents, S174 activations) are held by value only.

CREATE TABLE safety_security.lifesafety_inbox_messages (
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
    CONSTRAINT uq_lifesafety_inbox_source_key UNIQUE (source_system, idempotency_key)
);

CREATE TABLE safety_security.lifesafety_events (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(80) NOT NULL,
    source_system      VARCHAR(80) NOT NULL,
    external_event_id  VARCHAR(200),
    device_id          VARCHAR(120),
    zone_code          VARCHAR(80),
    kind               VARCHAR(20) NOT NULL CHECK (kind IN
        ('SMOKE', 'FIRE', 'PANIC', 'PANEL_FAULT', 'INSPECTION_STATUS')),
    occurred_at        TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    source_channel     VARCHAR(20) NOT NULL,
    correlation_id     VARCHAR(120)
);

CREATE INDEX ix_lifesafety_events_site_occurred ON safety_security.lifesafety_events (site_code, occurred_at DESC);

CREATE TABLE safety_security.lifesafety_fast_lane_triggers (
    id                     UUID PRIMARY KEY,
    site_code              VARCHAR(80) NOT NULL,
    zone_code              VARCHAR(80),
    life_safety_event_id   UUID NOT NULL,
    activation_id          UUID,
    status                 VARCHAR(20) NOT NULL CHECK (status IN ('TRIGGERED', 'DEGRADED')),
    latency_millis         BIGINT NOT NULL,
    note                   VARCHAR(500),
    triggered_at           TIMESTAMPTZ NOT NULL,
    created_by             VARCHAR(160) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,
    source_channel         VARCHAR(20) NOT NULL,
    correlation_id         VARCHAR(120)
);

CREATE INDEX ix_lifesafety_fast_lane_site_triggered ON safety_security.lifesafety_fast_lane_triggers
    (site_code, triggered_at DESC);

CREATE TABLE safety_security.lifesafety_inspection_schedules (
    id                   UUID PRIMARY KEY,
    site_code            VARCHAR(80) NOT NULL,
    system_ref           VARCHAR(120) NOT NULL,
    description          VARCHAR(500),
    frequency_days       INTEGER NOT NULL,
    last_performed_at    TIMESTAMPTZ,
    next_due_at          TIMESTAMPTZ,
    evidence_reference   VARCHAR(500),
    created_by           VARCHAR(160) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    last_modified_by     VARCHAR(160) NOT NULL,
    last_modified_at     TIMESTAMPTZ NOT NULL,
    record_version       BIGINT NOT NULL DEFAULT 0,
    source_channel       VARCHAR(20) NOT NULL,
    correlation_id       VARCHAR(120)
);

CREATE INDEX ix_lifesafety_inspection_site_due ON safety_security.lifesafety_inspection_schedules
    (site_code, next_due_at);

CREATE TABLE safety_security.lifesafety_compliance_exceptions (
    id                UUID PRIMARY KEY,
    site_code         VARCHAR(80) NOT NULL,
    ref_type          VARCHAR(30) NOT NULL CHECK (ref_type IN ('INSPECTION_SCHEDULE', 'DETECTOR', 'PANEL')),
    ref_id            UUID,
    kind              VARCHAR(30) NOT NULL CHECK (kind IN
        ('OVERDUE_INSPECTION', 'PANEL_FAULT', 'COVERAGE_GAP', 'FAILED_TEST')),
    status            VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    note              VARCHAR(2000),
    raised_at         TIMESTAMPTZ NOT NULL,
    resolved_by       VARCHAR(160),
    resolved_at       TIMESTAMPTZ,
    created_by        VARCHAR(160) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    last_modified_by  VARCHAR(160) NOT NULL,
    last_modified_at  TIMESTAMPTZ NOT NULL,
    record_version    BIGINT NOT NULL DEFAULT 0,
    source_channel    VARCHAR(20) NOT NULL,
    correlation_id    VARCHAR(120),
    CONSTRAINT ck_lifesafety_compliance_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

CREATE INDEX ix_lifesafety_compliance_site_status ON safety_security.lifesafety_compliance_exceptions
    (site_code, status);
CREATE INDEX ix_lifesafety_compliance_ref ON safety_security.lifesafety_compliance_exceptions
    (site_code, ref_id, kind, status);

CREATE TABLE safety_security.lifesafety_detector_coverage (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(80) NOT NULL,
    zone_code           VARCHAR(80) NOT NULL,
    device_ref          VARCHAR(120) NOT NULL,
    device_type         VARCHAR(30) NOT NULL CHECK (device_type IN
        ('SMOKE_DETECTOR', 'HEAT_DETECTOR', 'PANIC_DEVICE', 'SIREN')),
    covered             BOOLEAN NOT NULL DEFAULT TRUE,
    last_test_at        TIMESTAMPTZ,
    next_test_due_at    TIMESTAMPTZ,
    last_test_result    VARCHAR(20) NOT NULL CHECK (last_test_result IN ('NOT_TESTED', 'PASS', 'FAIL')),
    created_by          VARCHAR(160) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    last_modified_by    VARCHAR(160) NOT NULL,
    last_modified_at    TIMESTAMPTZ NOT NULL,
    record_version      BIGINT NOT NULL DEFAULT 0,
    source_channel      VARCHAR(20) NOT NULL,
    correlation_id      VARCHAR(120),
    CONSTRAINT uq_lifesafety_detector_site_ref UNIQUE (site_code, device_ref)
);

CREATE INDEX ix_lifesafety_detector_site_zone ON safety_security.lifesafety_detector_coverage (site_code, zone_code);

CREATE TABLE safety_security.lifesafety_muster_sessions (
    id                    UUID PRIMARY KEY,
    site_code             VARCHAR(80) NOT NULL,
    zone_code             VARCHAR(80) NOT NULL,
    triggering_event_id   UUID,
    status                VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    opened_at             TIMESTAMPTZ NOT NULL,
    closed_at             TIMESTAMPTZ,
    created_by            VARCHAR(160) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    last_modified_by      VARCHAR(160) NOT NULL,
    last_modified_at      TIMESTAMPTZ NOT NULL,
    record_version        BIGINT NOT NULL DEFAULT 0,
    source_channel        VARCHAR(20) NOT NULL,
    correlation_id        VARCHAR(120)
);

CREATE INDEX ix_lifesafety_muster_open ON safety_security.lifesafety_muster_sessions (site_code, zone_code, status);

CREATE TABLE safety_security.lifesafety_muster_checkins (
    id                  UUID PRIMARY KEY,
    muster_session_id   UUID NOT NULL REFERENCES safety_security.lifesafety_muster_sessions (id),
    person_ref          VARCHAR(160) NOT NULL,
    checked_in_at        TIMESTAMPTZ NOT NULL,
    source               VARCHAR(20) NOT NULL,
    CONSTRAINT uq_lifesafety_muster_checkin UNIQUE (muster_session_id, person_ref)
);

CREATE INDEX ix_lifesafety_muster_checkins_session ON safety_security.lifesafety_muster_checkins (muster_session_id);
