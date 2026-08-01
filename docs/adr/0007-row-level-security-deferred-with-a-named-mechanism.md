# ADR 0007 — Row-Level Security: the mechanism is chosen, the implementation is deferred

- Status: **Accepted**, 31 July 2026. Decision taken; implementation scheduled, not built.
- **Unblocked 1 August 2026:** A1 landed, so the precondition this ADR names — "until the actor is a
  verified JWT principal rather than an `X-SFL-*` header, RLS would be enforcing scopes asserted by
  the caller, which is theatre" — no longer holds. The `site_scopes` claim the policies will read is
  now issued by the realm and consumed by every actor resolver. This is the next thing to build.
- Date: 2026-07-31
- Deciders: SFL platform / DTI Cluster 9 architect. **Requires DTI platform sign-off on §Decision
  before the first migration is written.**
- Relates: [0001 phase-1 foundation](0001-phase-1-foundation.md); `solution.md` §Eventing, Outbox /
  Inbox & Data conventions; `docs/fleet/S166_Gap_And_Conflict_Report.md` C-09

## Context

`solution.md` and workplan §7 both require *"PostgreSQL Row-Level Security + repository site-scope
filter driven by the principal's SiteScopes"*. Only half of that exists.

**What is built, in every service.** Site scope is enforced in the application layer on every command
and query, and every list query is site-filtered **in SQL** by the caller's authorised sites — not
filtered in memory after loading. Since 31 July 2026 the record-level rules sit alongside it: a vendor
technician sees only work assigned to them, a requester only what they reported or booked, a driver
only their own trips and logbooks. All of it is tested, including by refusal on a direct id.

**What is not built.** Database-enforced RLS policies. So the guarantee currently rests entirely on
every repository method remembering to apply the filter. That is a real exposure and not a theoretical
one: this platform has already shipped `FacilityFaultController.findAll()` with **no permission check
and no site filter**, returning every fault at every site to any caller, and has shipped a documented,
unit-tested record-scope policy (`FleetAccessPolicy.requireRecordScope`) whose only production call
site passed `null` and enforced nothing. Both were found by reading the code, not by a test failing.
RLS is precisely the control that would have made either harmless.

**Why it stalled.** S166 C-09 records the reason honestly: RLS needs a decision about how the request
principal reaches the database *session*, and under connection pooling that is an operational
decision, not a code-local one. It was left open rather than guessed, and has stayed open across four
build passes — which is long enough that leaving it open again would be a decision by default.

## Options considered

**1. Per-request session GUC.** Each request sets `SET LOCAL app.site_scopes = '...'` on the pooled
connection inside the transaction; policies read `current_setting('app.site_scopes')`.

- `SET LOCAL` is transaction-scoped, so it cannot leak to the next borrower of a pooled connection —
  which is the failure mode that makes people frightened of this approach, and it is the one thing the
  design already handles.
- Costs one extra statement per transaction.
- Works with HikariCP unchanged, and with the existing `ActorContext`.
- A code path that opens a transaction without setting it gets **no rows** rather than **all rows**,
  provided the policy is written to fail closed. That default is the whole point.

**2. Per-tenant database roles.** A role per site, `SET ROLE` per request.

- Rejected. Site scopes are a set per principal, not a single tenant — a director scoped to four sites
  has no single role to assume. It also puts authorisation state into database role management, which
  is administered by a different team on a different change cadence than the permission matrices.

**3. Application-layer only, and amend the standard.** Delete the requirement from `solution.md`.

- Rejected, but it was seriously considered. The application filter is real, tested and enforced in
  SQL, and one could argue the standard overreached. It is rejected because the two defects above are
  evidence that the class of mistake is live in this codebase, and defence-in-depth is exactly for the
  case where the first layer is written by people who are sometimes wrong.

## Decision

**Adopt option 1, the per-request session GUC.** Specifically:

- A `SiteScopeConnectionInitializer` in `sfl-service-common` issues `SET LOCAL app.site_scopes` from
  the current `ActorContext` at the start of each transaction.
- Policies are `FORCE ROW LEVEL SECURITY`, written to fail **closed**: absent or empty
  `app.site_scopes` yields zero rows.
- `'*'` remains the cross-site scope, matching `SiteScopeFilter.all()`, so the application and database
  layers agree on what "all sites" means rather than encoding it twice.
- The audit tables are exempt: they are append-only, insert-only by role, and an auditor's whole job is
  to read across sites.

**Implementation is deferred to the pass that also turns authentication on (A1).** The two are one
piece of work: until the actor is a verified JWT principal rather than an `X-SFL-*` header, RLS would
be enforcing scopes asserted by the caller — which is theatre. Sequencing RLS *after* authentication is
not a delay, it is the only order in which it means anything.

## Consequences

- Until it lands, site scope has exactly one layer of enforcement, and that layer is the one this
  codebase has twice been observed to get wrong. **This must be stated in the go-live pack**, not left
  in an ADR.
- Every new repository method continues to carry the obligation to filter, and code review is the only
  thing enforcing it.
- When it lands, expect it to *find* something. A query that quietly relied on seeing rows outside the
  caller's scope will start returning fewer rows, and that will look like a regression. It is not one.
- One extra round trip per transaction. At Phase 1 volumes this is not worth measuring; it is recorded
  so nobody re-litigates it later on a hunch.

## Owner and date

**DTI platform** owns the sign-off on the session-GUC mechanism. Until that sign-off exists this ADR is
a decision the SFL team has taken and the platform team has not yet ratified, and it should be read
that way.
