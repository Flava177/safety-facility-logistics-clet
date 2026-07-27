# S171 Mailroom / Courier & Dispatch Tracking — Gap and Conflict Report

## Baseline

S171 ("Mailroom / Courier & Dispatch Tracking") is a **Build (Fast-Track)** feature delivered *inside* the
existing `sfl-fleet-logistics-service` deployable, as an extraction-ready sibling of the S166 Fleet and
S168 Fuel features. SFL owns the courier item register, dispatch manifests, chain-of-custody, destination
receipt confirmation, return reconciliation, the exception/case workflow, audit, evidence and dashboards.
Barcode/label scanners and courier-carrier APIs are **optional additive integrations**: the full
chain-of-custody, receipt confirmation and return reconciliation MUST work with no scanner and no carrier
connected. GPS/telematics and RFID are Phase-2 — provider-neutral ports + recorded/simulator adapters only.

Technical identity is unchanged: artifact `sfl-fleet-logistics-service`, schema `fleet_logistics`, base
package `gh.edu.clet.sfl.fleetlogistics` (new feature package `…​.dispatch`), port `8093`, PostgreSQL host
port `5443`, database `sfl__fleet_vehicle_service`, platform token `ftlmp`.

## Requirement-set reconciliation

The generated SRS (`SFL_SRS.docx`, pp. 62–65) documents S171 with the standard five-requirement template
`SRS-SFL-S171-01…05` (records / workflow / evidence-audit / integration / dashboards). The build prompt
elaborates the same scope into six capability-oriented requirements `SRS-SFL-S171-01…06`
(register+inbound / manifest+custody / receipt+variance / scanner+evidence / inbound distribution /
return reconciliation). Both are authoritative and consistent; the build-prompt decomposition is a
*superset elaboration* of the SRS five. The traceability matrix maps every code and test artifact to
**both** numbering schemes so neither is orphaned.

## Resolved conflicts and decisions

| ID | Gap or conflict | Resolution |
|---|---|---|
| D-01 | SRS numbers S171-01…05; build prompt numbers S171-01…06 with different titles. | Treat the build prompt's six requirements as the implementation contract; map each to the SRS five in the RTM. No requirement is dropped. |
| D-02 | Whether S171 is a new deployable. | No. It is an extraction-ready `dispatch` feature package inside `sfl-fleet-logistics-service`, sibling to `fuel`. Reuses fleet foundations by cross-package injection exactly as `fuel` does. |
| D-03 | Relationship to S166 trip/vehicle/driver. | **Optional soft reference.** A dispatch may reference `tripId`/`vehicleId`/`driverId`, validated read-only through a `DispatchFleetReferencePort` (never writes S166 tables). Chain-of-custody, receipt and return work with no trip linked; CT-05 links one. |
| D-04 | Item status lifecycle vs inbound/outbound difference. | One status enum used for both: `RECEIVED → STAGED → DISPATCHED → IN_TRANSIT → DELIVERED → RETURNED`, with `EXCEPTION` as a controlled branch and `CLOSED` only on a clean outcome. Inbound items close at `DELIVERED` on recorded distribution acknowledgement; undelivered inbound routes to `EXCEPTION`. |
| D-05 | Custody hop sequence. | Modeled explicitly as `WAREHOUSE_STAGING → DISPATCH → TRANSIT → CENTRE_RECEIPT → HALL_DEPLOYMENT → COLLECTION → RETURN`, matching the architecture doc §6.6/§6.14 (warehouse staging → scan → seal → vehicle → route → centre receipt → hall deployment → recovery → return reconciliation). Not every dispatch traverses `HALL_DEPLOYMENT`/`COLLECTION`; the gap policy validates ordering and the mandatory hops per dispatch kind, not a fixed count. |
| D-06 | SLA/threshold values (undelivered window, outstanding-return window, exception SLA, dashboard freshness). | None hard-coded as institutional policy. Stored as versioned rows in the shared `fleet_runtime_configuration` table (effective-dated, site-overridable) with documented defaults, read per evaluation through the shared `RuntimeConfigurationPort`. |
| D-07 | Scanner/carrier vendor payloads not procured. | Provider-neutral ports (`ScannerIngestPort`, `CarrierStatusPort`) + recorded simulator adapters + a versioned CSV scan-batch contract. Vendor DTOs live only in adapters; the domain never sees them. Inbound scan/carrier messages reuse the shared secure integration inbox (`FleetIntegrationApplicationService`). |
| D-08 | GPS/telematics + RFID. | Phase-2. Provider-neutral seams only: `GpsTrackReferencePort` and `RfidScanPort` with recorded adapters; no live systems built. Documented as deferred. |
| D-09 | Event naming: catalog pre-seeds `sfl.ftlmp.dispatch-created.v1` and `sfl.ftlmp.dispatch-received.v1`. | Reuse both verbatim (`dispatch-created` = manifest created/dispatched aggregate event; `dispatch-received` = destination receipt confirmed). Add further `sfl.ftlmp.*.v1` lifecycle events and register them in `FleetEventType` and `docs/integration/event-catalog.md`. |
| D-10 | Migration numbering. | Applied S166 (V1–V9_1) and S168 (V10–V15) migrations are immutable. Dispatch begins at **V16** and edits none of them. Must apply cleanly on a fresh DB and on a DB already carrying S166+S168. |
| D-11 | Governed evidence. Fuel stored only opaque evidence UUIDs; S171 requires full governance. | Reuse the fleet governed-evidence foundation (`FleetEvidenceApplicationService`, `EvidenceReference`, `EvidenceRetentionClass`, export approval, legal hold, audit-logged access) through a `DispatchEvidencePort` adapter. Signed dispatch/receipt/scan/custody records are registered as governed evidence with a mandatory retention class; no binary is stored in a dispatch row. The chain-of-custody record is reconstructable from `custody_handovers` + audit. |
| D-12 | Security-relevant variances (broken seal / tamper) to SSEMP. | Surfaced through a `SecurityVisibilityPort` (recorded adapter → transactional outbox event `sfl.ftlmp.dispatch-receipt-variance.v1` / `custody-gap-detected.v1` flagged `securityRelevant`). No direct write to any security database. |
| D-13 | Offline/edge receipt capture during WAN loss. | Receipts accept a client-supplied `capturedAt` + `captureCorrelationId`; persistence enforces a unique `(dispatch_id, capture_correlation_id)` constraint and the application replays idempotently through `IdempotencyPort` so a receipt is never lost or double-applied on restore. |
| D-14 | New SflRole values for dispatch user classes. | Added additively in `sfl-service-common`: `DISPATCH_CONTROLLER`, `LOGISTICS_COORDINATOR`, `CENTRE_MANAGER`, `MAILROOM_OFFICER`, `SECURITY_OFFICER` (SSEMP escalation target). Existing platform/fleet roles (`SFL_ADMIN`, `FLEET_MANAGER`, `FLEET_LOGISTICS_OFFICER`, `AUDITOR`, `COMPLIANCE_OFFICER`, `DTI_ADMIN`, `INTEGRATION_ENGINEER`, `SERVICE_INTEGRATION`, `COMMAND_ROLE`) are reused. |
| D-15 | Dashboard snapshots (fleet-only foundation; fuel built ad hoc). | Dispatch implements its own snapshot trio (`dispatch_dashboard_snapshots` table + port + adapter + scheduled refresh) mirroring the fleet pattern, plus live reconciliation counts and stale-data warnings. |

## External-owner gaps (remain configurable / behind ports)

- Barcode/label scanner event schema, carrier-API status contract and webhook signing scheme — Integration Engineer / vendor.
- NBES/examination dispatch-context contract (deployment order → manifest) — Examination Operations.
- Approved undelivered-item window, outstanding-return escalation window and exception SLA bands — Transportation & Logistics Unit.
- Production evidence retention classes, legal-hold and export approval policy — Compliance / Data Protection.
- SSEMP security-escalation routing and acknowledgement contract — Health, Safety & Security Unit.
- GPS/telematics and RFID providers — Phase-2 procurement.

None of these justify weakening validation, authorization, audit, evidence governance or idempotency.

## Deferred to Phase 2

- Live GPS/telematics tracking and RFID seal/asset reads (seams + recorded adapters only in Phase 1).
- Native mobile / true offline store-and-forward client (Phase 1 delivers a responsive console with an
  idempotent offline-capture-then-reconcile API path and preserved partial entry).
