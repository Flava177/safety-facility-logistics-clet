# S171 Requirement Traceability Matrix

Each build-prompt requirement is mapped to the generated-SRS requirement it elaborates, to the primary
code artifacts, and to the tests that prove it. Package root: `gh.edu.clet.sfl.fleetlogistics.dispatch`.

## Build-prompt requirements → SRS requirements

| Build prompt | Elaborates SRS | Scope |
|---|---|---|
| SRS-SFL-S171-01 Mailroom/Courier register & item tracking | S171-01 | Courier item register, item lifecycle, inbound registration |
| SRS-SFL-S171-02 Dispatch manifest & unbroken chain-of-custody | S171-01 / S171-02 | Manifest, seal IDs/counts, custody handovers, gap blocking |
| SRS-SFL-S171-03 Destination receipt confirmation & variance | S171-02 / S171-03 | Seal/count/signature confirmation, edge capture, variance escalation |
| SRS-SFL-S171-04 Optional scanner/carrier integration & immutable custody evidence | S171-03 / S171-04 | Provider-neutral scanner/carrier ports, governed evidence, secure inbox |
| SRS-SFL-S171-05 Inbound mail registration & internal distribution | S171-01 / S171-02 | Inbound register, distribution acknowledgement, undelivered escalation |
| SRS-SFL-S171-06 Return-leg / reverse-logistics reconciliation | S171-02 / S171-05 | Return leg custody, reconcile vs manifest, outstanding escalation |
| (cross-cutting) Exception/case workflow, SLA, notifications | S171-02 | Accountable SLA-controlled exception cases; closure gating |
| (cross-cutting) Dashboards & reports | S171-05 | Indicators, filters, reconciliation, stale warnings, CSV export |

## Requirement → artifacts → tests

Class names below are the **as-built** artifacts. Tests: `dispatch/domain/DispatchDomainTest` (13),
`dispatch/architecture/DispatchArchitectureTest` (2), `dispatch/events/DispatchEventCatalogTest` (2),
`dispatch/e2e/DispatchMandatoryScenariosEndToEndTest` (19 numbered scenarios).

| Requirement | Key artifacts (as built) | Verifying tests |
|---|---|---|
| **S171-01** Register & tracking | `domain.model.CourierItem` (+`Direction`, `Type`, `Sensitivity`, `Status`); `application.service.CourierItemService.registerItem/advanceItem/misrouteItem`; `infrastructure.persistence.JdbcDispatchRepository`; `api.DispatchItemController`; V16 `courier_items` | `DispatchDomainTest` (item transitions, custody-required derivation); E2E #1 (register→staged→dispatched→in-transit→delivered) |
| **S171-02** Manifest & custody | `domain.model.Dispatch`, `DispatchManifestItem`, `CustodyHandover` (+`CustodyHop`, `SealState`); `domain.policy.CustodyChainPolicy`, `DispatchClosurePolicy`; `application.service.DispatchManifestService`, `DispatchCustodyService`; `api.DispatchManifestController`, `ChainOfCustodyController`; V17/V18 | `DispatchDomainTest` (custody ordering, gap detection, closure gate); E2E #3 (manifest+full chain), #4 (custody gap blocks closure) |
| **S171-03** Receipt & variance | `domain.model.DispatchReceipt` (+`ReceiptOutcome`, `VarianceType`); `domain.policy.ReceiptVariancePolicy`; `application.service.DispatchReceiptService`; `api.DispatchReceiptController`; V18 `dispatch_receipts` (edge cols + `(dispatch_id, capture_correlation_id)` idempotency) | `DispatchDomainTest` (variance classification); E2E #5 (clean receipt completes custody), #6 (variance → SSEMP), #7 (offline capture, idempotent reconcile) |
| **S171-04** Scanner/carrier & evidence | `application.port.CarrierStatusPort`, `RfidScanPort`, `GpsTrackReferencePort`, `DispatchEvidencePort`, `SecurityVisibilityPort`; `application.service.DispatchScanService`; `infrastructure.integration.Recorded*Adapter`; `api.DispatchScanController`, `DispatchIntegrationController`; V20 `scan_import_batches/rows` | E2E #13 (scan mismatch → variance), #14 (unsigned/invalid rejected by inbox), #15 (outbox dead-letter + replay) |
| **S171-05** Inbound & distribution | `CourierItem` inbound/acknowledgement fields; `application.service.CourierItemService.registerItem(INBOUND)/distributeInbound/flagUndelivered`; `api.InboundMailController`; undelivered sweep in `DispatchSweepScheduler` | E2E #10 (register+distribute w/ acknowledgement), #11 (undelivered selected by sweep + escalated) |
| **S171-06** Return reconciliation | `domain.model.ReturnReconciliation` (+`ReturnOutcome`); `domain.policy.ReturnReconciliationPolicy`; `application.service.DispatchReturnService.reconcile/escalateOutstanding`; `api.ReturnReconciliationController`; V19 | E2E #8 (matched return closes custody), #9 (discrepancy blocks closure), #12 (outstanding-return escalation) |
| **Exception workflow** | `domain.model.DispatchExceptionCase` (Type/Severity/Status/Decision); `application.service.DispatchExceptionService`; `api.DispatchExceptionController`; `infrastructure.scheduling.DispatchSweepScheduler` | `DispatchDomainTest` (full transition set, closure gate); E2E #16 (no closure without explanation/decision/evidence) |
| **Evidence/audit** | `infrastructure.integration.RecordedDispatchEvidenceAdapter` → `EvidenceRepository`/`AuditPort`; `application.service.DispatchEvidenceSupport`; `AuditPort.record` on every state change | E2E #17 (audit-chain verification runs after operations); governed evidence on receipt/custody/distribution/return |
| **Integration** | shared `FleetIntegrationApplicationService` inbox; `IntegrationEventPublisher` outbox; `DispatchOutboxAdminPort` → `DispatchOutboxAdminAdapter` health/replay | E2E #14 (secure inbox rejects unsigned), #15 (dead-letter + privileged replay) |
| **Dashboards** | `application.service.DispatchDashboardService`; `JdbcDispatchRepository.dashboardCounts/saveDashboardSnapshot`; `api.DispatchDashboardController`, `DispatchReportController`; V20 `dispatch_dashboard_snapshots` | E2E #18 (counts reconcile to source: in-transit, custody-gap, open-exception) |
| **CT-05 Secure Dispatch** | end-to-end across the above | E2E #19 (examination manifest → seal → S166 vehicle/driver → dispatch → centre receipt → reconcile, no unexplained variance) |

## Cross-cutting acceptance

- Every operational record carries site scope, actor/time metadata, source channel and correlation ID.
- Mutable aggregates use optimistic locking and explicit state-transition methods (no generic status PATCH).
- Custody gaps, receipt variances, scan mismatches and return discrepancies open an SLA-controlled
  exception case and **block** dispatch/custody closure until resolved.
- Site-scoped authorization enforced on every list/detail/update/workflow/dashboard/evidence/export op.
- Verified by `mvn -pl sfl-fleet-logistics-service -am test` — **389 tests, 0 failures, 0 errors, 1
  skipped** (the placeholder `FleetPostgresEndToEndTest`), covering S166 fleet, S168 fuel and S171
  dispatch. The 36 S171 tests (19 E2E scenarios incl. CT-05, 13 domain, 2 event, 2 architecture) all
  pass against Testcontainers/`SFL_TEST_DB_URL` PostgreSQL. S166 + S168 regression suites remain green.
- Runtime endpoints (`/actuator/health`, `/v3/api-docs`, Swagger UI, `/dispatch/`) are wired and served
  in a normal environment; see the operations guide for the exact boot commands.
