# S153 CMMS — design

What was built, and the decisions that are not obvious from the code. The companion documents are
[S153_API_Reference.md](S153_API_Reference.md) and
[S153_Gap_And_Conflict_Report.md](S153_Gap_And_Conflict_Report.md).

- Service: `sfl-facilities-service`, module `gh.edu.clet.sfl.facilities.maintenance`
- Schema: `facilities` · Migration: `V9__cmms_platform_alignment.sql`
- Specification: `SRS-SFL-S153-01..05`, NFR 23.1, 23.3, 23.5, 23.8

## 1. This was a rewrite, not a greenfield build

`maintenance/` already existed. It was built before S152 and inherited none of the platform S152
established, and three things were wrong with it — all three are why this round happened rather than
being incidental to it:

| Defect | What it meant | Fixed by |
| --- | --- | --- |
| **No authorisation at all** on the fault path. `findAll()` had no permission check and no site filter | Any caller, with any role or none, got every fault at every site | `FacilityFaultService` authorises every command and filters every query |
| **A second actor model.** Controllers read `X-SFL-User` through `DevActorHeaderResolver` | Two actor models in one deployable, only one of which works with a JWT | Both controllers now resolve through `FacilitiesActorResolver` |
| **Outside the audit chain.** No fault or work-order state change was hash-chained | The integrity check verified a chain with holes where the maintenance work should be | 21 audit actions added; the chain verifies with maintenance in it |

The existing tables were **altered and backfilled**, not dropped. See §6.

## 2. The aggregates

| Aggregate | What it is | Notes |
| --- | --- | --- |
| `FacilityFault` | A reported problem | Carries `roomId` **and** `locationCode`: a fault can be against a corridor or a car park, which the estate model has no room for on purpose |
| `WorkOrder` | A unit of work | `facilityFaultId` is nullable — a preventive order answers no fault, which is why the old model had nowhere for preventive maintenance |
| `PreventiveMaintenanceSchedule` | A standing instruction to service an asset | Closes the loop S152 left open: the interval was on the asset and nothing acted on it |
| `MaintenanceVendor` | A local contractor reference | **Not** the procurement master. Carries `externalVendorId` as procurement's identifier |
| `MaintenanceEvidence` | A file reference and its hash | Never the bytes. Architecture standard: evidence by reference |
| `WorkOrderPart` | A part fitted | Not a stores system: no stock, no reorder, no reservation |

### The two state machines

**Fault** — `REPORTED → TRIAGED → WORK_ORDER_CREATED → RESOLVED`, with `REJECTED`, `DUPLICATE` and
`CANCELLED` as terminal dismissals. A dismissal always requires a reason; a duplicate must name what
it duplicates.

**Work order** — `OPEN → ASSIGNED → IN_PROGRESS ⇄ ON_HOLD → COMPLETED → CLOSED`, plus `CANCELLED`.

Three things about it are deliberate:

- **`ASSIGNED → ASSIGNED` is legal.** Reassignment is not a state; it is a change of owner that the
  audit trail records. A separate status would make an order reassigned twice look different from one
  reassigned once.
- **`COMPLETED` and `CLOSED` are separate.** The technician says the work is done; an authorised
  officer says it is accepted. Closure is where evidence is demanded and completion events publish.
- **Passing through `COMPLETED` is not mandatory.** Closure is reachable from any working state,
  because the gate is the closing permission and the evidence rule, not the route. It is also what the
  pre-S153 rows did, so they migrate meaning intact.

## 3. SLA and escalation — SRS-SFL-S153-02

The requirement asks for timers "from configurable priority, severity, site, operating mode and
workflow type rules", and for escalation "evaluated using the runtime configuration active at the
time of evaluation".

`SlaPolicy` is a value object — pure arithmetic on a deadline, no repository and no clock — so the
table for a site is built once per evaluation run and applied to a thousand work orders without a
thousand configuration reads. `MaintenanceConfiguration` reads it fresh on every call, which is what
makes "active at the time of evaluation" a fact rather than a hope about deployment timing.

| Key | Default | What it does |
| --- | --- | --- |
| `maintenance.sla.resolution.{critical,high,medium,low}` | 4h / 24h / 3d / 14d | The work order's deadline |
| `maintenance.sla.response.{…}` | 30m / 2h / 8h / 24h | Time to acknowledge. Carried for a later round |
| `maintenance.sla.examination-factor` | `0.5` | Multiplier while the site is in examination mode |
| `maintenance.escalation.interval` | 4h | Time between successive levels once overdue |
| `maintenance.escalation.max-level` | 3 | The ceiling |
| `maintenance.readiness.blocker-threshold` | `HIGH` | Priority at which a fault blocks its space |
| `maintenance.closure.evidence-threshold` | `HIGH` | Priority at which closure evidence is mandatory |
| `maintenance.closure.evidence-count` | 1 | How many items, above the threshold |
| `maintenance.preventive.generation-batch` | 200 | Schedules per generation run |

**Operating mode compresses rather than replaces.** A factor rather than a second table, so a site
that lengthens one priority's SLA cannot forget to lengthen its examination equivalent.

**Escalation is a ladder, not a flag.** Level 1 at the deadline, one more per interval, capped. A
boolean would notify once and go quiet on the item nobody picked up — the case escalation exists for.

**A vendor's contracted response time wins when it is tighter.** A six-hour contract beats a
fourteen-day low-priority rule; it never loosens one.

**The SLA is computed once and stored.** Recomputing on read would let a configuration change move
every open deadline, including ones already breached. The requirement's rule is about the escalation
ladder, not about rewriting deadlines already set.

**Nothing is notified from here.** The sweep publishes `ifimp.work-order.escalated` and
`ifimp.facility-fault.escalated` and stops. Building a notifier would be a second place for CLET's
escalation contact list to be wrong.

## 4. The readiness join — why S153 sits under S152

A fault is not only a ticket. If it affects a space, it affects whether that space can be used, and
before this round the two facts lived in different modules with nothing between them: an examination
hall could be flooded and still read as READY.

- A fault at or above `blocker-threshold`, in a known room, raises a readiness blocker on it.
- The severity mapping mirrors the asset one — priority in place of criticality — so a generator
  failure and a fault reported against that same generator cannot produce different severities for
  one physical problem.
- Every fault transition reconciles. Triage may raise the priority over the threshold; any dismissal
  or resolution takes it out of the open set.
- Closing the work order resolves the fault, which clears the blocker and re-derives the space.

### The dependency direction, and why the port is where it is

`ExternalBlockerPort` is declared **by readiness** and implemented by readiness; maintenance depends
on it. That is the opposite of S152's `SpaceReadinessPort`, which `masterdata` declares, and the
inversion is deliberate:

> Readiness is the deeper module. Whether a hall can be used is a fact about the estate, true whether
> or not anybody has raised a work order about it. Maintenance is one of several things that can
> change that fact, alongside assessments and asset failures.

If maintenance declared the port, readiness would import `maintenance.application.ports` to implement
it, and the arrow would point from the module that decides whether a hall is usable to the module
that tracks who is fixing it. Nothing crossing the boundary is a maintenance type — only primitives,
UUIDs and readiness's own `BlockerSeverity`. The ArchUnit rule
`readiness_does_not_depend_on_maintenance` holds the line.

## 5. Authorisation

Twelve new permissions in `SflPermission`, mapped in `FacilitiesPermissionMatrix`. Two grants are
decisions rather than defaults, and one is a narrowing the matrix cannot express.

**A technician does not hold `FACILITIES_WORK_ORDER_CLOSE`.** They mark work `COMPLETED`; a
supervisor accepts it. Giving them both would collapse the two states the SRS separates and let the
person who did the job be the only person who ever saw it — which is what closure evidence exists to
prevent. *(Found by a test: the technician could reopen their own completed work.)*

**`VENDOR_TECHNICIAN` is split from `IFIMP_TECHNICIAN`.** They shared a permission set before S153,
which meant a contractor could read the whole estate register, every fault at the site and every
asset's condition. Site scope is the wrong boundary for somebody who is not CLET staff.

**The real vendor boundary is assignment, enforced per record.** "The ones assigned to me" is a
property of the record, not of the role, so it lives in `WorkOrderApplicationService.assertVisible`
and is applied to reads *and* writes — a vendor who could not see an order but could transition it by
guessing its id would make the read-side narrowing decorative. Narrowing is by `assignedTo` matching
the actor, so a vendor firm sees per person, not per firm: the stricter reading, and the one to keep
until CLET says otherwise.

**A requester reads only the faults they reported**, by the same mechanism. Both narrowings apply
only when the role is the actor's *only* facilities role — treating a union of roles as its narrowest
member would make adding a role take capability away.

Every denial is audited as `AUTHORIZATION_DENIED`, including the per-record ones.

## 6. The migration

`V9` alters and backfills; it does not drop. Every column is added nullable, backfilled, then
constrained — a `NOT NULL` added in one step against a non-empty table fails at deploy time.

**Provenance is reconstructed from what the row had**: `created_by` from `reported_by`, `created_at`
from `reported_at`. Honest, and better than a synthetic `migration` actor, which would erase the only
actor known.

**Rooms are linked where the location code names one**, and left unlinked where it does not. A fault
against `CAR-PARK-B` has no room, and inventing one would put a readiness blocker on a space that
does not exist.

**Historic orders get `evidence_required = 0`.** Applying the rule retrospectively would make every
existing open row unclosable.

**Numbers moved from UUID fragments to sequences.** `FLT-CLET-HQ-000123` is sortable, sayable over a
radio, and unique without coordination; `FLT-1A2B3C4D` was none of those. Gaps after a rollback are
harmless, duplicates are not — hence a sequence rather than a row count.

Verified by building a database at V8, seeding rows in the old shape, and running V9 over them. See
the gap report §4.

## 7. Scheduling

`MaintenanceScheduledJobs`, enabled by `@EnableScheduling` and switchable with
`sfl.maintenance.scheduling.enabled=false`.

- **Escalation every 15 minutes.** The tightest default SLA is 30 minutes, so a quarter of that is
  fine-grained enough and coarse enough not to hammer a slow-moving table.
- **Preventive generation hourly**, not daily. A daily job has one chance to run, so a deploy across
  its window silently skips a day of preventive maintenance; hourly it catches up by itself.
- **Both catch `RuntimeException`.** An uncaught exception from a `fixedDelay` task cancels the
  schedule for the life of the process — one bad row would silently stop every future escalation, on
  the job whose whole purpose is to notice things.
- Both are idempotent, so the interval is a latency choice, not a correctness one, and two instances
  sweeping together is wasteful rather than wrong.

## 8. Tests

`mvn -pl sfl-facilities-service test` — **190 tests**, 12 skipped (Testcontainers cannot reach the
Docker named pipe in this environment).

`S153MandatoryScenariosTest` has 42, one per SRS acceptance criterion, grouped by requirement. The
clock is mutable rather than fixed: half of S153 is about time passing, and `Clock.fixed` cannot
express "four hours later the sweep runs".

Four design defects were found by writing them, and all four were real:

1. `IN_PROGRESS → CLOSED` was not allowed, so a supervisor doing a job themselves had to hand it to
   themselves first — and every pre-S153 row would have become unclosable.
2. A technician could reopen their own completed work, because they held `CLOSE`.
3. `resolve` and `dismiss` cleared `blockerRaised` before reconciliation read it, so a rejected fault
   left its blocker open forever — a hall nobody could book and nothing explained.
4. The sweep escalated a fault *and* its work order, notifying two people about one problem.
