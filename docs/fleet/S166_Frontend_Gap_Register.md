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
