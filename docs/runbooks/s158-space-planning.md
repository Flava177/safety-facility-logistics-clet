# Runbook - S158 Space Planning & Move Management

**Scope.** The `spaceplanning` module of `sfl-facilities-service`: allocation scenarios, occupancy
standards and overrides, utilisation reconciliation against S159, and space-change requests. For a
service that is down entirely, use [`incident-response.md`](incident-response.md) first - this runbook
assumes the service is up and answers "something in S158 specifically looks wrong."

## What it owns

- Migration `V18__space_planning.sql`, schema `facilities` (shared with every module in this service):
  `space_scenarios`, `space_scenario_allocations`, `occupancy_standards`, `occupancy_overrides`,
  `space_utilisation_snapshots`, `space_utilisation_signals`, `space_change_requests`.
- One table outside `spaceplanning` and owned by S152 instead: `space_allocations`, the **current-state
  allocation register**. Phase 1 S152 held no such register (a room carried only `cost_centre`); this
  build added it to `masterdata` because S158 must never be the thing a committed scenario writes into
  - see the gap report, item 1, for why. It is written only by
  `masterdata.application.SpaceAllocationService.applyCommittedChange`, called from S158's own services.
- REST: `/api/v1/facilities/space-planning/**` - see `S158_API_Reference.md`.
- Events: `sfl.ifimp.space-scenario-committed.v1`, `-handed-over.v1`, `-allocation-applied.v1`,
  `occupancy-override-recorded.v1`, `utilisation-signal-raised.v1` / `-cleared.v1`,
  `space-change-request-submitted.v1` / `-decided.v1` / `-resolved.v1` - see `S158_Event_Contracts.md`.
  Recorded to the outbox, not yet delivered (no drainer exists in this service - see that document's
  Status section, and `incident-response.md`'s eventing notes).

## Health checks, in order

1. `GET /actuator/health` - service up at all.
2. `GET /api/v1/facilities/space-planning/dashboard?siteCode=<SITE>` with a token holding
   `FACILITIES_SPACE_PLAN_READ` - if this answers, the module's read path, the S152 register and the
   utilisation tables are all reachable.
3. Compare the register to reality:
   ```sql
   SELECT room_code, allocated_unit, headcount, source_reference, allocated_since
     FROM facilities.space_allocations
    WHERE site_code = '<SITE>' AND ended_at IS NULL
    ORDER BY room_code;
   ```
4. Any scenario stuck `COMMITTED` with `commit_outcome = 'PHYSICAL_WORKS'` and no `handed_over_at` is
   waiting on S176:
   ```sql
   SELECT plan_reference, version_number, name, linked_project_id, linked_project_reference, committed_at
     FROM facilities.space_scenarios
    WHERE site_code = '<SITE>' AND status = 'COMMITTED' AND commit_outcome = 'PHYSICAL_WORKS'
    ORDER BY committed_at;
   ```
   This is normal while the works are actually in progress. It is a problem only alongside evidence the
   project already finished in S176 - see §"S176 handover did not arrive" below.

## Scheduled job

| Job | Property | Default | What it does |
|---|---|---|---|
| Utilisation pull | `sfl.space-planning.utilisation.interval-ms` | `3600000` (1h) | For every operational site, evaluates the latest complete reporting period against S159, stores a snapshot per room, raises/clears signals. `sfl.space-planning.utilisation.initial-delay-ms` (default `600000`) delays the first run after startup. |

Disable with `sfl.space-planning.scheduling.enabled=false` (default `true`, `matchIfMissing`). One
uncaught failure for one site is logged and does **not** cancel the schedule (mirrors
`BookingScheduledJobs` - see that class's javadoc for why swallowing is deliberate here); a site that
keeps failing every run is worth escalating rather than waiting out.

### The job did nothing this hour

Check the log for `Space utilisation reconciliation failed for <SITE>`. The two causes that matter:

- **S159 unreadable.** The run throws `SPACE_UTILISATION_SOURCE_UNAVAILABLE` and touches nothing for
  that site - no stale snapshot is overwritten, no signal is wrongly cleared. Fix S159/the booking
  tables, then either wait for the next scheduled run or call
  `POST /api/v1/facilities/space-planning/utilisation/reconcile` with that `siteCode` to catch up
  immediately.
- **Nothing to do.** A run that lands before the next period boundary evaluates the same period again
  (idempotent - see below) and logs nothing, because nothing changed. This is normal for an hourly job
  against a 7-day period; it is not a sign the job is stuck.

## Configuration keys (all `facilities.facility_runtime_configuration`, versioned, site-overridable)

| Key | Default | Meaning |
|---|---|---|
| `space-planning.utilisation.under-threshold-percent` | `20` | Below this utilisation rate over one complete period, a room is on the under-utilisation list (SRS-SFL-S158-03 AC). |
| `space-planning.utilisation.period-days` | `7` | Reporting-period length, aligned to Monday 2024-01-01T00:00Z. |
| `space-planning.utilisation.available-hours-per-day` | `10` | Bookable hours per available day - the frequency-rate denominator. |
| `space-planning.utilisation.available-days` | `MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY` | Days that count towards available hours. |
| `space-planning.signal.gap-threshold-percent` | `30` | Planned occupancy exceeding observed utilisation by at least this, in one period, counts as a gap period. |
| `space-planning.signal.persistence-periods` | `2` | Consecutive gap periods before a planned-vs-actual gap is surfaced as a signal. |

Change with `PUT /api/v1/facilities/config` (the platform-wide configuration endpoint - not owned by
this module) or directly:

```sql
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version, updated_by, updated_at)
VALUES (gen_random_uuid(), 'space-planning.utilisation.under-threshold-percent', '<SITE>', '15',
        'INTEGER', 'Tightened for <SITE> per estates committee', now(), 0, '<you>', now());
```

## What "utilisation rate" actually means

The SRS acceptance criterion ("booked at under 20% of capacity over a full reporting period") names no
metric. This build defines one - see `UtilisationPolicy`'s javadoc for the full reasoning - and it is
worth restating here because it is the number every alert and every dashboard tile is built on:

```
frequency rate   = used minutes / available minutes              (capped at 1)
occupancy rate   = mean expected attendees per taken-up booking / capacity
utilisation rate = frequency rate x occupancy rate                (capped at 1)
```

"Used minutes" only counts bookings actually taken up (`IN_USE`/`COMPLETED`); a no-show counts as zero
use, which is the point - a room booked every morning and never entered is the strongest possible
planning signal, and folding a no-show into "booked" would hide it. "Available minutes" is the
configured bookable hours for the period, not the whole 24x7 window. "Attendees" comes from the
booker's own `expectedAttendees` at booking time - S159 records no head count and this platform has no
occupancy sensor for an ordinary room, so this is an estimate, not a measurement. Flagged in the gap
report.

A room is **not evaluated at all** (no signal raised or cleared either way) when S152 records no
capacity for it, or when it is neither bookable nor ever booked in the period - see
`UtilisationSnapshot.observable()`. A non-bookable space (most offices) with zero bookings is silent by
design, not missing data: nothing in S159 could tell S158 anything about it.

## Failure modes and what to do

| Symptom | Likely cause | What to do |
|---|---|---|
| Committing a scenario returns `SPACE_CONSTRUCTION_INTAKE_UNAVAILABLE` | S176 is not deployed in this environment, or its intake threw | The commit did not apply anything and the scenario is still a draft - safe to retry once S176 is reachable. Check S176's own health first. |
| `POST /utilisation/reconcile` returns `SPACE_UTILISATION_SOURCE_UNAVAILABLE` | S159's booking tables/service are unreachable | See "The job did nothing this hour" above. Existing snapshots and signals are untouched - there is nothing to repair, only to retry. |
| A scenario is stuck `DRAFT` and a space-change request referencing it cannot be resolved | Working as designed - `SPACE_CHANGE_UNLINKED_RESOLUTION`/`SPACE_SCENARIO_UNCOMMITTED` is the "Uncommitted Scenario Referenced" rule (SRS-SFL-S158-01) | Commit the scenario (or link a different, already-committed one) before resolving the request. |
| An override sits `PENDING` indefinitely | Nobody holding `FACILITIES_OCCUPANCY_OVERRIDE_APPROVE` who is not the requester has looked at it | Check who holds the permission (`FACILITIES_DIRECTOR` by the seeded matrix) and escalate; there is no reminder/expiry mechanism. |
| The S152 register shows an allocation nobody remembers approving | Find its scenario: `SELECT source_scenario_id, source_reference FROM facilities.space_allocations WHERE room_id = '<room>' AND ended_at IS NULL`, then read that scenario's audit history (`historyFor('AllocationScenario', <id>)`) for the commit record: who, when, outcome. | Not a repair - the register is derived from an audited commit; the fix is organisational (who authorised it), not database. |

### S176 handover did not arrive

A scenario stuck `COMMITTED`/`PHYSICAL_WORKS` with no `handed_over_at`, while S176 reports the project
complete:

1. Confirm the project reference matches: `linked_project_reference` on the scenario against S176's own
   record.
2. S176 calls `ScenarioHandover.confirmHandover(scenarioId, projectId, projectReference, confirmedBy)`
   in-process (both modules share this deployable) at its own handover step - this is not queued or
   drained, so if it "did not arrive" the call was never made. Check S176's handover code path, not an
   S158 queue.
3. The call is idempotent per project id - safe to have S176 retry it. A call naming a *different*
   project id than the scenario is already linked to is refused as an invalid transition, which is the
   correct outcome if two projects both claim the same scenario.

## Replay / repair

There is no replay mechanism specific to this module beyond re-running reconciliation for a site
(`POST /utilisation/reconcile`, optionally with `periodEnd` naming an earlier period boundary to
recompute history). Nothing here needs a queue replay: every write is synchronous and transactional,
and utilisation snapshots are idempotent per room-per-period by construction (`ux_space_utilisation_
snapshots_period`).

## RLS notes

Every V18 table carries `site_code` and is covered by `facilities.apply_site_scope_policies()`, called
last in the migration (SRS CORR-06) - `Phase2RowLevelSecurityCoverageTest` fails the build otherwise.
`SpacePlanningRowLevelSecurityTest` proves it behaviourally against `sfl_app` for `space_scenarios`
(unscoped sees nothing, scoped sees own site, a write outside scope is `42501`); the other six tables
are covered by the same policy mechanism and the generic coverage test, not repeated per table here.

## Cross-service dependencies that do not yet consume or publish

- **S176 (Construction Project Management)** is built in the same deployable in a sibling worktree at
  the time of this build. `ConstructionHandoffPort` calls its real `ConstructionProjectIntake` contract
  in-process; if that worktree has not yet been merged, every `PHYSICAL_WORKS` commit and every
  hand-to-construction request will throw `SPACE_CONSTRUCTION_INTAKE_UNAVAILABLE` until it is.
- **S140 (HRMS)** is not available to SFL at all (see the gap report). `allocatedUnit` is free text a
  planner enters, not a validated org-unit code - there is no HRMS unit list to check it against, and
  no procedure here can repair a typo except editing the next scenario.
- **CAD floorplans**, named as a dependency in the SRS mapping, are not integrated. Nothing in this
  module reads or renders a floorplan; a space is identified by its S152 room code only.
- **The outbox has no drainer in this service** (see `S158_Event_Contracts.md`). Every event this
  module publishes is recorded and inert until that is built; there is no "replay to a consumer"
  procedure to write yet because there is no consumer.
