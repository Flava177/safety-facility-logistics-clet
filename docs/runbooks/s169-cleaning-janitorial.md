# Runbook - S169 Cleaning & Janitorial Schedule Management

**Scope.** Routine cleaning schedules, checklist execution with photo evidence, occupant feedback,
vendor SLA tracking, and the cleaning-capacity feed to S173. Lives entirely inside
`sfl-facilities-service`, package `gh.edu.clet.sfl.facilities.cleaning`, schema `facilities`, tables
prefixed `cleaning_*` (migration `V19__cleaning_janitorial.sql`). For the service's general health
checks (is it up, is the database reachable, is the drainer running) see
[`incident-response.md`](incident-response.md) first - this runbook covers only what is specific to
S169.

## What this module owns

- **Routine schedules** (`cleaning_schedules`): by site, space type (optionally one room) and
  frequency. A background sweep materialises tasks a configured horizon ahead.
- **Checklist templates** (`cleaning_checklist_templates` / `_items`): versioned per site and space
  type, a configurable photo-required subset per item.
- **Cleaning tasks** (`cleaning_tasks` / `cleaning_task_checklist_items`): the unit everything else
  hangs off - routine, booking setup/teardown, reactive, event (S173) or ad-hoc.
- **Occupant feedback** (`cleaning_feedback`) and the **low-rating review queue**
  (`cleaning_low_rating_flags`).
- **Cleaning vendors** (`cleaning_vendors`), their **SLA terms** (`cleaning_vendor_sla_terms`,
  versioned) and their **computed breaches** (`cleaning_sla_breaches`).
- **The Vendor Master stand-in** (`cleaning_vendor_master_references`) - see "What is simulated" below.
- **S173 capacity reservations** (`cleaning_capacity_reservations`) - the write side of the
  `EventCleaningCapacity` contract this module provides to S173, called in-process.

## Health checks

```
GET /actuator/health              # the service is up at all
GET /api/v1/facilities/cleaning/dashboard?siteCode=<SITE>
GET /api/v1/facilities/cleaning/capacity?siteCode=<SITE>
```

A 200 from the dashboard with a populated `sites` array means the module, its database tables and its
authorisation wiring are all working end to end for at least one caller.

```sql
-- Tasks by status at a site - the fastest "is anything stuck" check.
SELECT status, origin, count(*) FROM facilities.cleaning_tasks
 WHERE site_code = '<SITE>' GROUP BY status, origin ORDER BY status, origin;

-- Overdue reactive requests right now.
SELECT task_number, room_code, requested_at, due_by
  FROM facilities.cleaning_tasks
 WHERE site_code = '<SITE>' AND origin = 'REACTIVE' AND status IN ('OPEN','ASSIGNED','IN_PROGRESS')
   AND due_by < now()
 ORDER BY due_by;

-- Every open low-rating flag, oldest first.
SELECT subject_type, subject_label, low_rating_count, flagged_at
  FROM facilities.cleaning_low_rating_flags
 WHERE site_code = '<SITE>' AND status = 'OPEN' ORDER BY flagged_at;
```

## Scheduled jobs

Both run on platform threads (row-level security scope `*`), gated by
`sfl.cleaning.scheduling.enabled` (default `true`, mirrors `sfl.booking.scheduling.enabled` /
`sfl.maintenance.scheduling.enabled`).

| Job | Property (interval / initial delay) | Default | What it does |
|---|---|---|---|
| Routine generation | `sfl.cleaning.generation.interval-ms` / `sfl.cleaning.generation.initial-delay-ms` | `3600000` (1h) / `180000` (3m) | Materialises routine tasks from now to each schedule's horizon. Idempotent - a second run in the same window creates nothing (`ux_cleaning_tasks_schedule_occurrence`). |
| SLA response sweep | `sfl.cleaning.sla.interval-ms` / `sfl.cleaning.sla.initial-delay-ms` | `300000` (5m) / `240000` (4m) | Records a response breach on any reactive vendor task still unstarted past its contracted response time - without waiting for somebody to start it. |

Both swallow and log a failure rather than let it cancel the schedule (`fixedDelay` semantics: an
uncaught exception stops all future runs for the life of the process). A stuck sweep shows as: routine
tasks stop appearing for new schedules/rooms, or response breaches stop appearing despite overdue
unattended reactive vendor requests. Check the log at `INFO` for
`gh.edu.clet.sfl.facilities.cleaning.infrastructure.scheduling.CleaningScheduledJobs`; an `ERROR` line
there names which sweep failed and says it will retry on the next tick regardless.

## Runtime configuration (Configuration Without Code)

Every key is seeded by `V19` with a platform default (`site_code IS NULL`); a site-scoped row
overrides it. List active values: `GET /api/v1/facilities/config?siteCode=<SITE>` (the shared S152
configuration endpoint - not owned by this module, but this is where these keys are read and written).

| Key | Default | What it controls |
|---|---|---|
| `cleaning.schedule.horizon-days` | `7` | How far ahead the generation sweep materialises tasks. |
| `cleaning.schedule.time-zone` | `Africa/Accra` | Zone a schedule's times of day are interpreted in. |
| `cleaning.booking.task-minutes` | `30` | Length of a booking setup/teardown clean when the booking's own buffer is shorter. |
| `cleaning.reactive.target` | `PT4H` | How long after it is raised a reactive request is due. |
| `cleaning.capacity.crews` | `2` | Crews available at once at the site - what S173 capacity is measured against. |
| `cleaning.feedback.low-rating-max` | `2` | A rating at or below this counts as low. |
| `cleaning.feedback.low-rating-repeat-count` | `3` | Low ratings for the same space or vendor, within the window, that raise a flag. |
| `cleaning.feedback.low-rating-window` | `P30D` | The window repeated low ratings are counted over. |
| `cleaning.sla.discrepancy-tolerance` | `PT5M` | How far a vendor-reported completion may sit from the recorded one before it is logged as a discrepancy. |
| `cleaning.sweep.batch` | `500` | Rows the SLA response sweep processes per run. |

A threshold changed here applies to the *next* evaluation with no restart - `CleaningConfiguration`
reads every value fresh, never caches across a call.

## Failure modes and what to do

**A booking-triggered task never appeared.** Check the booking's own status first
(`GET /api/v1/facilities/bookings/{id}`) - a task is raised only on **confirm**, not on request, and
only if `cleaningRequirement` is not `NONE`. If the booking is confirmed and asked for cleaning:

```sql
SELECT * FROM facilities.cleaning_tasks WHERE booking_id = '<booking id>';
```

Nothing there and no `CLEANING_TASK_CREATED` audit record for that booking means the observer call
itself failed inside the booking's own transaction - which would have failed the *booking* confirmation
too (see `BookingLifecycleObserver`'s Javadoc: this is deliberate, a loud, retryable refusal rather
than a silent gap). If the booking confirmed successfully but the task is missing, that is a defect;
check the service log around the confirmation time for a `CLEANING_BOOKING_TASK_REJECTED` audit entry
first - a task claiming a booking S159 cannot resolve is refused rather than silently dropped, and the
audit record on the chain says why (see `CLEANING_BOOKING_UNLINKED` below).

**`CLEANING_BOOKING_UNLINKED` / `CLEANING_BOOKING_TASK_REJECTED` in the audit log.** A task tried to
claim a booking reference S159 could not resolve - no such booking, one outside the caller's site
scope, or one that no longer holds its space (cancelled, rejected, completed). Find the reason in the
audit record's payload (`reason` field):

```sql
SELECT occurred_at, resource_id, site_scope, after
  FROM facilities.facility_audit_records
 WHERE action = 'CLEANING_BOOKING_TASK_REJECTED'
 ORDER BY occurred_at DESC LIMIT 20;
```

**A task will not complete - `CLEANING_CHECKLIST_INCOMPLETE`.** The error message and the
`CLEANING_TASK_COMPLETION_REFUSED` audit record both name every blocking item. Check what is still
open:

```sql
SELECT item_code, label, done, photo_required, photo_reference
  FROM facilities.cleaning_task_checklist_items
 WHERE task_id = '<task id>' ORDER BY sequence_no;
```

A row with `photo_required = true`, `done = true` and `photo_reference IS NULL` is the common case: the
cleaner ticked the item but the photo upload from the field device never reached
`PATCH /tasks/{id}/checklist/{itemId}`. There is no server-side recovery for a lost photo - it has to
be retaken and resubmitted; this module never had the original bytes to fall back to (see "What is not
integrated" below).

**A vendor task's SLA looks wrong.** Every number on a scorecard is computed from the task's own
`requested_at` / `started_at` / `completed_at` and from occupant ratings - never from
`vendor_reported_completed_at`. If a vendor disputes a breach, read the breach's `basis` column, which
states in words which timestamps or rating produced it:

```sql
SELECT breach_type, contracted_value, actual_value, basis, recorded_at
  FROM facilities.cleaning_sla_breaches WHERE task_id = '<task id>';
```

A logged discrepancy (`CLEANING_COMPLETION_DISCREPANCY_RECORDED` in the audit chain, and
`completion_discrepancy_seconds` on the task) means the vendor's own claim disagreed with the recorded
completion by more than `cleaning.sla.discrepancy-tolerance`. That is evidence *for* the scorecard, not
a reason to trust the vendor's number instead.

**A capacity reservation from S173 keeps returning CONFLICT.** The conflict names the competing
commitment (`competing_commitment` on the reservation row, and in the `sfl.ifimp.cleaning-capacity-
conflict.v1` event payload). Look the named task up directly and either reschedule it, raise
`cleaning.capacity.crews` for the site if the site is genuinely short-staffed, or have S173 ask for a
different slot:

```sql
SELECT id, status, event_reference, window_from, window_to, competing_commitment
  FROM facilities.cleaning_capacity_reservations
 WHERE site_code = '<SITE>' ORDER BY created_at DESC LIMIT 20;
```

**A vendor cannot be registered - `CLEANING_VENDOR_NOT_FOUND`.** The reference has not been recorded as
known to Vendor Master (S133) yet - see "What is not integrated" below. An authorised user records it
first:

```
POST /api/v1/facilities/cleaning/vendor-master-references
{"siteCode": "<SITE>", "reference": "<S133 reference>", "legalName": "...", "evidenceNote": "..."}
```

then `POST /vendors` with the same reference.

## Replay and repair

- **A missed routine occurrence.** Run generation on demand rather than waiting for the timer:
  `POST /api/v1/facilities/cleaning/schedules/generate?siteCode=<SITE>` (or with no `siteCode` for
  every active schedule, matching the sweep). Idempotent - safe to call repeatedly.
- **A cancelled task that should not have been.** There is no "un-cancel". Raise a fresh ad-hoc task
  (`POST /tasks`) or, for a booking-origin one, let the next booking lifecycle event (confirm,
  reschedule) raise it again - `bookingConfirmed` raises a setup/teardown task only when a live one does
  not already exist, so a cancelled one is filled in on the next lifecycle call that touches the
  booking. There is currently no endpoint that re-triggers it without a booking event; escalate to raise
  the ad-hoc task by hand if the booking will not change state again before the event.
- **A stuck capacity reservation.** `POST` is not exposed over REST (S173 calls the
  `EventCleaningCapacity` contract in-process); a reservation orphaned by a coordinator crash on the
  S173 side is released the same way any release is - `release(reservationId, reason)` - which S173
  itself must call. This module has no timeout that releases an unreleased reservation on its own; that
  is a gap, recorded in the gap report.

## What is simulated / not integrated

- **Vendor Master (S133) is not built.** `RecordedVendorMasterAdapter` resolves a vendor reference only
  against `facilities.cleaning_vendor_master_references`, a local table an authorised user maintains by
  hand through `POST /vendor-master-references`. Every scorecard and every known-reference listing
  carries `vendorMasterStatus`, stating this in words - never report S133 as integrated from this
  module's output.
- **Photo storage is references only.** `cleaning_task_checklist_items.photo_reference` and
  `.photo_content_hash` are a pointer and a SHA-256, never bytes. There is no object store behind this
  build; the reference is wherever the mobile checklist client's own storage puts it. If that storage is
  unreachable, the reference is meaningless and there is nothing here to recover it from.
- **The mobile checklist client does not exist.** Every field interaction in this runbook
  (marking an item done, attaching a photo, starting/completing a task) is an API call
  (`/api/v1/facilities/cleaning/**`); there is no purpose-built device UI in this build.
- **S173 (Event Logistics & Set-Up Workflow) is built in a sibling worktree at the same time as this
  one.** The write side of the capacity feed (`EventCleaningCapacity.reserve/find/release`) is a real
  in-process Spring bean this module provides; whether S173 actually calls it depends on that build
  landing with a matching consumer adapter. If `cleaning_capacity_reservations` stays empty in
  production, check that S173 is deployed and wired to this contract, not that S169 is broken.

## Row-level security

Every table carries `site_code NOT NULL` and is covered by `facilities.apply_site_scope_policies()`,
called at the end of `V19` (`Phase2RowLevelSecurityCoverageTest` fails the build otherwise). No S169
table is exempt. `CleaningRowLevelSecurityTest` proves, against the `sfl_app` role the application
actually connects as: an unscoped session sees no cleaning tasks, a scoped session sees only its own
site's, `*` sees across every site, and a write outside scope is refused with `SQLSTATE 42501`.

Per-record narrowing sits above RLS, in `CleaningSupport`: a `VENDOR_TECHNICIAN` sees and acts only on
tasks assigned to them; an `IFIMP_REQUESTER` (or anyone without `FACILITIES_CLEANING_READ`) sees only
the reactive requests they themselves raised. Both are enforced on every read and every write, not only
on the list endpoints - see `CleaningSupport.assertVisible`.
