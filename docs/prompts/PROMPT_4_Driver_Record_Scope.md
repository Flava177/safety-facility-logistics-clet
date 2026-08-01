# PROMPT 4 — Bind the actor to their driver record, and give a driver something to do

> Closes the gap left by the permission tightening of 1 August 2026. That pass fixed what a driver is
> *offered*; this one fixes what a driver is *shown* and what they can *do about it*.

Everything below was verified against the working tree, not inferred. Where a mechanism already
exists it is named, because the temptation on several of these items is to build a second one.

---

## The defect, stated once

**A trip carries `driverId` — a `DriverProfileReference` id. A session carries an actor id, like
`kwame.driver`. Nothing joins them.**

So `TripQueryService.search` has no value to narrow on, and every driver sees every trip at their
site. The same root cause leaves fuel transactions unnarrowed, which is already recorded in
`S168_Fuel_UI_Gap_Report.md`.

This is not a missing filter. `TripRepository.TripSearchCriteria` **already has a `driverId` field**
— it is optional and caller-supplied, so a driver can simply not send it. The work is to make it
*forced* for a driver-only actor, which requires knowing which driver record they are.

---

## 1. Bind the actor to the driver record

Add a principal binding to `driver_profile_references`. Migration **`V22__driver_principal_binding.sql`**
(V21 is the fuel-card registry; check before writing).

- Column `principal_reference VARCHAR(160)`, **nullable**. Existing rows have none and backfilling
  them by guessing a username would be worse than leaving them unbound.
- Partial unique index on `(site_code, lower(principal_reference)) WHERE principal_reference IS NOT NULL`.
  Follow the shape already in `V4__fleet_driver_register.sql`, which indexes
  `(site_code, upper(staff_reference))` — one person is one driver record per site, and two records
  claiming the same principal is the failure that would silently show one driver another's trips.
- `VARCHAR(n)` with a length `CHECK`, never `CHAR(n)` — Hibernate rejects the latter at validation.
  Alter and backfill rather than drop and recreate.

Expose it on `DriverProfileReference`, on the register API, and on the create/update requests so an
administrator can set it. **A driver must not be able to set their own** — it is `FLEET_DRIVER_MANAGE`,
the same grant that creates the record.

### The decision this forces, and it belongs to the owner

An unbound driver record cannot be narrowed to. Two defensible answers:

- **Fail closed** — a driver-only actor with no bound record sees *no* trips. Safe, and it turns a
  missing binding into a support call on day one.
- **Fail open to site scope** — they see the site's trips, as today. Kinder, and it means an
  unbound record leaks quietly, which is exactly the class of defect this repository keeps finding.

**Build fail-closed** and put the reason in the code. An empty trip list with a notice saying "your
account is not linked to a driver record — ask the fleet office" is a diagnosable state; a full list
is not.

## 2. Narrow trips, on reads *and* writes

Follow `FuelAccessPolicy` exactly rather than inventing a second pattern. It already has
`isDriverOnly(actor)` — `FLEET_DRIVER` and none of `FLEET_MANAGER`, `FLEET_LOGISTICS_OFFICER`,
`SFL_ADMIN` — and `requireOwnRecord(...)`, which A0 added after finding a narrowing that applied to
one and not the other.

In `FleetAccessPolicy`:

- `driverFilter(actor)` → the bound `DriverProfileReference` id for a driver-only actor, else `null`.
- `requireOwnTrip(actor, trip)` → refuse when the actor is driver-only and `trip.driverId()` is not
  theirs.

Then:

- `TripQueryService.search` — override `criteria.driverId()` with `driverFilter` when non-null.
  **Override, do not merge**: a driver passing somebody else's `driverId` in the query string must
  get their own trips, not an empty list and not the other driver's.
- `TripQueryService.trip(id, actor)` and every trip transition — call `requireOwnTrip`.

> **Both halves, or neither is real.** A narrowing applied to the list and not to the by-id read
> means a driver reads a colleague's trip by pasting a UUID. That is the exact defect A0 found in
> the fuel module and the reason `requireOwnRecord` exists.

## 3. Confirm or defer an assignment

A driver holds `FLEET_TRIP_READ` and nothing that writes, so this needs a new permission.

- Add `FLEET_TRIP_ACKNOWLEDGE` to `SflPermission`.
- Grant it to `FLEET_DRIVER` in `FleetPermissionMatrix`, plus the roles that already manage trips.
- **Do not** widen `FLEET_TRIP_MANAGE`. Acknowledging the trip you were given and planning trips for
  other people are different authorities, and collapsing them hands every driver the planner.

Behaviour, on an `ASSIGNED` trip and only the actor's own:

- **Confirm** — records who acknowledged and when. No reason required.
- **Defer** — records who, when, and a **required** reason. A deferral with no reason cannot be told
  from one nobody has looked at, which is the same distinction the S159 setup task draws between
  `SKIPPED` and `PENDING`.

**Do not add a status.** `TripStatus` is `PLANNED → ASSIGNED → IN_PROGRESS → ON_HOLD → COMPLETED →
CANCELLED` and its transitions are already enforced. An acknowledgement is a fact *about* an
assignment, not a state of the trip — the same call S159 made for the readiness hold, which is a
column beside the status rather than a status. A deferral does not un-assign the trip; it flags it
for the fleet office, who decide whether to reassign.

Audit both through the existing hash-chained trail, and publish the event names through
`FleetEventType` so they pass the outbox regex — `^sfl\.[a-z0-9]+\.[a-z0-9-]+\.v(\d+)$`, enforced at
the write path since A4.

## 4. The same narrowing for fuel transactions

`FuelApplicationService` narrows logbooks on `created_by` in SQL and deliberately does not narrow
transactions, because a transaction has no driver column it could narrow on. With a bound driver
record it does: a transaction carries a `driverId`.

Apply `driverFilter` to the transaction query, then **delete the caption that says otherwise** —
`DriverDayPage` currently reads *"Not filtered to you. The service does not narrow fuel transactions
per driver, so this is every fill recorded at CLET-HQ."* Leaving an honest caption in place once it
has become false is worse than never having written it. Update `S168_Fuel_UI_Gap_Report.md` in the
same pass.

## 5. The driver's day, rebuilt around what they now have

`DriverDayPage` shows logbooks and fuel. Add the assignments, because they are the reason a driver
opens the application.

- **Needs your confirmation** — `ASSIGNED`, unacknowledged. The reason the page exists.
- **Today** — confirmed and in progress.
- Confirm and Defer on each, gated on `FLEET_TRIP_ACKNOWLEDGE` and hidden without it, following the
  rule S153 paid for: a permission denial hides the control, a state or data shortfall disables it
  with the reason.

Leave `TripQueuePage` alone. It now returns only the driver's trips for a driver-only actor, and that
is the whole point — one register, narrowed per actor, rather than a second screen that has to be
kept in step.

---

## What "done" means

- [ ] A driver signed in as `driver@clet.gh` sees **only** trips assigned to their bound record — in
      the register *and* by direct URL to another trip's id.
- [ ] An unbound driver-only actor sees no trips and is told why.
- [ ] Confirm and Defer work on their own assignment; Defer refuses without a reason.
- [ ] Both are refused with `FLEET_UNAUTHORIZED_SCOPE` on somebody else's trip.
- [ ] A fleet manager's view is unchanged — verify explicitly, this is the half a narrowing usually
      breaks.
- [ ] Fuel transactions narrowed, and the caption saying they are not is gone.
- [ ] **Run it against real PostgreSQL**, not only the suite. Testcontainers is skipped in this
      environment, so this is by hand — and 135 green unit tests once coexisted with a service that
      could not start.
- [ ] Backend suite green with the `SFL_*_TEST_DB_URL` variables set, so the e2e suites actually run.
      Without them they skip and the build still reports green.

## What not to touch

Do not widen `FLEET_TRIP_MANAGE`, do not add a `TripStatus`, and do not build a second access policy
beside `FuelAccessPolicy` — extend the pattern that is there.

**No AI-attribution trailers in any commit, PR body or changelog entry.** The history was rewritten
on 31 July 2026 to strip 21 of them; a second rewrite would invalidate every commit SHA again.
