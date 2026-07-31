-- SRS-SFL-S153-02, the response half of the SLA.
--
-- `maintenance.sla.response.*` has been read from runtime configuration, stored in SlaPolicy and
-- exposed on the API since S153 shipped, and nothing has ever used it: only the *resolution* deadline
-- drove escalation. So "nobody has picked this up" and "nobody has finished this" were the same event,
-- which is the one distinction an escalation ladder exists to make. A job nobody has touched for three
-- hours and a job somebody has been working on for three hours need different people told.
--
-- Two columns, both nullable:
--
--   response_due_at        when somebody should have started. Computed at creation from the policy
--                          active at that moment, exactly as sla_due_at is.
--   response_escalated_at  when the response breach was raised. The idempotence marker: the sweep is
--                          at-least-once, and without this it would notify the same supervisor on
--                          every run for as long as the job sat untouched — which is the fastest way
--                          to make an escalation ignored.
--
-- Deliberately NOT backfilled. Every open work order would otherwise breach its response deadline the
-- moment this deploys, producing a wall of escalations on day one about jobs nobody has newly
-- neglected. This is the same decision V9 recorded for sla_due_at, for the same reason, and it has the
-- same consequence: work open at cutover has no response deadline until it is next assigned. That
-- belongs on the go-live checklist, not in a backfill.

ALTER TABLE facilities.work_orders
    ADD COLUMN response_due_at TIMESTAMPTZ,
    ADD COLUMN response_escalated_at TIMESTAMPTZ;

-- The sweep's query: work that is due a response, has not been started, and has not already been
-- raised. Partial, because the rows that matter are a small and shrinking subset of the table.
CREATE INDEX ix_work_orders_response_due
    ON facilities.work_orders (response_due_at)
    WHERE response_due_at IS NOT NULL
      AND response_escalated_at IS NULL
      AND started_at IS NULL;
