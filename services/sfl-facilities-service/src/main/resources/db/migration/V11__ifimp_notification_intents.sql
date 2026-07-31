-- SRS-SFL-S153-02: "notify when work is assigned, overdue, escalated or blocked".
--
-- The escalation sweep has always published `sfl.ifimp.work-order-escalated.v1` and stopped there, and
-- the gap report was blunt that the requirement is not met until somebody is told. Two things had to be
-- true before this table was worth adding, and now both are: the outbox is actually drained
-- (FacilitiesOutboxDrainer), and there is a precedent to copy rather than a design to invent —
-- fleet_logistics.fleet_notification_intents, which S166-02 uses for the identical requirement.
--
-- Deliberately an *intent*, not a delivery. Phase 1 has no notification provider for IFIMP, so the
-- adapter records what should be sent and to whom, and never reports a success it did not achieve. An
-- operator can read this table and reconcile it; a log line and a shrug is not evidence that a
-- technician was told their job was overdue.
--
-- Column shapes mirror the fleet table exactly, so the two can be read by one query and, if a provider
-- is ever procured, drained by one adapter. VARCHAR with a CHECK rather than CHAR: Hibernate maps
-- String(length=n) to VARCHAR(n) and refuses CHAR(n) under ddl-auto: validate, which cost this
-- platform a day in V5.

CREATE TABLE facilities.facility_notification_intents (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    recipient_type VARCHAR(20) NOT NULL,
    recipient VARCHAR(160) NOT NULL,
    notification_kind VARCHAR(60) NOT NULL,
    subject_reference VARCHAR(160) NOT NULL,
    context JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    failure_reason VARCHAR(1000),
    CONSTRAINT ck_facility_notification_recipient_type CHECK (recipient_type IN ('USER', 'ROLE'))
);

-- The query an operator runs: what is outstanding, oldest first.
CREATE INDEX ix_facility_notification_status_created
    ON facilities.facility_notification_intents (status, created_at);

-- The query a person runs about themselves: what was I told, and when.
CREATE INDEX ix_facility_notification_recipient
    ON facilities.facility_notification_intents (recipient, created_at DESC);
