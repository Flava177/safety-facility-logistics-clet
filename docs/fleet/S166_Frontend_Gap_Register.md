# S166 — Frontend Gap Register

Raised while building `frontend/sfl-operations-ui` against `sfl-fleet-logistics-service`.

Every item below was found by reading the controllers, request/response records and domain enums in
`services/sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fleet` and
comparing them with `docs/fleet/S166_API_Inventory.md`. No API contract in the UI was inferred from
documentation alone.

**Status key** — `DOC` documentation disagrees with the implementation · `MISSING` the UI needs an
endpoint that does not exist · `WORKAROUND` the UI ships a bounded alternative.

---

## 1. Dashboard and report paths differ from the inventory — `DOC`

| Inventory (`§7`)                                | Implemented (`FleetDashboardController`)             |
| ----------------------------------------------- | ---------------------------------------------------- |
| `GET /api/v1/fleet/dashboard`                   | `GET /api/v1/fleet/dashboards/operations`             |
| `GET /api/v1/fleet/dashboard/drilldown/{metric}`| `GET /api/v1/fleet/dashboards/operations/drilldowns/{indicator}` |
| `GET /api/v1/fleet/dashboard/readiness-report`  | `GET /api/v1/fleet/reports/go-live-readiness`         |
| `GET /api/v1/fleet/dashboard/compliance-report` | **not implemented**                                   |
| —                                               | `GET /api/v1/fleet/dashboards/operations/reconciliation` (undocumented) |

The inventory also lists a `reconcile` query parameter; the controller takes `requireFresh`.

**UI position:** the implemented paths are used. `docs/fleet/S166_API_Inventory.md §7` should be
corrected, or the controller aligned to the documented paths.

---

## 2. Vehicle readiness has no dedicated endpoint — `WORKAROUND`

The inventory lists `GET /api/v1/fleet/vehicles/{id}/readiness` (V6). `VehicleController` has no
such mapping. The only readiness assessment exposed is
`GET /api/v1/fleet/trips/assignment-preview?vehicleId=&driverId=&from=&to=&operatingMode=`.

**UI position:** the vehicle detail screen calls `assignment-preview` with only `vehicleId`. This is
the same policy the assignment itself runs, so the answer is correct — but readiness is reachable
only through a trip-shaped endpoint, which reads oddly for a vehicle-centric screen.

**Ask:** add `GET /vehicles/{id}/readiness`, or record that `assignment-preview` is the intended
single entry point and drop V6 from the inventory.

---

## 3. Vehicle movement / telematics history is not exposed — `MISSING`

Inventory V12 lists `GET /api/v1/fleet/vehicles/{id}/movement` (telematics history plus freshness
age, conflict C-10). There is no controller mapping, although `VehicleLocationRepository`,
`VehicleLocationSnapshotEntity` and `FleetIntegrationResponses.VehicleLocationResponse` all exist.

**UI position:** no movement panel is shown on the vehicle detail screen. The integration health
page states plainly that stale movement data is possible when dead letters exist.

---

## 4. Standalone (periodic) vehicle inspection cannot be recorded — `MISSING`

`FleetTripRequests.RecordStandaloneInspection` exists and is documented as
`POST /api/v1/fleet/vehicles/{id}/inspections`, but no controller maps it. Inspections can only be
recorded against a trip.

**UI position:** the "record inspection" action appears only on trip detail. `PERIODIC` and
`DEFECT_FOLLOW_UP` inspection types are selectable there, but a vehicle with no open trip cannot be
inspected at all.

**Ask:** this blocks the periodic-inspection part of SRS-SFL-S166-01. Either map the endpoint or
confirm periodic inspections are out of Phase 1 scope.

---

## 5. No evidence search endpoint — `WORKAROUND`

Inventory E2 lists `GET /api/v1/fleet/evidence` with filters. `FleetEvidenceController` exposes
registration, lookup by id, access recording, export request, export decision and export — but no
search.

**UI position:** Evidence & audit is built around lookup by reference ID, with the limitation stated
on the page. Closure dialogs therefore ask the operator to paste an evidence reference ID rather
than pick from a list, which is the main usability cost in the whole console.

**Ask:** add `GET /evidence` with at least `relatedRecordType` + `relatedRecordId` filters. That
alone would let trip and workflow closure dialogs offer a picker.

---

## 6. Evidence export paths differ from the inventory — `DOC`

| Inventory                                                       | Implemented                                                    |
| --------------------------------------------------------------- | -------------------------------------------------------------- |
| `PATCH /evidence/export-requests/{id}/approval`                 | `PATCH /evidence/export-requests/{id}/decision`                 |
| `GET /evidence/export-requests/{id}/download`                   | `POST /evidence/export-requests/{id}/export`                    |
| —                                                               | `POST /evidence/{id}/access` (undocumented; records an access entry) |

---

## 7. Audit paths differ from the inventory — `DOC`

| Inventory                              | Implemented                                  |
| -------------------------------------- | -------------------------------------------- |
| `GET /api/v1/fleet/audit`              | `GET /api/v1/fleet/audit/records`            |
| `GET /api/v1/fleet/audit/integrity-check` | `GET /api/v1/fleet/audit/chain/verification` |

`GET /audit/records` returns a bare `List<AuditEvent>` rather than the paged envelope every other
collection endpoint uses, and `AuditEvent` is a domain type rather than a response DTO — so the UI
types it defensively.

**Ask:** wrap audit search in `PageResponse` and add an `AuditEventResponse` DTO for consistency
with the "persistence entities are never exposed" rule stated in the inventory conventions.

---

## 8. Integration intake path and inbox search — `DOC` / `MISSING`

Inventory I1 lists `POST /api/v1/integrations/webhooks/telematics`; the implementation is
`POST /api/v1/fleet/integrations/{sourceSystem}/messages`. Inventory I3
(`GET /fleet/integrations/messages`) is not implemented.

**UI position:** the integration health page renders only the messages carried in the health
projection and says so. Replay works, but the operator must already hold the message identifier —
there is no way to find a dead-lettered message from the UI.

**Ask:** implement I3. Without it, dead-letter replay is not operable from the console.

---

## 9. Documented search filters that the controllers do not accept — `DOC`

| Endpoint            | Documented filter                   | Present in controller |
| ------------------- | ----------------------------------- | --------------------- |
| `GET /vehicles`     | `complianceExpiringBefore`          | no                    |
| `GET /workflow-items` | `severity`                        | no                    |
| `GET /drivers`      | `search` (free text)                | yes — undocumented    |
| `GET /vehicles`     | `sort`, `page`, `size`              | yes — undocumented in the filter list |

**UI position:** those two filters are absent from the register screens rather than being sent and
silently ignored.

---

## 10. Compliance is only reachable per vehicle — `WORKAROUND`

There is no cross-fleet compliance document search. The compliance screen therefore fans out over
the first 50 active vehicles in scope and labels that limit on the page. The authoritative
scope-wide counts come from the server-computed dashboard indicators and the
`EXPIRED_COMPLIANCE` drilldown.

**Ask:** a `GET /fleet/compliance-documents` search (site, status, `expiringBefore`) would remove
the fan-out and make the screen correct for fleets larger than 50 vehicles.

---

## 11. Drilldown indicators are a fixed set of four — noted

`FleetDashboardApplicationService.drilldown` recognises `EXPIRED_COMPLIANCE`, `SERVICE_DUE`,
`READINESS_BLOCKERS` and `ASSIGNMENT_CONFLICTS`; anything else returns an empty list rather than a
404.

**UI position:** only those four tiles are clickable. The other four indicators are rendered as
non-interactive, so the UI never implies a drilldown that would silently return nothing.

---

## 12. `RetentionClass` vs `EvidenceRetentionClass` — noted

Compliance documents take `RetentionClass` (`OPERATIONAL_SHORT`, `OPERATIONAL_STANDARD`,
`COMPLIANCE`, `INCIDENT`, `STATUTORY`, `LEGAL_HOLD`) while evidence records take
`EvidenceRetentionClass` (`OPERATIONAL_1_YEAR`, `COMPLIANCE_7_YEARS`, `INCIDENT_10_YEARS`,
`LEGAL_HOLD`). Two retention vocabularies with different periods coexist for the same governance
concern; `RetentionClass`'s Javadoc already flags the periods as an unconfirmed assumption
(gap report C-08).

**UI position:** each form offers its own enum. Worth resolving before go-live so an auditor is not
shown two different retention answers for the same evidence.

---

## Front-end limitations recorded honestly

- No mock data is shipped. `VITE_FLEET_DEV_FALLBACK` exists as a named switch but no screen uses a
  fallback — an endpoint that does not exist is documented above rather than faked.
- Vehicle and driver pickers in the trip dialogs load up to 200 records per site. Beyond that a
  server-side typeahead endpoint would be needed.
- `npm install`, `npm run lint` and `npm run build` were not executed in the environment this branch
  was authored in (its npm registry egress was blocked). The code was verified by TypeScript
  parse-checking every file, resolving every local import against real exports, checking for unused
  imports and formatting with Prettier. Run the three commands locally before merging.

---

# Driver logbook — contract review (28 July 2026)

Reviewed ahead of building a Driver Logbook module. **The Fleet & Logistics service exposes no
driver logbook API.** This is recorded here rather than worked around: nothing was mocked and no
screen was built.

## Evidence

The service's full source tree under
`services/sfl-fleet-logistics-service/src/main/java/.../fleet` contains:

- **Controllers** — `DriverController`, `TripController`, `VehicleController`,
  `FleetWorkflowController`, `FleetDashboardController`, `FleetEvidenceController`,
  `FleetAuditController`, `FleetIntegrationController`. There is no logbook controller.
- **Domain models** — no `LogbookEntry`, `DutyLog`, `Journey`, `Shift` or equivalent aggregate.
- **Application ports** — no logbook repository; the port list stops at trips, vehicles, drivers,
  inspections, compliance documents, service records, workflow items, evidence, audit, integration
  inbox, SLA rules and dashboard snapshots.
- **Persistence** — no logbook entity, JPA repository or adapter.

## What exists that a logbook would be assembled from

`Trip` is the journey record and already carries most of the raw material:

| Field | Source |
| --- | --- |
| `driverId`, `vehicleId`, `siteCode` | assignment |
| `origin`, `destination`, `purpose`, `operatingMode` | plan |
| `plannedStart` / `plannedEnd`, `actualStart` / `actualEnd` | plan and execution |
| `startOdometer`, `endOdometer`, `distanceCovered` | captured at start and closure; **distance is computed by the service** |
| `status`, `holdReason`, `cancellationReason`, `closureReason`, `closureEvidenceId` | lifecycle |

`GET /api/v1/fleet/trips` accepts `driverId`, `vehicleId`, `siteCode`, `status`, `operatingMode`,
`from`, `to`, `page`, `size` and `sort`, so a per-driver, per-period view **can** be assembled from
real, server-filtered records. `VehicleInspection` supplies pre- and post-trip checks with their own
odometer reading; `EvidenceReference` supplies attachments; the audit chain supplies the trail.

## What is missing for a logbook module as specified

1. **No logbook resource.** No `/drivers/{id}/logbook`, no logbook entry, no create or amend
   endpoint distinct from the trip lifecycle.
2. **No duty or shift concept.** Nothing models on-duty and off-duty periods, rest, or
   hours-of-service limits, so a "driver day" has no server-side definition and no limit to breach.
3. **No logbook lifecycle.** Trips transition PLANNED → IN_PROGRESS → ON_HOLD → COMPLETED /
   CANCELLED, which describes the movement, not a driver's log being submitted, reviewed, corrected
   or approved. There is therefore no review queue and no approver role.
4. **No aggregates.** `DashboardIndicators` returns vehicles available, expired compliance, service
   due, assignment conflicts, readiness blockers, open and escalated workflow items, and integration
   dead letters. Nothing per driver: no distance logged, no entries today, no drivers on duty.
   Running totals would have to be summed client-side from the fetched page, which is only honest
   for the window actually retrieved.
5. **No logbook-specific exceptions.** "Unclosed entry" and "missing odometer" are inferable from
   trip status and null odometer fields, but the service raises no workflow item for either.

## Consequence

A logbook module built today would be a **derived view over trips**, not a system of record. That is
buildable and honest provided every panel says what it is derived from — but it cannot support
submission, review, approval, hours-of-service, or any per-driver total beyond the page fetched.
A logbook as a system of record requires service work first: an aggregate, its lifecycle and
permissions, a repository and migration, query endpoints filterable by driver and period, and
aggregate indicators.

---

## Correction (29 July 2026) — a driver logbook system of record already exists

**The review above is right about the `fleet` package and wrong about the service.** It searched
`services/sfl-fleet-logistics-service/src/main/java/.../fleet` and concluded correctly that there is
no logbook aggregate there. The logbook lives in the **`fuel`** package (S168), which that search did
not cover:

| Concern the review found missing | Where it actually is |
| --- | --- |
| Logbook aggregate | `fuel.domain.model.DriverLogbook` |
| Logbook lifecycle with review and approval | `DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED`, plus `RETURNED → RESUBMITTED`, privileged `REOPENED`, and `CANCELLED` with a reason |
| Repository and migration | `FuelRepository`, `V11__fuel_driver_logbooks.sql` |
| Query endpoints filterable by driver and period | `GET /api/v1/fuel/logbooks` — site, status, driver, vehicle, use classification, journey range, paged |
| Permissions | `FUEL_LOGBOOK_CREATE`, `_SUBMIT`, `_REVIEW`, `_REOPEN`, `_READ` in `FuelPermissionMatrix` |
| Aggregate indicators | `pendingLogbookReviews` and `draftLogbooks` on `GET /api/v1/fuel/dashboard` |
| Exception raising | `MISSING_LOGBOOK` anomaly cases, raised by `FuelSweepScheduler` for a completed trip with no logbook |

So a logbook module **is** a system of record, not a derived view over trips, and it supports
submission, review, correction and approval end to end. It was built as part of the S168 **Fuel &
Driver Logbooks** module; see `docs/fuel/S168_Fuel_Frontend_Gap_Register.md`.

The two things the review named that are genuinely absent remain absent: there is **no duty or shift
concept** — nothing models on-duty and off-duty periods, rest, or hours-of-service limits — and there
are **no per-driver aggregates** beyond the site-level counts above. Both are still service work if
they are wanted.

**The lesson worth keeping:** `sfl-fleet-logistics-service` holds three modules — `fleet`, `fuel` and
`dispatch` — and searching one package is not searching the service.
