# S166 — Frontend Gap Register

**Status (29 July 2026): all twelve gaps closed.**

| # | Gap | Was | Closed by |
| --- | --- | --- | --- |
| 1 | Dashboard and report paths differ from the inventory | `DOC` | The inventory corrected. Every implemented path was right; four documented ones were wrong, and `dashboard/compliance-report` was never implemented at all |
| 2 | Vehicle readiness has no dedicated endpoint | `WORKAROUND` | `GET /vehicles/{id}/readiness` — the same `FleetReadinessService.assessVehicle` policy `assignment-preview` runs, reached without pretending a trip is involved |
| 3 | Vehicle movement / telematics history not exposed | `MISSING` | `GET /vehicles/{id}/movement?size=` plus `VehicleLocationRepository.findByVehicle` |
| 4 | Standalone periodic inspection cannot be recorded | `MISSING` | `POST /vehicles/{id}/inspections` |
| 5 | No evidence search endpoint | `WORKAROUND` | `GET /evidence?relatedRecordType=&relatedRecordId=` |
| 6 | Evidence export paths differ from the inventory | `DOC` | The inventory corrected — `/decision` not `/approval`, `POST .../export` not `GET .../download` |
| 7 | Audit paths differ from the inventory | `DOC` | The inventory corrected — `/audit/records` and `/audit/chain/verification` |
| 8 | Integration intake path, and no inbox search | `DOC` / `MISSING` | The intake path corrected, and `GET /integrations/messages?sourceSystem=&status=&eventType=` implemented |
| 9 | Documented filters the controllers do not accept | `DOC` | `severity` added to `GET /workflow-items`; `complianceExpiringBefore` answered by the new compliance search; the undocumented ones documented |
| 10 | Compliance only reachable per vehicle | `WORKAROUND` | `GET /vehicles/compliance-documents?documentType=&status=&expiringBefore=&size=` |
| 11 | Drilldown indicators are a fixed set of four | noted | Documented in the inventory, so a reader knows which four are clickable and that a fifth returns an empty list rather than a 404 |
| 12 | `RetentionClass` vs `EvidenceRetentionClass` | noted | **Left as it is** — see below |

### Gap 4 — what it was blocking

Inspections could only be recorded against a trip, so a vehicle sitting in the yard could not be
inspected at all. That blocked the periodic-inspection half of SRS-SFL-S166-01. The request record
and the service path both existed already — `TripApplicationService.recordInspection` has always
accepted a null trip with an explicit vehicle — and nothing mapped it. Verified live: a `PERIODIC`
inspection with no trip returns `201` and a real inspection record.

### Gap 5 — the main usability cost, now removed

With no search, every closure dialog asked an operator to paste an evidence reference id from
somewhere else. `EvidenceRepository.findByRelatedRecord` had answered that question since the service
was built and nothing exposed it. A trip or workflow closure can offer a picker now.

### Gap 12 — deliberately not resolved

Two retention vocabularies still coexist: `RetentionClass` on compliance documents
(`OPERATIONAL_SHORT` … `LEGAL_HOLD`) and `EvidenceRetentionClass` on evidence
(`OPERATIONAL_1_YEAR` … `LEGAL_HOLD`). Merging them is a **records-management decision with a
migration behind it**, not a code change: existing rows carry the old values, and choosing which
vocabulary survives decides how long already-filed evidence is kept. That is not a call to make from
a gap register. `RetentionClass`'s own Javadoc already flags its periods as an unconfirmed assumption
(gap report C-08); it should be settled with compliance before go-live.

## One defect found while verifying, and fixed

`GET /vehicles/compliance-documents?expiringBefore=…` returned **500** —
`could not determine data type of parameter $7`.

This is the same defect that made `GET /fleet/audit/records` return 500 on every call before the S168
round. Hibernate expands a named parameter used twice into **two** JDBC placeholders, so the one
inside `:param is null` stands alone, and Postgres cannot infer a type for a parameter it only ever
sees compared to null. The fix is `cast(:param as date)` in the null test; the same cast was applied
to the new inbox search, which had the identical shape.

Worth recording that a comment had been written into that query claiming the pattern was safe
*because* each parameter also appeared in a comparison. That reasoning was wrong, and only driving
the endpoint showed it.

## Also found while verifying — not fixed, needs a decision

`GET /fleet/audit/chain/verification` reports **`intact: false`** against the local development
database, diverging at sequence 0 of 207 records with "Record hash does not match the stored
content".

The endpoint is behaving correctly — detecting a divergence and reporting it with
`FLEET_AUDIT_CHAIN_FAILURE` and a 409 is exactly what it is for. And the chain logic is sound: the
"audit chain stays intact" tests pass against a freshly migrated database. So this is about **this
database's history**, most likely rows written before a change to the canonicaliser.

It is recorded rather than diagnosed. Confirming it properly means comparing a stored row against what
the current canonicaliser produces for the same content, and if that is the cause it has a bearing on
whether any pre-change audit row can still be verified — which is a compliance question, not a
refactor.

## Front-end limitations recorded honestly

- No mock data is shipped. `VITE_FLEET_DEV_FALLBACK` exists as a named switch but no screen uses a
  fallback.
- Vehicle and driver pickers in the trip dialogs load up to 200 records per site. Beyond that a
  server-side typeahead endpoint would be needed.
### The screens now use the new endpoints

Built and walked through against the running service on 29 July 2026.

- **Readiness panel** — `GET /vehicles/{id}/readiness`. It used to be fetched through
  `trips/assignment-preview` with only a `vehicleId`: the right answer through an endpoint shaped for a
  question nobody was asking on that screen. Confirmed live on GT-9902-26 — one blocking issue
  (`VEHICLE_REGISTRATION` missing) and one advisory (a roadworthiness certificate expiring 2026-08-14,
  picked up from a document registered minutes earlier).
- **Movement panel** — `GET /vehicles/{id}/movement`, a new tab on the vehicle record. Coordinates at
  five decimal places, `recordedAt` leading every row, and a caption saying plainly that this is a
  vendor projection SFL does not correct. **Verified empty only.** No telematics source is allowlisted
  in the dev database, so the populated table has not been seen in a browser; the endpoint returns 200
  and the empty state is right.
- **Standalone inspection** — `POST /vehicles/{id}/inspections`, from a "Record inspection" button on
  the readiness card. The dialog previews the *derived* result rather than offering it as a field, and
  refreshes readiness on save because a critical finding takes the vehicle out of service.
- **Compliance search** — `GET /vehicles/compliance-documents`. The screen was fanning out over the
  first fifty active vehicles in scope and saying so on the page; it is one query now, with document
  type, status and expiry filters. Confirmed live: four documents ordered by expiry, and
  `status=EXPIRED` narrowing to the one that the server-computed dashboard drilldown reports
  independently on the same screen.
- **Evidence picker** — `GET /evidence?relatedRecordType=&relatedRecordId=`, wired into the
  workflow-closure dialog as `EvidenceSelect`. This is the gap this register called the main usability
  cost in the whole console. A workflow item already carries the record it is about, so the picker
  needs no new convention: it lists what is filed against that record, and keeps a text field for a
  reference held elsewhere, because a site-wide certificate closes a dozen items and belongs to none of
  them. Confirmed live — two evidence records offered by file name and type, and the close attempt
  reached the service with the picked id: it was refused for `FLEET_INVALID_STATE_TRANSITION`, not
  `FLEET_CLOSURE_EVIDENCE_MISSING`.
- **Evidence by record** on Evidence & audit, replacing a paste-an-id-only screen and the notice
  claiming no evidence search existed. Open-by-identifier stays, for an id that came from a log.
- **Dead-letter finder** — `GET /integrations/messages`, replacing a loosely-typed read of whatever the
  health projection happened to carry. Rows are typed, replay is offered inline on anything not already
  `PROCESSED`, and the dead-letter alert has a "Show them" shortcut. **Verified empty only**, for the
  same reason as movement: nothing has arrived through the signed intake endpoint in this database.
- **Severity filter** on the workflow queue. The service has accepted `severity` since the search
  endpoint was written and the column has always shown it, so a supervisor could see which rows were
  critical and had no way to ask for only those. Confirmed live: `MAJOR` returns the seeded item,
  `CRITICAL` returns nothing.

**Still open:** an `EvidenceSelect` on the compliance, service and odometer dialogs. Those forms take
an evidence reference for a record that does not exist yet at the moment the form is open, and there is
no convention for filing evidence against a `Vehicle` — inventing one here would have shipped a
dropdown that is always empty. The text field stays until that convention is decided.

**Noticed, not fixed:** the workflow detail screen offers every transition button regardless of the
current status, so "Close" is clickable on an `OPEN` item the service will refuse. Pre-existing, outside
this round, and worth gating against the permitted transitions.

---

## The original findings, kept for the evidence

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
