# PROMPT 4 — Close the authorisation surface across every system

> Backend and frontend. Every finding below was verified against the working tree on 1 August 2026 by
> counting endpoints against checks and reading the services, not by reading the documentation.
>
> The 1 August permission pass fixed what a user is **offered** in the menu. It did not fix what the
> **services enforce**, and the audit that followed found one service with no authorisation at all.

---

## The audit, and it is worse than the menu problem was

| Service | Endpoints | `require()` calls | Per-record narrowing | RLS |
|---|---:|---:|---|---|
| `sfl-facilities-service` | 119 | 121 | 7 files | ✅ `V14` |
| `sfl-fleet-logistics-service` | 141 | 136 | 8 files | ❌ none |
| `sfl-emergency-notification-service` | 40 | 34 | 1 file | ❌ none |
| **`sfl-asset-visibility-service`** | **8** | **0** | **0 files** | ❌ none |
| `sfl-safety-security-service` | 1 | 0 | — | ❌ none |

Frontend action gating, by module:

| Module | Pages | Pages that gate an action |
|---|---:|---:|
| facilities | 24 | 10 |
| booking | 5 | 4 |
| fleet | 12 | 3 |
| **fuel** | 12 | **0** |
| **dispatch** | 10 | **0** |
| **emergency** | 9 | **1** |

---

## A — AVAMP has no authorisation of any kind ⛔

**Do this first.** `sfl-asset-visibility-service` contains **no `SflPermission`, no
`AuthorizationPolicy`, no role check anywhere**. Eight endpoints, zero checks. Any authenticated
caller — every one of the 22 seeded accounts, a driver included — can register an asset, read the
whole asset register and query assets by location, at any site.

A1 fixed this service's *identity* (it took the actor from a caller-supplied header and now resolves
the JWT subject). Nobody then asked what it did with that identity. The answer is nothing.

1. Add an `AssetVisibilityPermissionMatrix` following `FacilitiesPermissionMatrix` — the same shape,
   a `READ_ONLY` set unioned per role. Permissions exist already: `ASSETVIS_*` in `SflPermission`; if
   the set is thin, extend it rather than borrowing a facilities permission, because an AVAMP grant
   must not be satisfiable by an IFIMP role.
2. `require(...)` on all eight endpoints — register, list, by-id, by-location and the rest.
3. Site scope. The register is site-scoped in the domain and not enforced at the boundary.
4. **The controllers also break the platform envelope** — they return raw `AssetReference` and
   `List<AssetReference>` rather than `{data, error}`. Fix that in the same pass; a client written
   against the current shape is a client that breaks when this is corrected later.

> Everything else in this prompt is tightening. This one is a hole.

## B — The actor is not bound to any domain record

The general defect, of which the driver is one instance. **A session carries an actor id
(`kwame.driver`). Domain records carry their own identity — a `DriverProfileReference` id, a centre
code, a vendor firm. Nothing joins them**, so a service that wants to narrow "to you" has nothing to
narrow on.

Four instances, all already recorded somewhere as gaps:

| Who | Record | Consequence today |
|---|---|---|
| Driver | `DriverProfileReference` | Sees every trip and every fuel transaction at their site |
| Centre manager | centre / destination | Sees every consignment at the site — `SFL_Role_Portal_Gap_Report.md` |
| Vendor technician | vendor firm | Narrowed per *person* by assignment, not per firm — deliberate, leave it |
| Technician | work-order assignee | Already narrowed on `assignedTo` — leave it |

### B1. The binding

Migration **`V22__driver_principal_binding.sql`** (V21 is the fuel-card registry — check first).

- `principal_reference VARCHAR(160)`, **nullable**. Existing rows have none, and backfilling by
  guessing a username would be worse than leaving them unbound.
- Partial unique index on `(site_code, lower(principal_reference)) WHERE principal_reference IS NOT NULL`,
  following the `(site_code, upper(staff_reference))` index already in `V4__fleet_driver_register.sql`.
  Two records claiming one principal is the failure that silently shows a driver another's trips.
- `VARCHAR(n)` with a length `CHECK`, never `CHAR(n)` — Hibernate rejects the latter at validation.
- Settable only under `FLEET_DRIVER_MANAGE`. A driver binding themselves defeats the point.

**Decision for the owner, and build it fail-closed:** a driver-only actor with *no* bound record sees
**no** trips, with a notice saying the account is not linked. The alternative — falling back to site
scope — means a missing binding leaks quietly, which is the exact class of defect this repository
keeps finding. An empty list with a reason is diagnosable; a full list is not.

### B2. Narrow trips, on reads **and** writes

`TripRepository.TripSearchCriteria` **already carries a `driverId`** — it is optional and
caller-supplied, so a driver simply omits it. This is not a missing filter; it is one that must
become forced.

Extend `FuelAccessPolicy`'s existing pattern rather than inventing a second: it already has
`isDriverOnly(actor)` and `requireOwnRecord(...)`. In `FleetAccessPolicy` add `driverFilter(actor)`
and `requireOwnTrip(actor, trip)`, then:

- `TripQueryService.search` — **override** `criteria.driverId()` with the filter, do not merge. A
  driver passing somebody else's id must get their own trips, not an empty list and not theirs.
- `TripQueryService.trip(id, actor)` and every transition — `requireOwnTrip`.

> **Both halves, or neither is real.** Narrowing the list and not the by-id read means a driver reads
> a colleague's trip by pasting a UUID. That is precisely the defect A0 found in fuel, and the reason
> `requireOwnRecord` exists.

### B3. Fuel transactions

`FuelApplicationService` narrows logbooks on `created_by` and deliberately does not narrow
transactions, because there was no driver value to narrow on. With B1 there is. Apply the filter,
then **delete the caption that says otherwise** — `DriverDayPage` reads *"Not filtered to you…"* — and
update `S168_Fuel_UI_Gap_Report.md`. An honest caption left in place after it becomes false is worse
than never writing it.

### B4. Confirm or defer an assignment

A driver holds `FLEET_TRIP_READ` and nothing that writes.

- Add `FLEET_TRIP_ACKNOWLEDGE`; grant to `FLEET_DRIVER` and the trip-managing roles.
- **Do not widen `FLEET_TRIP_MANAGE`.** Acknowledging the trip you were given and planning trips for
  other people are different authorities; collapsing them hands every driver the planner.
- On an `ASSIGNED` trip, their own only: **confirm** (who, when) or **defer** (who, when, and a
  **required** reason — a deferral with no reason cannot be told from one nobody has looked at).
- **Do not add a `TripStatus`.** The set is `PLANNED → ASSIGNED → IN_PROGRESS → ON_HOLD → COMPLETED →
  CANCELLED` with enforced transitions. An acknowledgement is a fact *about* an assignment, not a
  state of the trip — the call S159 already made for the readiness hold.
- Audit through the hash chain; event names must pass the outbox regex
  `^sfl\.[a-z0-9]+\.[a-z0-9-]+\.v(\d+)$`, enforced at the write path since A4.

## C — Endpoints with no check

Counting says fleet has ~5 endpoints without a `require()` and emergency ~6. Some are legitimate:
`ProviderCallbackService` is an inbound provider callback guarded by HMAC, allowlist and schema
validation at the integration inbox rather than by a role, which is correct and must stay that way.

Audit each one and classify it as **checked**, **deliberately unauthenticated with the guard named**,
or **a defect**. Write the classification down — an endpoint nobody can explain is the one that turns
out to be a hole.

## D — Row-level security everywhere else

ADR 0007 built RLS in facilities as the reference implementation and recorded the other three schemas
as owed. Apply the same migration shape to `sfl-fleet-logistics-service`,
`sfl-emergency-notification-service` and `sfl-asset-visibility-service`:

- A `sfl_app` role separate from the table owner, so the owner keeps its bypass and Flyway migrations
  still backfill. **This is the part that is easy to get wrong**: `FORCE` would apply the policies to
  migrations, and a backfill that silently writes nothing is worse than the problem.
- `SET LOCAL app.site_scopes` per transaction via the shared `SiteScopeGuc`, already in the kernel.
- Policies **fail closed** when the setting is unset. A second layer that opens up when the first
  forgets to speak is not a second layer.
- Exempt the audit chain — a tamper-evident record that is invisible in parts replays as a break that
  is really a filter — and runtime configuration, which is read for sites the actor may not hold.
- Test as `sfl_app` on its own connection. A test running as the owner passes while proving nothing,
  which is why `FacilitiesRowLevelSecurityTest` opens its own.

## E — Frontend action gating for fuel, dispatch and emergency

Fuel and dispatch gate **nothing**: 22 pages between them, every create/void/import/confirm control
offered to anyone who can open the page. Emergency gates one page of nine — and it is the module
where the ungated control sends a mass notification.

`modules/fleet/api/access.ts` already carries helpers for all three FTLMP systems including dispatch;
emergency needs its own. Apply them, following the rule S153 paid for:

> **A permission denial hides the control. A state or data shortfall disables it with the reason.**

Highest value first, because these are the ones where pressing the button matters: emergency
activation send, break-glass send, all-clear, fuel transaction void, fuel policy edit, dispatch
custody and receipt confirmation.

## F — Pin it

`navigationPermissions.test.ts` pins the driver's menu. Extend the same approach to actions and to
the other narrow roles — vendor technician, requester, mailroom officer, centre manager — because a
menu test does not catch a create button, and those four are the roles a widening silently benefits.

---

## What "done" means

- [ ] AVAMP refuses an unpermitted caller on all eight endpoints and returns the `{data, error}` envelope
- [ ] A driver sees only their own trips — in the register **and** by direct URL to another trip's id
- [ ] An unbound driver-only actor sees no trips and is told why
- [ ] Confirm and defer work on their own assignment; defer refuses without a reason; both are refused
      with `FLEET_UNAUTHORIZED_SCOPE` on somebody else's
- [ ] Fuel transactions narrowed, and the caption saying they are not is gone
- [ ] Every endpoint in every service is classified: checked, deliberately open with the guard named,
      or fixed
- [ ] RLS in all four remaining schemas, each proved by a test connecting as `sfl_app`
- [ ] Fuel, dispatch and emergency gate their consequential actions
- [ ] **A fleet manager, facilities manager and emergency coordinator lose nothing** — verify
      explicitly. This is the half a narrowing usually breaks and nobody notices until go-live.
- [ ] **Run it against real PostgreSQL**, not only the suite. Testcontainers is skipped in this
      environment, so by hand — 135 green unit tests once coexisted with a service that could not start.
- [ ] Backend green with the three `SFL_*_TEST_DB_URL` variables set. Without them the e2e suites skip
      and the build still reports green.

## What not to touch

Do not widen `FLEET_TRIP_MANAGE`, do not add a `TripStatus`, do not build a second access policy
beside `FuelAccessPolicy`, and do not put a role check on the provider callback paths — they are
guarded by HMAC and allowlist at the inbox, and a role check there would break real ingest.

**No AI-attribution trailers in any commit, PR body or changelog entry.** The history was rewritten on
31 July 2026 to strip 21 of them; a second rewrite would invalidate every commit SHA again.
