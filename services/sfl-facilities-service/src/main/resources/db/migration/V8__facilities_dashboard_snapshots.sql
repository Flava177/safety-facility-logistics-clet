-- =====================================================================================
-- S152 CAFM / IWMS — dashboard snapshots (SRS-SFL-S152-05).
--
-- "Dashboard snapshot ID, metric code, period, site scope, generated timestamp and
-- source record references" are named as system-managed fields, and "dashboard snapshots
-- shall be suitable for operational review and go-live readiness reporting" — so the
-- numbers behind a review have to be reproducible after the estate has moved on.
--
-- The live dashboard is computed from the source tables on request; snapshots exist so a
-- go-live report can be re-read later, not to serve the screen. That is why nothing here
-- is on the read path and why generated_at is what the freshness warning is measured
-- against.
-- =====================================================================================

CREATE TABLE facilities.facility_dashboard_snapshots (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    operating_mode VARCHAR(20) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by VARCHAR(160) NOT NULL,
    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,
    spaces_total INTEGER NOT NULL DEFAULT 0,
    spaces_ready INTEGER NOT NULL DEFAULT 0,
    spaces_degraded INTEGER NOT NULL DEFAULT 0,
    spaces_blocked INTEGER NOT NULL DEFAULT 0,
    spaces_unknown INTEGER NOT NULL DEFAULT 0,
    spaces_unavailable_for_booking INTEGER NOT NULL DEFAULT 0,
    spaces_unavailable_for_examination INTEGER NOT NULL DEFAULT 0,
    spaces_with_stale_readiness INTEGER NOT NULL DEFAULT 0,
    blockers_critical INTEGER NOT NULL DEFAULT 0,
    blockers_major INTEGER NOT NULL DEFAULT 0,
    blockers_minor INTEGER NOT NULL DEFAULT 0,
    blockers_advisory INTEGER NOT NULL DEFAULT 0,
    assets_total INTEGER NOT NULL DEFAULT 0,
    assets_impaired INTEGER NOT NULL DEFAULT 0,
    assets_critical_impaired INTEGER NOT NULL DEFAULT 0,
    assets_service_overdue INTEGER NOT NULL DEFAULT 0,
    open_work_orders INTEGER NOT NULL DEFAULT 0,
    open_faults INTEGER NOT NULL DEFAULT 0,
    readiness_score INTEGER NOT NULL DEFAULT 0,
    correlation_id VARCHAR(120),
    CONSTRAINT ck_facility_dashboard_snapshots_mode CHECK (operating_mode IN ('ROUTINE', 'EXAMINATION')),
    CONSTRAINT ck_facility_dashboard_snapshots_score CHECK (readiness_score BETWEEN 0 AND 100),
    CONSTRAINT ck_facility_dashboard_snapshots_period CHECK
        (period_start IS NULL OR period_end IS NULL OR period_end >= period_start)
);

CREATE INDEX ix_facility_dashboard_snapshots_site_generated
    ON facilities.facility_dashboard_snapshots (site_code, generated_at DESC);

-- A snapshot is a historical statement, so it is append-only for the same reason an
-- assessment is: a go-live report that could be edited afterwards proves nothing.
CREATE TRIGGER trg_facility_dashboard_snapshots_append_only
    BEFORE UPDATE OR DELETE ON facilities.facility_dashboard_snapshots
    FOR EACH ROW EXECUTE FUNCTION facilities.facility_append_only_guard();

-- -------------------------------------------------------------------------------------
-- The source records behind a snapshot's exception counts.
--
-- "Dashboard records shall link back to source workflows and evidence where the user has
-- permission" — a count with no way back to the rows that produced it cannot be
-- reconciled, and SRS-SFL-S152-05 requires that "dashboard counts must reconcile to
-- source workflow/read-model records".
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_dashboard_snapshot_references (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES facilities.facility_dashboard_snapshots(id) ON DELETE CASCADE,
    metric_code VARCHAR(60) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id UUID NOT NULL,
    label VARCHAR(300)
);

CREATE INDEX ix_facility_dashboard_snapshot_references_snapshot
    ON facilities.facility_dashboard_snapshot_references (snapshot_id, metric_code);
