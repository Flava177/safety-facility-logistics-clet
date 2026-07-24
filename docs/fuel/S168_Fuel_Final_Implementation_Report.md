# S168_fuel Final Implementation Report

## Requirement status

| Requirement | Status | Evidence / remaining work |
|---|---|---|
| SRS-SFL-S168fuel-01 | In progress | Core records are implemented; complete lifecycle, pagination and API/site-scope verification remain open |
| SRS-SFL-S168fuel-02 | In progress | Core transitions and sweeps exist; complete rule coverage, hold/reassignment/cancellation and notifications remain open |
| SRS-SFL-S168fuel-03 | In progress | Audit-port calls and evidence references exist; governed evidence/export and tamper-path verification remain open |
| SRS-SFL-S168fuel-04 | In progress | Provider-neutral ingestion/import and events exist; replay, Finance/Audit delivery and retry/dead-letter proof remain open |
| SRS-SFL-S168fuel-05 | In progress | Basic totals, CSV and console exist; required snapshots, filters, drilldowns and screens remain open |

## Delivered design

The extraction-ready `gh.edu.clet.sfl.fleetlogistics.fuel` feature contains domain, application ports/services,
JDBC persistence, a Fleet reference/odometer adapter, CSV adapter, scheduler and REST/UI adapters. S166 remains
authoritative for vehicles, drivers, trips and accepted odometer state. V10–V14 add fuel data without altering
applied Fleet migrations. V15 strengthens trip-based missing-logbook anomaly idempotency without changing an
already-applied migration.

Swagger is at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`, and the operational console at `/fuel/`.
Twenty additive permissions cover driver, officer, manager, reporting, audit, compliance, administration and
integration roles. Card references are masked, money uses decimal arithmetic, and core workflow transitions are explicit.

## Known external dependencies

- Production provider HMAC/mTLS field mapping awaits vendor selection.
- Finance acknowledgement/materiality contracts await Finance/Audit approval.
- Institutional numeric limits and retention periods must replace local policy values before go-live.
- Native offline mobile is not Phase 1 scope; the delivered console is responsive and browser-based.

## Verification record

The first complete reactor run executed 334 tests with zero failures/errors and one Testcontainers-only skip.
The configured PostgreSQL E2E suite validated 16 migrations and applied V10–V14 successfully. A later
database-backed S168 critical workflow was added together with V15; its final reactor rerun remains mandatory
because the execution environment reached its command-approval usage limit. The module remains uncommitted.

## Definition-of-Done decision

**Not ready to close or commit.** The exact open implementation and verification rows are maintained in
`S168_Fuel_Gap_And_Conflict_Report.md`. No S168 completion claim should be made until those rows are closed,
the 17 mandatory scenarios are proven, the application endpoints/UI are smoke-tested, and the full reactor is green.

---

## Addendum — backend completion pass (2026-07-23)

> Status remains **in progress**. The changes below were written to match the S166 foundations
> verbatim but were **not compiled or executed in the authoring environment** (no JDK 17/Maven).
> Build and run the verification commands locally before treating any item as done.

### New / changed source

| File | Change |
|---|---|
| `fuel/domain/model/FuelAnomalyCase.java` | Added `reassign`, `hold`, `resume`, `cancel` transitions (reuse `HELD`/`CANCELLED`). |
| `fuel/application/port/FinanceAuditVisibilityPort.java` | New outbound seam for material exceptions. |
| `fuel/infrastructure/integration/RecordedFinanceAuditAdapter.java` | Publishes material exceptions via the outbox. |
| `fuel/application/port/FuelOutboxAdminPort.java` | New outbox health/replay port. |
| `fuel/infrastructure/integration/OutboxAdminAdapter.java` | Wraps shared `OutboxMessageRepository`/`OutboxDrainer`. |
| `fuel/application/port/FuelRepository.java` (+ `JdbcFuelRepository`) | Added `findPreviousTransaction`, `countRecentAnomalies`, `findLogbookForTrip`. |
| `fuel/application/service/FuelApplicationService.java` | Wired `NotificationPort`, finance visibility, outbox admin; new anomaly transitions + notifications; expanded reconciliation rules; persisted computed consumption. |
| `fuel/application/service/FuelAccessPolicy.java` | Added `requirePermission` (permission-only guard for cross-site integration admin). |
| `fuel/api/FuelAnomalyController.java` | Exposed `reassign|hold|resume|cancel`. |
| `fuel/api/FuelProviderIntegrationController.java` | Added `GET /outbox/health`, `POST /outbox/{id}/replay`. |
| `fuel/domain/FuelDomainTest.java` | Added coverage for the new anomaly transitions. |

### New API endpoints

| Method | Path | Permission |
|---|---|---|
| `POST` | `/api/v1/fuel/anomalies/{id}/reassign` | `FUEL_ANOMALY_MANAGE` |
| `POST` | `/api/v1/fuel/anomalies/{id}/hold` | `FUEL_ANOMALY_MANAGE` |
| `POST` | `/api/v1/fuel/anomalies/{id}/resume` | `FUEL_ANOMALY_MANAGE` |
| `POST` | `/api/v1/fuel/anomalies/{id}/cancel` | `FUEL_ANOMALY_MANAGE` |
| `GET`  | `/api/v1/fuel/integrations/outbox/health` | `FUEL_INTEGRATION_REPLAY` |
| `POST` | `/api/v1/fuel/integrations/outbox/{messageId}/replay` | `FUEL_INTEGRATION_REPLAY` |

### Reconciliation rules now evaluated and stored in `fuel_reconciliations.rule_results`

`MAX_PER_TRANSACTION`, `TANK_CAPACITY`, `FUEL_PRODUCT`, `APPROVED_VENDOR`, `DRIVER_ELIGIBLE`,
`VEHICLE_OPERATIONAL`, `TRIP_MATCH`, `ODOMETER_NON_REGRESSION`, `ODOMETER_JUMP`, `RECEIPT`,
`CONSUMPTION_RANGE` → `ABNORMAL_CONSUMPTION`, `COST_VARIANCE`, `LOGBOOK_MATCH` → `LOGBOOK_MISMATCH`,
`REPEATED_PATTERN` → `UNUSUAL_PATTERN`.

### Known limitations carried forward
Card/allocation management (S168_fuel-04), rolling daily/weekly/monthly limits, governed evidence
export for fuel, dashboard drilldowns, page/cursor pagination, the full 17-scenario E2E suite, and the
remaining UI screens are not yet delivered. `COST_VARIANCE`/`REPEATED_PATTERN` thresholds are
documented default constants pending versioned-policy columns.
