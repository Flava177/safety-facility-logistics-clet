-- S163 HSE Incident / Near-Miss Reporting. Lands in safety_security (Hibernate's default schema
-- for this deployable), alongside the shared audit_log from V10 and S160's tables from V11.
--
-- source/reference/anonymous etc. follow SecurityIncident - the SRS's shared incident-case aggregate
-- (see SecurityIncident's class Javadoc): S160a/S161/S162/S162a will seed into this same table once
-- built, distinguished by `source`, rather than each getting a table of their own.

CREATE TABLE safety_security.security_incidents (
    id                    UUID PRIMARY KEY,
    site_code             VARCHAR(80) NOT NULL,
    source                VARCHAR(20) NOT NULL,
    reference             VARCHAR(40) NOT NULL,
    anonymous             BOOLEAN NOT NULL DEFAULT FALSE,
    reporter_id           VARCHAR(160),
    reporter_contact      VARCHAR(200),
    description           VARCHAR(4000) NOT NULL,
    near_miss             BOOLEAN NOT NULL DEFAULT FALSE,
    status                VARCHAR(20) NOT NULL,
    severity              VARCHAR(20),
    likelihood            VARCHAR(20),
    impact                VARCHAR(20),
    emergency_escalated   BOOLEAN NOT NULL DEFAULT FALSE,
    investigator_id       VARCHAR(160),
    investigation_notes   VARCHAR(4000),
    reportable            BOOLEAN NOT NULL DEFAULT FALSE,
    reportability_notes   VARCHAR(2000),
    closure_notes         VARCHAR(4000),
    closed_at             TIMESTAMPTZ,
    created_by            VARCHAR(160) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    last_modified_by      VARCHAR(160) NOT NULL,
    last_modified_at      TIMESTAMPTZ NOT NULL,
    record_version        BIGINT NOT NULL DEFAULT 0,
    source_channel        VARCHAR(20) NOT NULL,
    correlation_id        VARCHAR(120),
    CONSTRAINT ck_security_incidents_source CHECK (source IN
        ('REPORTED', 'HSE', 'CCTV_SEED', 'ACCESS_SEED', 'INTRUSION_SEED', 'FIRE_SEED')),
    CONSTRAINT ck_security_incidents_status CHECK (status IN ('TRIAGE', 'INVESTIGATING', 'CLOSED')),
    CONSTRAINT ck_security_incidents_severity CHECK (severity IS NULL OR
        severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'EMERGENCY')),
    CONSTRAINT ck_security_incidents_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE UNIQUE INDEX ux_security_incidents_reference ON safety_security.security_incidents (reference);
CREATE INDEX ix_security_incidents_site_status ON safety_security.security_incidents (site_code, status);
CREATE INDEX ix_security_incidents_site_severity ON safety_security.security_incidents (site_code, severity);

CREATE TABLE safety_security.corrective_actions (
    id                   UUID PRIMARY KEY,
    incident_id          UUID NOT NULL REFERENCES safety_security.security_incidents (id),
    site_code            VARCHAR(80) NOT NULL,
    description          VARCHAR(2000) NOT NULL,
    owner_id             VARCHAR(160) NOT NULL,
    due_date             DATE NOT NULL,
    mandatory            BOOLEAN NOT NULL DEFAULT TRUE,
    status               VARCHAR(20) NOT NULL,
    verification_notes   VARCHAR(2000),
    created_by           VARCHAR(160) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    resolved_by          VARCHAR(160),
    resolved_at          TIMESTAMPTZ,
    CONSTRAINT ck_corrective_actions_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'VERIFIED', 'CANCELLED')),
    CONSTRAINT ck_corrective_actions_resolved CHECK
        ((status IN ('VERIFIED', 'CANCELLED')) = (resolved_at IS NOT NULL))
);

CREATE INDEX ix_corrective_actions_incident ON safety_security.corrective_actions (incident_id, status);

-- Backs SecurityIncidentRepository#countOpenMandatoryCorrectiveActions - the hard-rule-1 closure gate.
CREATE INDEX ix_corrective_actions_open_mandatory ON safety_security.corrective_actions (incident_id)
    WHERE mandatory = TRUE AND status IN ('OPEN', 'IN_PROGRESS');

CREATE TABLE safety_security.incident_evidence (
    id               UUID PRIMARY KEY,
    incident_id      UUID NOT NULL REFERENCES safety_security.security_incidents (id),
    site_code        VARCHAR(80) NOT NULL,
    file_reference   VARCHAR(500) NOT NULL,
    file_name        VARCHAR(255),
    media_type       VARCHAR(120),
    size_bytes       BIGINT,
    content_hash     VARCHAR(64) NOT NULL,
    retention_class  VARCHAR(20) NOT NULL,
    notes            VARCHAR(2000),
    uploaded_by      VARCHAR(160) NOT NULL,
    uploaded_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_incident_evidence_retention CHECK (retention_class IN ('STANDARD', 'EXTENDED', 'PERMANENT'))
);

CREATE INDEX ix_incident_evidence_incident ON safety_security.incident_evidence (incident_id, uploaded_at);
