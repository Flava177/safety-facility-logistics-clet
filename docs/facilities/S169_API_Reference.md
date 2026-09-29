# S169 Cleaning & Janitorial Schedule Management - API reference

Base path `/api/v1/facilities/cleaning`, on `sfl-facilities-service` (port 8091). Every response is the
platform envelope `{data, error}`; every error carries an SRS-worded code and a correlation ID.

Actor headers (`X-SFL-User`, `X-SFL-Roles`, `X-SFL-Sites`, `X-SFL-Source-Channel`) are resolved by
`FacilitiesActorResolver` while `sfl.security.enabled=false`; in production the same `ActorContext`
comes from the OIDC principal.

`Idempotency-Key` is honoured on the two POSTs that raise a task (`/tasks`, `/requests`) and nowhere
else - every other write is a PATCH guarded by the record's own version and state machine.
`expectedVersion` on a PATCH body is optional; supplying it turns a lost update into `VERSION_CONFLICT`,
omitting it accepts last-write-wins.

**Per-record narrowing**, on top of the permission and site checks below: a `VENDOR_TECHNICIAN` sees
and acts only on tasks assigned to them; an actor with no `FACILITIES_CLEANING_READ` (an
`IFIMP_REQUESTER`, typically) sees only the reactive requests they themselves raised. This applies to
every read and write below that names a task, not only the list endpoints.

---

## Schedules - SRS-SFL-S169-01

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/schedules` | `FACILITIES_CLEANING_SCHEDULE_MANAGE` | By site, space type (optionally one room) and frequency |
| `PATCH` | `/schedules/{scheduleId}` | `FACILITIES_CLEANING_SCHEDULE_MANAGE` | Replaces the rota; tasks already generated are untouched |
| `GET` | `/schedules` | `FACILITIES_CLEANING_READ` | `siteCode` |
| `POST` | `/schedules/generate` | `FACILITIES_CLEANING_SCHEDULE_MANAGE` (site-scoped call) | On-demand sweep. `siteCode` optional - omit for every active schedule, matching the timer. Idempotent |

## Checklist templates - SRS-SFL-S169-02

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/checklist-templates` | `FACILITIES_CLEANING_SCHEDULE_MANAGE` | A new version supersedes the site's active template for that space type |
| `GET` | `/checklist-templates` | `FACILITIES_CLEANING_READ` | `siteCode`. Every version, newest first |

## Tasks - SRS-SFL-S169-01, -02

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/tasks` | `FACILITIES_CLEANING_TASK_SUPERVISE` | **Idempotent.** Ad-hoc, or a booking setup/teardown task the automatic path did not raise - `bookingId` must resolve in S159 or the request is refused (`CLEANING_BOOKING_UNLINKED`) |
| `POST` | `/requests` | `FACILITIES_CLEANING_REQUEST` | **Idempotent.** A reactive request, by any occupant. `bookingId` optional; if given, must resolve |
| `PATCH` | `/tasks/{taskId}/assignment` | `FACILITIES_CLEANING_TASK_SUPERVISE` | To in-house staff or a registered, active vendor |
| `PATCH` | `/tasks/{taskId}/start` | `FACILITIES_CLEANING_TASK_EXECUTE` (or `_SUPERVISE`) | Stops the vendor SLA response clock |
| `PATCH` | `/tasks/{taskId}/checklist/{itemId}` | `FACILITIES_CLEANING_TASK_EXECUTE` (or `_SUPERVISE`) | Marks one item done/not done; photo evidence by reference + SHA-256 hash, never bytes |
| `PATCH` | `/tasks/{taskId}/completion` | `FACILITIES_CLEANING_TASK_EXECUTE` (or `_SUPERVISE`) | Refused with `CLEANING_CHECKLIST_INCOMPLETE`, naming every blocking item, while any required item is unaddressed |
| `PATCH` | `/tasks/{taskId}/cancellation` | `FACILITIES_CLEANING_TASK_SUPERVISE`, or the occupant on their own unassigned reactive request | Reason required |
| `GET` | `/tasks/{taskId}` | (narrowed - see above) | |
| `GET` | `/tasks/{taskId}/checklist` | (narrowed) | |
| `GET` | `/tasks` | (narrowed) | `siteCode`, `roomId`, `status`, `origin`, `requestedBy`, `assignedTo`, `bookingId`, `from`, `to`, `page`, `size` |

## Feedback and low-rating review - SRS-SFL-S169-02

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/tasks/{taskId}/feedback` | `FACILITIES_CLEANING_FEEDBACK_SUBMIT` | Rating 1-5, optional comment. Completed tasks only; not the assignee or whoever completed it |
| `GET` | `/tasks/{taskId}/feedback` | (narrowed to the task) | |
| `GET` | `/low-rating-flags` | `FACILITIES_CLEANING_TASK_SUPERVISE` | `siteCode`, `open` |
| `PATCH` | `/low-rating-flags/{flagId}/review` | `FACILITIES_CLEANING_TASK_SUPERVISE` | Notes required |

## Vendors, SLA terms and scorecards - SRS-SFL-S169-03

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/vendor-master-references` | `FACILITIES_CLEANING_VENDOR_MANAGE` | Records a reference as known to Vendor Master (S133) - S133 is not integrated, see the gap report. Idempotent on (site, reference) |
| `GET` | `/vendor-master-references` | `FACILITIES_CLEANING_VENDOR_MANAGE` | `siteCode` |
| `POST` | `/vendors` | `FACILITIES_CLEANING_VENDOR_MANAGE` | Resolves against a recorded reference or `CLEANING_VENDOR_NOT_FOUND` |
| `PATCH` | `/vendors/{vendorId}/status` | `FACILITIES_CLEANING_VENDOR_MANAGE` | `ACTIVE` / `SUSPENDED` |
| `GET` | `/vendors` | `FACILITIES_CLEANING_READ` | `siteCode` |
| `POST` | `/vendors/{vendorId}/sla-terms` | `FACILITIES_CLEANING_VENDOR_MANAGE` | A new version closes the one in force |
| `GET` | `/vendors/{vendorId}/sla-terms` | `FACILITIES_CLEANING_READ` | Full history, oldest first |
| `GET` | `/vendors/{vendorId}/scorecard` | `FACILITIES_CLEANING_READ` | `from`, `to` (default: the last 90 days). Every figure computed, never self-reported |

## Capacity - SRS-SFL-S169-04

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/capacity` | `FACILITIES_CLEANING_READ` | `siteCode`, `from`, `to`. Configured crews, peak concurrent commitments, every live commitment named |

The write side - `reserve` / `find` / `release` - is not REST. S173 and S169 share a deployable in this
build, so S173 calls the `EventCleaningCapacity` contract (`cleaning.application.contract`) in-process,
the same way any two modules in one service call each other.

## Dashboard - SRS §3.1 (S169 Dashboard attribute)

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/dashboard` | `FACILITIES_CLEANING_READ` | `siteCode` (omit for every site the caller can see), `from`, `to` (default: last 30 days). Scheduled vs completed, overdue reactive requests, vendor SLA compliance, feedback trend |
