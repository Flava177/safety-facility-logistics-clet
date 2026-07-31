# S168_fuel Gap and Conflict Report

## Baseline

S168_fuel is a hybrid system inside `sfl-fleet-logistics-service`. SFL owns fuel records, logbooks,
reconciliation, exception workflow, audit, dashboards and provider-neutral adapters. Fuel-card/POS vendors,
telematics and Finance remain external systems. The five normative requirements are
`SRS-SFL-S168fuel-01` through `-05`.

## Resolved conflicts

| ID | Gap or conflict | Resolution |
|---|---|---|
| F-01 | The generated SRS says modular monolith while the approved realignment uses four deployables. | Implement an extraction-ready `fuel` feature inside the existing fleet/logistics deployable. |
| F-02 | S166 traceability mentions `FuelCapabilityPort`, but no such source contract exists. | Add explicit fuel-to-fleet reference and odometer ports; do not fabricate historical implementation. |
| F-03 | S166 and S168 both observe odometer readings. | S166 vehicle odometer is the accepted current reading. Fuel/logbook observations retain provenance and may advance it only through a port; regression creates an anomaly. |
| F-04 | Older workplan events use `sfl.logistics.*`; the approved catalog uses `sfl.ftlmp.*.vN`. | Use `sfl.ftlmp.*.v1` exclusively and update the catalog. |
| F-05 | Original migration reservations conflict with completed S166 migrations through V9_1. | S168 begins at V10 and never edits applied S166 migrations. |
| F-06 | Provider payloads and Finance contracts are not procured. | Define provider-neutral ports, signed recorded adapters and a versioned CSV contract; defer vendor field mapping. |
| F-07 | Numeric limits, materiality, consumption bands and SLAs are not approved. | Store effective-dated, site-scoped, versioned policies with documented local defaults. No threshold is hard-coded as institutional policy. |
| F-08 | The SRS requests mobile support but no native-mobile product is scoped. | Deliver a responsive driver logbook workflow with draft preservation; native/offline synchronization remains an integration seam. |
| F-09 | Receipt retention values are not approved. | Store governed evidence references and require configured retention classes; no binary receipt is stored in a fuel row. |

## External-owner gaps

- Fuel provider/card API, webhook signature scheme and production CSV layout: Integration owner/vendor.
- Finance exception materiality and acknowledgement contract: Finance/Audit.
- Approved consumption bands, tank capacities, receipt grace periods and anomaly SLAs: Transportation & Logistics.
- Production retention, legal-hold and evidence export policy: Compliance/Data Protection.

These gaps must remain configurable or behind ports and do not justify weakening validation, audit or
idempotency.

## Implementation gaps found during final audit

The following items remain open and prevent a Definition-of-Done declaration. They are implementation gaps,
not vendor-specific deferrals:

| Priority | Gap | Required closure evidence |
|---|---|---|
| Critical | The 17 mandatory S168 end-to-end scenarios are not individually implemented and proven. The current S168 suite contains domain, architecture and one combined PostgreSQL scenario. | Add the missing API, authorization, integration, scheduler, audit-tamper, retry and workflow E2E cases and run the full reactor. |
| High | The anomaly workflow does not yet expose reassignment, hold/resume or authorised cancellation, and notification delivery is not wired for assignment, overdue, escalation or blocked states. | Explicit domain transitions, permission checks, audit/outbox records, notification-port calls and tests. |
| High | Evidence IDs are referenced, but fuel-specific evidence metadata validation, access/export justification and export audit flows are not exposed or tested. | Reuse the governed evidence service through an application port; verify retention/hash/access/export and audit-chain tamper detection. |
| High | Provider ingestion reuses the signed inbox, but fuel payload schema/version validation, replay API, failed outbound Finance/Audit delivery and dead-letter visibility are not fully demonstrated. | Provider contract tests, replay/retry endpoints, recorded Finance/Audit adapter, health metrics and failure-path E2E coverage. |
| High | Reconciliation does not yet implement all requested rules: daily/weekly/monthly and driver/card limits, consumption history, logbook distance/fuel mismatch, unusual time/site patterns, variance and repeated-actor anomalies. | Effective-dated policy fields, reproducible rule calculations, stored rule results and tests for each rule. |
| High | The operational dashboard lacks the required import results, transaction/reconciliation detail, evidence view, manager actions, anomaly investigation detail, reports/export and integration-health screens. | Responsive API-backed screens with loading, empty, validation, server-error, authorization and correlation-ID states. |
| Medium | Dashboard coverage is limited to aggregate transaction totals. Snapshot identity/period/source references and required vehicle/driver/vendor/provider/SLA/logbook drilldowns are absent. | Snapshot/read model, filters, drilldowns, reconciliation tests and stale-provider indicators. |
| Medium | API list operations use a bounded `limit`, but not the documented page/cursor contract and stable pagination metadata. OpenAPI field examples and error responses remain limited. | Page/cursor DTOs, stable sort keys, documented validation/errors/examples and MVC tests. |
| Medium | Individual scheduler toggles are inserted as runtime configuration but the scheduler currently uses one Spring property and does not refresh dashboard snapshots or detect stale integrations. | Runtime-config evaluation per job, idempotency tests, snapshot refresh and stale-integration sweep. |

Until these rows are closed and the mandatory verification is green, S168_fuel is an in-progress implementation.


---

## Update — backend completion pass (2026-07-23)

This pass extended the existing S168_fuel slice against the S166 foundations. It was written
against verbatim S166 contracts; **it has not yet been compiled or run in this environment**
(no JDK 17 / Maven access here) and must be built and tested locally before sign-off.

**Now addressed**

- *Anomaly workflow completeness.* `FuelAnomalyCase` gained `reassign`, `hold`, `resume` and
  `cancel` transitions (reusing the existing `HELD`/`CANCELLED` statuses — no schema change).
  `FuelApplicationService.transitionAnomaly` handles the new actions, records specific
  `AuditAction`s (`REASSIGN`/`HOLD`/`RESUME`/`CANCEL`/`ESCALATE`/`CLOSE`/`REOPEN`), and the
  anomaly controller exposes them. Reopen/cancel remain permission-gated (`FUEL_ANOMALY_MANAGE`).
- *Notifications.* Wired through the existing `NotificationPort`: new/assigned/reassigned anomalies
  notify the assignee (and the `FLEET_MANAGER` role on creation — the CT-08 routing), escalations
  notify `FLEET_MANAGER` (`WORK_ESCALATED`), holds notify the assignee (`WORK_BLOCKED`); logbook
  submit notifies the manager review queue, return/approve notify the driver (`createdBy`).
- *Finance/Audit outbound.* New `FinanceAuditVisibilityPort` + `RecordedFinanceAuditAdapter`
  surface **material** exceptions through the transactional outbox (no direct Finance DB write);
  invoked on material anomaly creation and on escalation.
- *Outbox delivery visibility + replay.* New `FuelOutboxAdminPort` + `OutboxAdminAdapter` wrap the
  shared outbox repository/drainer; `GET /outbox/health` and `POST /outbox/{id}/replay`
  (permission `FUEL_INTEGRATION_REPLAY`) expose pending/published/dead-letter counts and
  privileged dead-letter requeue.
- *Additional reconciliation rules* (reproducible, stored in `fuel_reconciliations.rule_results`):
  `CONSUMPTION_RANGE` (litres-per-km vs policy min/max, computed consumption now persisted),
  `COST_VARIANCE` (unit-price deviation vs prior transaction), `LOGBOOK_MATCH` (odometer vs the
  trip's logbook end reading), `REPEATED_PATTERN` (repeated anomalies per vehicle/driver in a
  look-back window). Backed by new repository reads `findPreviousTransaction`,
  `countRecentAnomalies`, `findLogbookForTrip`.
- *Tests.* Added domain coverage for the new anomaly transitions (`FuelDomainTest`).

**Still open (unchanged from above unless noted)**

- The 17 mandatory end-to-end scenarios are still not all individually implemented; only domain,
  architecture and one combined PostgreSQL scenario plus the new domain transition test exist.
  A full reactor rerun is required and has not been performed here.
- Reconciliation still lacks daily/weekly/monthly rolling limits and fuel-card/allocation checks
  (S168_fuel-04 card management is not implemented — no card registry yet). `COST_VARIANCE` uses a
  documented default tolerance constant and should move to a versioned policy column.
- Governed evidence export/legal-hold/audit-tamper flows for fuel are not yet exposed via a fuel port.
- Operational UI screens (import results, transaction/reconciliation detail, manager actions,
  anomaly investigation, reports/export, integration-health) remain to be built.
- Dashboard drilldowns and snapshot identity/period/source references remain limited.
- List endpoints still use a bounded `limit` rather than a page/cursor contract.

Verification owed locally: `mvn -pl sfl-fleet-logistics-service -am test`, app boot on 8093,
`/actuator/health`, `/v3/api-docs`, Swagger UI, `/fuel/`, and S166 regression suite.

---

## Update — record scope on logbooks (31 July 2026)

**A driver could read another driver's logbook by id.** `FuelAccessPolicy.isDriverOnly` narrows the
logbook **list** in SQL on `created_by`, guards `createLogbook` against the trip's driver reference and
guards `transitionLogbook` against the record's creator. `logbook(UUID, ActorContext)` — the detail read
behind `GET /api/v1/fuel/logbooks/{id}` — checked permission and site only.

The comment three lines below it already stated the rule the method broke: *"Scoping a query by what the
caller may see belongs on this side of the wire: a client-side filter is a display convention, and the
records would still have crossed the boundary."* That is exactly what happened, one record at a time
instead of a page at a time — a driver holding a colleague's logbook id read journey, route, purpose and
passenger-load notes in full.

Closed by `FuelAccessPolicy.requireOwnRecord`, applied to the read, the create and the transition.
Ownership is `created_by`, deliberately the same column the list query filters on, so the collection and
the record cannot disagree about who owns what.

**Both ownership refusals were 500s, and unaudited.** They threw `IllegalStateException`, which reaches
the caller as an internal error and writes nothing to the hash chain — so a driver being refused a
colleague's record left no evidence that anyone had been refused. They now throw
`FleetAuthorizationException`, which is the SRS's `FLEET_UNAUTHORIZED_SCOPE` envelope, returns 403, and is
recorded as a denial.

`FuelMandatoryScenariosEndToEndTest.driver_is_restricted_to_own_records` asserted the defect — it required
`IllegalStateException` — and now asserts the authorisation refusal plus the two by-id cases that the old
assertion could not distinguish from an empty list.

**Not closed:** a `FLEET_DRIVER` still reads every fuel **transaction** at their site, because they hold
`FUEL_TRANSACTION_READ` and neither `transaction(id, actor)` nor `transactions(...)` narrows. Whether a
driver should see only their own vehicle's transactions is a policy question for the Transportation &
Logistics Unit, not a defect to be fixed on an assumption — recorded here rather than decided here.
