# Runbook - S176 Construction Project Management

**Scope.** The construction project register, contractor compliance and site access, variation orders
and budget control, and handover/defects-liability tracking, on `sfl-facilities-service`
(`gh.edu.clet.sfl.facilities.construction`, schema `facilities`, migration `V21`). For the service
being down or unreachable, use [`incident-response.md`](incident-response.md) - this runbook is for
"S176 is up, but a project is stuck, a permit gate looks wrong, or an event did not arrive."

## What S176 owns

- **construction_projects** and their milestones, approvals, and versioned baseline/milestone history
  (`construction_milestones`, `construction_project_revisions`, `construction_project_approvals`).
- **construction_contractors**, their competencies (`construction_contractor_competencies`) and site-access
  grants (`construction_site_access_grants`).
- **construction_permit_projection** - S176's own read of what S164 (SSEMP, not built) has said about a
  permit, and `construction_project_permits` - the links a project manager has claimed.
- **construction_variations** - variation orders and the escalation state that gates them.
- **construction_handovers**, `construction_handover_register_changes` and **construction_defects**.

S176 does **not** own the S152 room it writes into at handover, the S153 work order a defect raises,
or the S158 scenario it confirms - those stay S152's, S153's and S158's records; S176 holds only its
own reference to each.

## Health checks

```
GET /actuator/health
GET /api/v1/facilities/construction/dashboard/integrations
```

The second is the honest one: it names every cross-module and cross-service dependency and its actual
status (`INTEGRATED_IN_PROCESS`, `CONTRACT_ONLY`, `NOT_PUBLISHED`, `NO_CONSUMER`,
`RECORDED_NOT_FORWARDED`, `NOT_INTEGRATED`, `OUT_OF_SCOPE`). Nothing here is ever reported as fully
integrated in the vendor-procurement sense (SRS CORR-07) - S176 has no vendor product to gate, but the
same honesty applies to its service-to-service dependencies.

```sql
-- Pipeline by stage, for a quick eyeball without the API.
SELECT site_code, status, count(*) FROM facilities.construction_projects GROUP BY site_code, status;

-- Every refusal to start, most recent first - what a project manager is actually hitting.
SELECT resource_id, occurred_at, after
  FROM facilities.facility_audit_records
 WHERE action = 'PROJECT_START_REFUSED'
 ORDER BY occurred_at DESC LIMIT 20;
```

## Scheduled jobs

Both are `@ConditionalOnProperty(sfl.construction.scheduling.enabled, default true)`, run on the
platform scheduler thread (RLS scope `*`), and are idempotent - running twice does no harm.

| Job | Property | Default interval | Default initial delay | What it does |
|---|---|---|---|---|
| Compliance suspension | `sfl.construction.compliance.interval-ms` | 3 600 000 ms (1 h) | `sfl.construction.compliance.initial-delay-ms` = 150 000 ms | Suspends every ACTIVE site-access grant of a contractor whose insurance or a competency has expired (SRS-SFL-S176-02). |
| Defect sync | `sfl.construction.defects.interval-ms` | 900 000 ms (15 min) | `sfl.construction.defects.initial-delay-ms` = 180 000 ms | Reads S153 for each OPEN defects-liability item and closes it when its work order is CLOSED. |

Both log at `ERROR` and are retried on the next tick rather than cancelling the schedule on an
uncaught exception - `BookingScheduledJobs`'s pattern. If either stops logging entirely (not even the
occasional `INFO` when it acts), suspect the scheduler thread itself, not the job - see
[`incident-response.md`](incident-response.md) §4.

**A contractor stuck ACTIVE past its insurance expiry**: confirm the sweep is enabled and running (the
property above), then check the contractor's row directly -

```sql
SELECT contractor_code, insurance_expires_on FROM facilities.construction_contractors WHERE id = '<id>';
SELECT id, status, valid_to FROM facilities.construction_site_access_grants WHERE contractor_id = '<id>';
```

If `insurance_expires_on` is genuinely in the past and the grant is still `ACTIVE`, the sweep has not
run since the expiry - it is date-driven (UTC, which is Ghana's civil time), so a fresh expiry today
is picked up by the next tick, not instantly.

## Configuration keys (Configuration Without Code)

Seeded by V21 into `facilities.facility_runtime_configuration` with `site_code IS NULL` (platform
default); a site-scoped row overrides it. Read live at `GET /api/v1/facilities/config` (S152's shared
endpoint) or via `RuntimeConfigurationPort.activeValues`.

| Key | Default | What it governs |
|---|---|---|
| `construction.work-types` | `GENERAL_BUILDING,FIT_OUT,REFURBISHMENT,DEMOLITION,MECHANICAL,PLUMBING,ROOFING,HOT_WORK,WORK_AT_HEIGHT,CONFINED_SPACE,ELECTRICAL_ISOLATION,EXCAVATION` | The catalogue a project's `workTypes` must be drawn from. |
| `construction.permit-required.work-types` | `HOT_WORK,WORK_AT_HEIGHT,CONFINED_SPACE,ELECTRICAL_ISOLATION,EXCAVATION` | Which work types need a current linked S164 permit before start (SRS-SFL-S176-01). |
| `construction.variation.escalation-threshold-percent` | `10` | Cumulative approved variations above this percentage of the approved baseline need escalated approval (SRS-SFL-S176-03). |
| `construction.defects-liability.period-days` | `365` | Length of the defects-liability period from handover. |
| `construction.compliance.expiry-warning-days` | `30` | Dashboard warning window for insurance/competency expiring soon (not yet lapsed). |
| `construction.sweep.batch` | `200` | Rows processed per compliance-suspension or defect-sync run. |

Changing `construction.permit-required.work-types` or the escalation threshold takes effect on the
next evaluation - no restart, no deploy. A typo in either falls back to the seeded default rather than
disabling the gate (see `ConstructionConfiguration`'s Javadoc for why that direction is the safe one).

## Failure modes and what to do

### "Every project needing a permit is stuck at approval" - this is by design, not a bug

**S164 (Permit-to-Work) is not built anywhere in this workspace.** Nothing publishes
`sfl.ssemp.permit-issued.v1` or its siblings, so `facilities.construction_permit_projection` is empty
and every project whose work type is in `construction.permit-required.work-types` is refused the start
with `PROJECT_PERMIT_MISSING` - fail-closed, per SRS-SFL-S176-01. This is correct behaviour, not an
incident. Confirm it is this and not a broken consumer:

```sql
SELECT permit_id, status, last_event_at, last_event_type
  FROM facilities.construction_permit_projection ORDER BY last_event_at DESC LIMIT 20;
```

Empty, or stale against permits you know S164 has issued once it ships → the inbound listener is not
receiving `ssemp.#` traffic; check the broker binding and `sfl.facilities.messaging.drainer-enabled` on
the *inbound* side (see [`dead-letter-recovery.md`](dead-letter-recovery.md) for the general inbound
picture). Rows present and current, but the start is still refused → check
`construction_project_permits` for the project: the link may be missing, or the linked `permit_id`
does not match what `construction_permit_projection` actually holds (a typo when linking is recorded
literally, not resolved against the projection).

### "A site-access suspension did nothing at the gate"

**Also by design.** `facilities.construction_site_access_grants.enforcement` is
`RECORDED_NOT_ENFORCED` on every row this release writes - S160a (Access Control) and S160 (Visitor
Management) are SSEMP systems with no consumer of `sfl.ifimp.contractor-site-access-requested.v1` or
`-suspended.v1` anywhere in this workspace. A suspended contractor is stopped by a person reading the
dashboard or the audit trail, not by a turnstile, until an SSEMP consumer exists. This is logged at
`WARN` on every suspension (`gh.edu.clet.sfl.facilities.construction.infrastructure.integration.OutboxSiteAccessAdapter`)
specifically so it is not silently invisible.

### "A handover keeps coming back PROJECT_HANDOVER_INCOMPLETE"

Two distinct causes, both named in the refusal reason:

1. **No register change listed at all.** The handover request's `roomChanges` was empty.
2. **A space the project declared it would touch was not updated.** Compare
   `construction_projects.affected_room_ids` (set at S158 intake) against the `roomChanges` sent -
   every declared room needs an `UPDATE` entry (a `CREATE` for a brand-new space does not, by itself,
   satisfy an *existing* declared room).

Every attempt, including flagged-incomplete ones, is kept:

```sql
SELECT id, outcome, incomplete_reason, register_change_count, recorded_at
  FROM facilities.construction_handovers WHERE project_id = '<id>' ORDER BY recorded_at;
```

The project stays at `PRACTICAL_COMPLETION` until a complete handover succeeds - there is no manual
override; the fix is always a corrected handover request with the missing room included.

### "A handover completed but the S158 scenario is UNRESOLVED"

Expected until S158 is merged into the same release: `S158ScenarioConfirmationAdapter` asks S158's
`ScenarioHandover.find`, and the scaffold answers "no such scenario" for everything. The S152 register
update has already been applied and the handover is `COMPLETE` - the space is correctly in the
register regardless. Retry the confirmation once S158 ships:

```
PATCH /api/v1/facilities/construction/projects/{id}/handover/scenario-confirmation
```

### "A project cannot close" - `PROJECT_DEFECTS_OPEN`

```sql
SELECT defect_reference, status, work_order_id FROM facilities.construction_defects
 WHERE project_id = '<id>' AND status = 'OPEN';
```

Each named item needs either its S153 work order closed (the defect-sync sweep picks this up within
15 minutes, or trigger it by re-running the sweep) or an explicit deferral with a reason:

```
PATCH /api/v1/facilities/construction/projects/{id}/defects/{defectId}/deferral
```

Closing also refuses before the defects-liability period ends (`defects_liability_ends_on` on the
project), independently of open items - this is a stated interpretation of the SRS workflow (see the
gap report), not an error state with its own code; it surfaces as `INVALID_STATE_TRANSITION` naming
the end date.

### "A variation is stuck, nobody can approve it"

```sql
SELECT variation_reference, status, escalation_required, escalation_reason
  FROM facilities.construction_variations WHERE project_id = '<id>';
```

`escalation_required = true` means an ordinary `FACILITIES_VARIATION_APPROVE` decision is refused with
`VARIATION_ESCALATION_REQUIRED` - it needs `FACILITIES_VARIATION_ESCALATED_APPROVE` via
`PATCH .../variations/{id}/escalated-approval`. If a different variation on the same project is also
held, the escalated approver must decide the **earlier** one first (by `submitted_at`) - the refusal
names it.

## Replay and repair

- **A missed event** (any `sfl.ifimp.project-*`, `contractor-site-access-*`, `project-variation-*`,
  `project-defect-raised`, `project-closed`): use [`dead-letter-recovery.md`](dead-letter-recovery.md)
  against `facilities.outbox_messages` filtered on `aggregate_type` in `ConstructionProject`,
  `VariationOrder`, `SiteAccessGrant`, `DefectItem`. The row is transactional with the state change
  that caused it - if the state changed, the row exists.
- **A stuck inbound permit event**: not applicable yet - nothing publishes `sfl.ssemp.permit-*` in this
  workspace. Once S164 ships, replay through the same inbound-inbox mechanism every other
  `IntegrationEventHandler` uses; `PermitEventHandler` is idempotent per `messageId` at the inbox layer
  and per `(permitId, occurredAt)` ordering in the projection itself, so a redelivery is harmless.
- **A milestone or baseline value that looks wrong**: never corrected in place -
  `construction_project_revisions` is append-only (a database trigger refuses UPDATE/DELETE on it).
  Submit a new revision with the corrected value and a reason; the old value stays in the history at
  `GET /api/v1/facilities/construction/projects/{id}/history`.

## What is simulated or not integrated - the honest list

Mirrors `GET /api/v1/facilities/construction/dashboard/integrations`, kept here for anyone reading
offline:

| Dependency | Direction | Status |
|---|---|---|
| S152 CAFM/IWMS | outbound | In-process call, real - handover writes the register directly. |
| S153 CMMS | outbound | In-process call, real - defects raise real work orders. |
| S158 Space Planning | both | Contract only - S158 is built in a separate worktree; scenario confirmations are UNRESOLVED until it is merged. |
| S164 Permit-to-Work (SSEMP) | inbound | Not published - S164 is not built; permit-requiring projects fail closed. |
| S160a Access Control / S160 Visitor Management (SSEMP) | outbound | No consumer - recorded and published, nothing enforced at the door. |
| S208 SIEM | outbound | Recorded, not forwarded - the shared recorded forwarder logs and reports `forwarded=false`. |
| S133 Vendor Master | inbound | Not integrated - contractor vendor references are held by value, unverified. |
| S136 CLM / S141 Payments | outbound | Out of scope this pass - payment certification is not built. |
| S223 Master Data Management | inbound | Not integrated - funding sources are held by reference, unresolved. |
| S140 HRMS | inbound | Not integrated - contractor competency records are entered by hand. |

## RLS notes

Every S176 table carries `site_code NOT NULL` and is covered by `facilities.apply_site_scope_policies()`
(V21's last statement) - `Phase2RowLevelSecurityCoverageTest` fails the build if a table is missed.
Proved against the `sfl_app` role specifically (not the schema owner, which bypasses RLS) in
`ConstructionRowLevelSecurityTest`: an unscoped session sees no construction project, a scoped session
sees only its own site, and a write to a site outside scope is refused at `WITH CHECK` with SQLSTATE
`42501`, not silently dropped.

## Migration and databases

Single migration `V21__construction_projects.sql`: fourteen tables, three sequences
(`construction_project_reference_seq`, `construction_variation_reference_seq`,
`construction_defect_reference_seq`), and the append-only trigger on `construction_project_revisions`.
Test databases: `sfl_facilities_s176_e2e` (port 55441) and `sfl_facilities_s176_migration_test`. If the
e2e database gets into a bad state:

```
docker exec sfl-facilities-e2e-postgres psql -U sfl -d sfl_facilities_s176_e2e \
  -c "DROP SCHEMA IF EXISTS facilities CASCADE"
```

then restart the service so Flyway rebuilds it from `V1` - never hand-patch a S176 table in production;
every mutation goes through the application so RLS, audit and the outbox all fire together.
