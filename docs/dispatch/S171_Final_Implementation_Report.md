# S171 Mailroom / Courier & Dispatch Tracking — Final Implementation Report

**Module:** `sfl-fleet-logistics-service` · **Schema:** `fleet_logistics` · **Package:**
`gh.edu.clet.sfl.fleetlogistics.dispatch` · **Port:** 8093 · **Platform token:** `ftlmp`

S171 is delivered as an extraction-ready feature package inside the existing FTLMP deployable, a sibling of
S166 Fleet and S168 Fuel. It reuses the shared audit hash-chain, governed evidence, secure integration
inbox, transactional outbox, notifications, runtime configuration and OpenAPI foundations; no service,
artifact, schema or existing fleet/fuel code was renamed.

## 1. Requirement-by-requirement status

| SRS | Requirement | Status |
|---|---|---|
| SRS-SFL-S171-01 | Mailroom/courier register & item tracking | ✅ Complete |
| SRS-SFL-S171-02 | Dispatch manifest & unbroken chain-of-custody | ✅ Complete |
| SRS-SFL-S171-03 | Destination receipt confirmation & variance handling | ✅ Complete |
| SRS-SFL-S171-04 | Optional scanner/carrier integration & immutable custody evidence | ✅ Complete (Phase-1 recorded adapters; GPS/RFID seams only) |
| SRS-SFL-S171-05 | Inbound mail registration & internal distribution | ✅ Complete |
| SRS-SFL-S171-06 | Return-leg / reverse-logistics reconciliation | ✅ Complete |

Custody gaps, receipt variances, scan mismatches and return discrepancies each open an accountable,
SLA-controlled exception case and **block** dispatch/custody closure until resolved. Closure requires
explanation, decision, closure reason and governed evidence.

## 2. Implemented API inventory (`/api/v1/dispatch`)

| Tag | Endpoints |
|---|---|
| Dispatch Items | `POST /items`, `GET /items`, `GET /items/{id}`, `POST /items/{id}/{stage\|dispatch\|in-transit\|deliver\|return\|close}`, `POST /items/{id}/misroute` |
| Inbound Mail | `POST /inbound`, `GET /inbound`, `POST /inbound/{id}/distribute` |
| Dispatch Manifests | `POST /manifests`, `GET /manifests`, `GET /manifests/{id}`, `GET/POST /manifests/{id}/items`, `POST /manifests/{id}/seal`, `/assign-trip`, `/dispatch`, `/in-transit`, `/close` |
| Chain of Custody | `POST /custody`, `GET /custody?dispatchId=`, `GET /custody/{dispatchId}/gaps` |
| Dispatch Receipts | `POST /receipts` (edge-capable, idempotent), `GET /receipts?dispatchId=`, `GET /receipts/{id}` |
| Return Reconciliation | `POST /returns/reconcile`, `GET /returns?dispatchId=`, `GET /returns/{id}` |
| Dispatch Exceptions | `GET /exceptions`, `GET /exceptions/{id}`, `POST /exceptions/{id}/{assign\|reassign\|review\|request-explanation\|explain\|approve\|reject\|escalate\|hold\|resume\|cancel\|close\|reopen}` |
| Dispatch Integrations | `POST /scans/imports` (CSV, idempotent), `GET /scans/imports/{id}`, `/imports/{id}/rows`, `POST /integrations/scanners/{provider}/events` (secure inbox), `POST /integrations/carriers/{carrier}/status`, `GET /integrations/health`, `POST /integrations/outbox/{id}/replay` |
| Dispatch Dashboards and Reports | `GET /dashboard`, `GET /reports/items.csv`, `GET /reports/exceptions.csv` |

All state-changing endpoints honour `Idempotency-Key`/`X-Correlation-ID`, optimistic locking, site-scoped
authorization and the shared `ApiResponse`/`ApiError` envelope; workflow transitions are explicit
endpoints (no generic PATCH-status).

## 3. Domain & state model

Aggregates (immutable records with explicit transition methods): `CourierItem`, `Dispatch`,
`DispatchManifestItem`, `CustodyHandover`, `DispatchReceipt`, `ReturnReconciliation`,
`DispatchExceptionCase`, `ScanImportBatch`/`ScanImportRow`. Policies (pure): `CustodyChainPolicy`,
`ReceiptVariancePolicy`, `ReturnReconciliationPolicy`, `DispatchClosurePolicy`, `DispatchPermissionMatrix`.
Item lifecycle `RECEIVED→STAGED→DISPATCHED→IN_TRANSIT→DELIVERED→RETURNED→CLOSED` with `EXCEPTION` branch;
dispatch `DRAFT→SEALED→DISPATCHED→IN_TRANSIT→RECEIVED→(RETURNED)→RECONCILED→CLOSED`; custody hops
`WAREHOUSE_STAGING→DISPATCH→TRANSIT→CENTRE_RECEIPT→HALL_DEPLOYMENT→COLLECTION→RETURN`. Full detail in
`S171_Domain_And_State_Model.md`.

## 4. Database migrations (idempotent on fresh DB and on a DB already carrying V1–V15)

`V16 courier_items` · `V17 dispatches` + `dispatch_manifest_items` · `V18 custody_handovers` +
`dispatch_receipts` (edge-capture idempotency `(dispatch_id, capture_correlation_id)`) · `V19
return_reconciliations` + `dispatch_exception_cases` + `dispatch_exception_case_history` · `V20
scan_import_batches`/`scan_import_rows` + `dispatch_dashboard_snapshots` + seeded `dispatch.*` runtime
config. Every operational table carries UUID id, site scope, created/modified actor+time (TIMESTAMPTZ UTC),
source channel, correlation ID, lifecycle status and optimistic-lock version where mutable, plus the
required unique/check/idempotency/queue indexes.

## 5. Security matrix

Additive `SflPermission` values `DISPATCH_ITEM_READ/REGISTER/MANAGE`, `DISPATCH_MANIFEST_READ/CREATE`,
`DISPATCH_CUSTODY_RECORD`, `DISPATCH_RECEIPT_CONFIRM`, `DISPATCH_RETURN_RECONCILE`,
`DISPATCH_INBOUND_REGISTER/DISTRIBUTE`, `DISPATCH_EXCEPTION_READ/MANAGE/APPROVE/ESCALATE`,
`DISPATCH_REPORT_READ/EXPORT`, `DISPATCH_INTEGRATION_INGEST/REPLAY`; additive `SflRole` values
`DISPATCH_CONTROLLER`, `LOGISTICS_COORDINATOR`, `CENTRE_MANAGER`, `MAILROOM_OFFICER`, `SECURITY_OFFICER`.
Mapping in `DispatchPermissionMatrix`; site scope enforced on every operation via `DispatchAccessPolicy`.

## 6. Integrations & events

Ports + Phase-1 recorded adapters: `DispatchFleetReferencePort` (read-only S166 trip/vehicle/driver
validation), `DispatchEvidencePort` (governed evidence on the S166 foundation), `SecurityVisibilityPort`
(SSEMP via outbox), `CarrierStatusPort`, `RfidScanPort`, `GpsTrackReferencePort`, `DispatchOutboxAdminPort`.
Inbound scanner/carrier events reuse the shared secure inbox (HMAC, allowlist, schema, idempotency, inbox
before domain). 18 `sfl.ftlmp.*.v1` events registered in `FleetEventType` and documented in
`docs/integration/event-catalog.md` (two pre-seeded names reused verbatim).

## 7. UI deliverables

Responsive page formerly at `/dispatch/`, retired by ADR 0006 in favour of the dashboard's courier
and dispatch screens. As built it followed the Fleet/Fuel visual language: overview/dashboard, item
register, inbound registration + distribution, manifest builder (items, seals, trip link, seal, dispatch),
chain-of-custody record/view with live gap chips, destination receipt confirmation, return reconciliation,
exception queue + full manager action set, and integration health + CSV exports. The API is authoritative
for every custody/variance/closure decision.

## 8. Tests & exact results

`mvn -pl sfl-fleet-logistics-service -am test` → **389 tests, 0 failures, 0 errors, 1 skipped**
(placeholder `FleetPostgresEndToEndTest`). S171 contributes 36: `DispatchMandatoryScenariosEndToEndTest`
(all 19 mandatory scenarios incl. CT-05 secure dispatch), `DispatchDomainTest` (13 domain + policy),
`DispatchEventCatalogTest` (2), `DispatchArchitectureTest` (2 — domain framework-free & adapter-free).
S166 + S168 regression suites remain green in the same run.

## 9. Docker/PostgreSQL & Swagger URLs

See `S171_Operations_And_Verification_Guide.md`. Swagger UI `http://localhost:8093/swagger-ui.html`;
OpenAPI `http://localhost:8093/v3/api-docs`; dashboard `http://localhost:8093/ui/dispatch`.

## 10. Known limitations & deferred Phase-2 work

- GPS/telematics and RFID are provider-neutral **seams** only (`GpsTrackReferencePort`, `RfidScanPort`
  return no live data); carrier-API status is a recorded adapter. Replacing any with a live vendor adapter
  requires no domain change.
- Live carrier/scanner vendors are simulated; the CSV scan-import contract and secure-inbox event contract
  are the supported Phase-1 ingestion paths.
- On sandboxed hosts that block loopback TCP, embedded Tomcat cannot bind its NIO connector; the
  `@SpringBootTest` suite still proves context/Flyway/persistence/wiring. On a normal host the service
  binds `:8093` and serves all endpoints.

## 11. Remaining gaps with owners

No unresolved critical or high-severity gap. Deferred Phase-2 GPS/RFID/live-carrier integrations are owned
by the Integration Engineering team and tracked outside this slice.
