-- ADR 0007: PostgreSQL Row-Level Security, the defence-in-depth half of site scoping.
--
-- Site scope has always been enforced in the application layer and filtered in SQL, which is correct
-- and tested. It rests entirely on every repository method remembering to apply the filter, and this
-- platform has twice shipped a method that did not: `FacilityFaultController.findAll()` returned every
-- fault at every site to any caller, and `FleetAccessPolicy.requireRecordScope` was passed null at its
-- only call site and enforced nothing. RLS is the layer that makes those harmless.
--
-- ## The two mechanisms, and why both are needed
--
-- **The principal reaches the session as a GUC.** `SiteScopeGuc` issues `SET LOCAL app.site_scopes`
-- inside every transaction, from the authenticated actor. `SET LOCAL` is transaction-scoped, so it
-- cannot leak to the next borrower of a pooled connection — the failure people fear from this design
-- and the one thing it actually handles. Absent or empty yields **no rows**: the policies fail closed,
-- which is the entire point of adding a second layer.
--
-- **RLS applies to a role that is not the owner.** A table owner bypasses RLS unless FORCE is set, and
-- FORCE would apply the policies to Flyway itself — so a migration that backfills would silently write
-- nothing, which is the worst failure this could have. Instead the schema owner (`sfl`) keeps its
-- bypass and runs migrations, and a separate `sfl_app` role carries the policies. Production connects
-- as `sfl_app`; development and the test suite keep connecting as the owner, so nothing that works
-- today stops working.
--
-- That split is what makes this migration safe to apply immediately and adopt per environment. The
-- proof it works is `FacilitiesRowLevelSecurityTest`, which connects as `sfl_app` explicitly.
--
-- ## Scope of the policies
--
-- Every table carrying `site_code`, except the two that must not be narrowed:
--
--   facility_audit_records      append-only evidence. An auditor's whole job is to read across sites,
--                               and a tamper-evident chain that is invisible in parts cannot be
--                               replayed — verification would report a break that is really a filter.
--   facility_runtime_configuration  read during evaluation for sites the actor may not hold; narrowing
--                               it would make an SLA silently unresolvable rather than refused.

-- ---------------------------------------------------------------------------------------------
-- The application role
-- ---------------------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sfl_app') THEN
        -- NOLOGIN by default: an environment that adopts this grants LOGIN and a password itself,
        -- rather than a migration inventing a credential and putting it in version control.
        CREATE ROLE sfl_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA facilities TO sfl_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA facilities TO sfl_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA facilities TO sfl_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA facilities
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sfl_app;

-- ---------------------------------------------------------------------------------------------
-- The predicate
-- ---------------------------------------------------------------------------------------------
--
-- STABLE, not IMMUTABLE: it reads a session setting, which is constant within a statement and not
-- across them. Marking it IMMUTABLE would let the planner cache a scope across transactions, which is
-- precisely the leak this design exists to avoid.
CREATE OR REPLACE FUNCTION facilities.site_in_scope(row_site TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT CASE
        -- Unset or empty: no rows. Failing closed is the whole value of a second layer — a policy that
        -- opened up when the application forgot to set the scope would protect nothing.
        WHEN coalesce(current_setting('app.site_scopes', true), '') = '' THEN FALSE
        -- '*' is the cross-site scope, matching SiteScopeFilter.all() and crossProgrammeRoles.
        WHEN current_setting('app.site_scopes', true) = '*' THEN TRUE
        WHEN '*' = ANY (string_to_array(current_setting('app.site_scopes', true), ',')) THEN TRUE
        WHEN row_site IS NULL THEN FALSE
        ELSE row_site = ANY (string_to_array(current_setting('app.site_scopes', true), ','))
    END;
$$;

-- ---------------------------------------------------------------------------------------------
-- Apply to every site-scoped table
-- ---------------------------------------------------------------------------------------------
--
-- A loop rather than 26 hand-written statements: the list is derived from the catalogue, so a table
-- added later with a site_code and no policy is a visible omission rather than a silent one. The two
-- exemptions are named here so the reason travels with the rule.
DO $$
DECLARE
    target RECORD;
    scope_column TEXT;
BEGIN
    FOR target IN
        SELECT DISTINCT c.table_name
          FROM information_schema.columns c
         WHERE c.table_schema = 'facilities'
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
    END LOOP;
END
$$;

COMMENT ON FUNCTION facilities.site_in_scope(TEXT) IS
    'ADR 0007. Reads app.site_scopes, set per transaction by SiteScopeGuc. Fails closed when unset.';
