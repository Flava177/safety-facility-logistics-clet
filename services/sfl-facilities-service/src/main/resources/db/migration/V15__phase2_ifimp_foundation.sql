-- =============================================================================================
-- Phase 2 IFIMP foundation (SRS CLET/DTI/CL9/SFL/SRS/2026/002, Section 3.1).
--
-- Three things every one of S156, S157, S158, S169, S173 and S176 depends on, landed once so the six
-- system migrations that follow (V16..V21) do not each invent their own:
--
--   1. facilities.apply_site_scope_policies() - the V14 catalogue loop, made callable.
--   2. bookings.cleaning_requirement           - the S159 half of SRS-SFL-S169-01.
--   3. facilities.vendor_inbox_messages        - the one authenticated inbox for vendor telemetry
--                                                 (S156 BMS/IoT, S157 AMI gateway) and the S078 hand-off.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- 1. Row-level security, from each Phase 2 table's first migration (SRS CORR-06, NFR-SEC3).
--
-- V14 applied the policies with a loop over the catalogue - and ran once. A table created in V16 has
-- a site_code, is not in any policy, and is readable across sites by sfl_app until somebody notices.
-- That is exactly the deferral ADR 0007 recorded against Phase 1, one migration later.
--
-- So the loop becomes a function, and every Phase 2 migration ends with
--
--     SELECT facilities.apply_site_scope_policies();
--
-- which covers the tables it just created in the same transaction that created them. It is safe to
-- run any number of times: ENABLE is idempotent and the policy is dropped and recreated. The two V14
-- exemptions are carried forward verbatim, and Phase2RowLevelSecurityCoverageTest asserts that no
-- other table with a site_code is left without a policy, so an omission fails the build rather than
-- waiting to be read.
--
-- Sequences are granted here as well as tables. V14 granted the sequences that existed on the day it
-- ran; a sequence created by a later migration was not covered, and an sfl_app INSERT that draws a
-- reference number from one would fail with permission denied the first time production ran it.
-- ---------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION facilities.apply_site_scope_policies()
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    target RECORD;
    scope_column TEXT;
    applied INTEGER := 0;
BEGIN
    GRANT USAGE ON SCHEMA facilities TO sfl_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA facilities TO sfl_app;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA facilities TO sfl_app;

    FOR target IN
        SELECT DISTINCT c.table_name
          FROM information_schema.columns c
          JOIN information_schema.tables t
            ON t.table_schema = c.table_schema AND t.table_name = c.table_name
         WHERE c.table_schema = 'facilities'
           AND t.table_type = 'BASE TABLE'
           AND c.column_name IN ('site_code', 'site_scope')
           AND c.table_name NOT IN ('facility_audit_records', 'facility_runtime_configuration')
         ORDER BY c.table_name
    LOOP
        SELECT column_name INTO scope_column
          FROM information_schema.columns
         WHERE table_schema = 'facilities'
           AND table_name = target.table_name
           AND column_name IN ('site_code', 'site_scope')
         ORDER BY column_name
         LIMIT 1;

        EXECUTE format('ALTER TABLE facilities.%I ENABLE ROW LEVEL SECURITY', target.table_name);
        EXECUTE format('DROP POLICY IF EXISTS site_scope_read ON facilities.%I', target.table_name);
        EXECUTE format(
            'CREATE POLICY site_scope_read ON facilities.%I FOR ALL TO sfl_app '
            || 'USING (facilities.site_in_scope(%I::text)) '
            || 'WITH CHECK (facilities.site_in_scope(%I::text))',
            target.table_name, scope_column, scope_column);
        applied := applied + 1;
    END LOOP;
    RETURN applied;
END
$$;

COMMENT ON FUNCTION facilities.apply_site_scope_policies() IS
    'ADR 0007 / SRS-2026-002 CORR-06. Applies the fail-closed site_scope_read policy to every table '
    'carrying site_code. Every Phase 2 migration calls it last, so RLS exists from a table''s first '
    'migration rather than being retrofitted.';

-- ---------------------------------------------------------------------------------------------
-- 2. What cleaning a booking asks for - SRS-SFL-S169-01.
--
-- On the booking row, because the requester states it when booking and nobody should re-enter it.
-- NOT NULL with a default, so every existing booking reads as "none" rather than "unknown".
-- ---------------------------------------------------------------------------------------------
ALTER TABLE facilities.bookings
    ADD COLUMN IF NOT EXISTS cleaning_requirement VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE facilities.bookings DROP CONSTRAINT IF EXISTS ck_bookings_cleaning_requirement;
ALTER TABLE facilities.bookings ADD CONSTRAINT ck_bookings_cleaning_requirement
    CHECK (cleaning_requirement IN ('NONE', 'SETUP', 'TEARDOWN', 'SETUP_AND_TEARDOWN'));

-- ---------------------------------------------------------------------------------------------
-- 3. vendor_inbox_messages - the authenticated front door for every inbound vendor message.
--
-- SRS-SFL-S156-01 and NFR-SEC2: "unauthenticated or malformed telemetry is rejected and logged,
-- never acted on". Both halves are here. An accepted message is recorded in the same transaction as
-- the domain action it causes, so a failure releases the claim; a REJECTED one is written in a
-- transaction of its own, because a rejection that rolled back with the refusal it caused would be a
-- log nobody could read.
--
-- site_code is nullable, and that is the one place in Phase 2 where it is. A forged message may carry
-- no site, or one that does not exist, and it must still be logged. Under RLS a NULL site is visible
-- only to the '*' scope - which is who investigates forged traffic.
--
-- Idempotency is (source_system, idempotency_key) among ACCEPTED rows only. A message rejected for a
-- bad signature and then resent correctly is the vendor fixing its clock, not a duplicate.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.vendor_inbox_messages (
    id               UUID PRIMARY KEY,
    source_system    VARCHAR(80)  NOT NULL,
    channel          VARCHAR(40)  NOT NULL,
    message_type     VARCHAR(120),
    idempotency_key  VARCHAR(200),
    site_code        VARCHAR(40),
    outcome          VARCHAR(20)  NOT NULL,
    rejection_reason VARCHAR(60),
    rejection_detail VARCHAR(1000),
    payload_hash     VARCHAR(64),
    signed_at        TIMESTAMPTZ,
    received_at      TIMESTAMPTZ  NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ck_vendor_inbox_outcome CHECK (outcome IN ('ACCEPTED', 'REJECTED', 'DUPLICATE')),
    CONSTRAINT ck_vendor_inbox_channel CHECK (channel IN ('BMS_TELEMETRY', 'ENERGY_METERING', 'CCP_EVENTS')),
    -- A rejection must say why. "Rejected" alone is a log line nobody can act on.
    CONSTRAINT ck_vendor_inbox_reason CHECK (outcome <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_vendor_inbox_accepted
    ON facilities.vendor_inbox_messages (source_system, idempotency_key)
    WHERE outcome = 'ACCEPTED';
CREATE INDEX IF NOT EXISTS ix_vendor_inbox_rejections
    ON facilities.vendor_inbox_messages (channel, received_at)
    WHERE outcome = 'REJECTED';

SELECT facilities.apply_site_scope_policies();
