-- S160 Visitor Management. Lands in safety_security (Hibernate's default schema for this
-- deployable), alongside the shared audit_log from V10 and the outbox/inbox scaffolded in V1.

CREATE TABLE safety_security.visitor_visits (
    id                        UUID PRIMARY KEY,
    site_code                 VARCHAR(80) NOT NULL,
    visitor_name              VARCHAR(200) NOT NULL,
    visitor_organization      VARCHAR(200),
    visitor_contact           VARCHAR(200),
    host_id                   VARCHAR(160) NOT NULL,
    host_name                 VARCHAR(200),
    purpose                   VARCHAR(30) NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    expected_arrival          TIMESTAMPTZ NOT NULL,
    expected_departure        TIMESTAMPTZ,
    approval_required         BOOLEAN NOT NULL,
    approval_id               UUID,
    watchlist_flagged         BOOLEAN NOT NULL DEFAULT FALSE,
    watchlist_override_reason VARCHAR(2000),
    badge_number              VARCHAR(80),
    access_zones              VARCHAR(1000),
    checked_in_at             TIMESTAMPTZ,
    checked_out_at            TIMESTAMPTZ,
    closure_reason            VARCHAR(2000),
    created_by                VARCHAR(160) NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL,
    last_modified_by          VARCHAR(160) NOT NULL,
    last_modified_at          TIMESTAMPTZ NOT NULL,
    record_version            BIGINT NOT NULL DEFAULT 0,
    source_channel            VARCHAR(20) NOT NULL,
    correlation_id            VARCHAR(120),
    CONSTRAINT ck_visitor_visits_status CHECK (status IN
        ('PRE_REGISTERED', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'REJECTED', 'CANCELLED', 'NO_SHOW'))
);

CREATE INDEX ix_visitor_visits_site_status ON safety_security.visitor_visits (site_code, status);
CREATE INDEX ix_visitor_visits_host ON safety_security.visitor_visits (host_id);
CREATE INDEX ix_visitor_visits_expected_arrival ON safety_security.visitor_visits (expected_arrival);

-- One row per on-site visitor, so the roll-call query never has to reason about status inside a
-- larger scan. Refreshed by the JPA entity write path, not maintained separately.
CREATE INDEX ix_visitor_visits_on_site ON safety_security.visitor_visits (site_code)
    WHERE status = 'CHECKED_IN';

CREATE TABLE safety_security.visitor_approvals (
    id          UUID PRIMARY KEY,
    visit_id    UUID NOT NULL REFERENCES safety_security.visitor_visits (id),
    site_code   VARCHAR(80) NOT NULL,
    decision    VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    reason      VARCHAR(2000),
    decided_by  VARCHAR(160) NOT NULL,
    decided_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_visitor_approvals_visit ON safety_security.visitor_approvals (visit_id, decided_at);
