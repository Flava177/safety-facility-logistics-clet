# S171 Mailroom / Courier & Dispatch Tracking — Operations & Verification Guide

S171 is an extraction-ready feature package inside the existing `sfl-fleet-logistics-service` deployable
(schema `fleet_logistics`, base package `gh.edu.clet.sfl.fleetlogistics.dispatch`). It reuses the S166
audit hash-chain, governed evidence, secure integration inbox, transactional outbox, notifications,
runtime configuration and OpenAPI foundations — it does not create a parallel stack.

## 1. Local prerequisites

- JDK 17 (the module targets `release 17`).
- Maven (or the bundled `services/mvnw`). Run reactor commands from `services/`.
- PostgreSQL 16. The service defaults to `jdbc:postgresql://localhost:5443/sfl__fleet_vehicle_service` (user/pass `sfl`).

```bash
docker run -d --name sfl-fleet-vehicle-db -p 5443:5432 \
  -e POSTGRES_USER=sfl -e POSTGRES_PASSWORD=sfl -e POSTGRES_DB=sfl__fleet_vehicle_service postgres:16-bookworm
```

## 2. Run the service

```bash
cd services
SFL_SECURITY_ENABLED=false mvn -pl sfl-fleet-logistics-service -am spring-boot:run
```

Flyway applies `V16`–`V20` on top of the S166 (`V1`–`V9_1`) and S168 (`V10`–`V15`) migrations. With
`SFL_SECURITY_ENABLED=false` the service uses the `X-SFL-*` development actor headers; production uses the
OIDC/JWT resource server unchanged.

## 3. Runtime verification checklist

| Check | URL / command | Expected |
|---|---|---|
| Health | `GET http://localhost:8093/actuator/health` | `{"status":"UP"}` |
| OpenAPI JSON | `GET http://localhost:8093/v3/api-docs` | OpenAPI document incl. the nine Dispatch tags |
| Swagger UI | `http://localhost:8093/swagger-ui.html` | Dispatch Items, Inbound Mail, Dispatch Manifests, Chain of Custody, Dispatch Receipts, Return Reconciliation, Dispatch Exceptions, Dispatch Integrations, Dispatch Dashboards and Reports |
| Dashboard | `http://localhost:8093/ui/dispatch` | Courier & dispatch screens in the SFL Operations dashboard |
| Retired route | `http://localhost:8093/dispatch/` | Redirects to the dashboard. The page that used to be here was retired by ADR 0006 |
| PostgreSQL | `psql -h localhost -p 5443 -U sfl -d sfl__fleet_vehicle_service -c '\dt fleet_logistics.courier_items'` | table present |

Dev headers for Swagger "Authorize" or the dashboard's actor configuration:

```
X-SFL-User=dispatch.controller
X-SFL-Roles=DISPATCH_CONTROLLER
X-SFL-Sites=ACCRA
X-SFL-Source-Channel=WEB
```

`FLEET_MANAGER` / `SFL_ADMIN` hold every dispatch permission; the SRS user classes map through
`DispatchPermissionMatrix` (DISPATCH_CONTROLLER, LOGISTICS_COORDINATOR, CENTRE_MANAGER, MAILROOM_OFFICER,
SECURITY_OFFICER, AUDITOR, COMPLIANCE_OFFICER, DTI_ADMIN, INTEGRATION_ENGINEER, SERVICE_INTEGRATION).

## 4. A CT-05 smoke walk-through (dashboard or API)

1. **Register** two `EXAMINATION_PAPER` / `SECRET` items — `POST /api/v1/dispatch/items`.
2. **Create** a manifest — `POST /api/v1/dispatch/manifests` — then **add** both items (`/items`),
   **seal** with seal IDs (`/seal`), optionally **assign** an S166 vehicle/driver (`/assign-trip`), and
   **dispatch** (`/dispatch`).
3. **Record custody** `WAREHOUSE_STAGING → DISPATCH → CENTRE_RECEIPT` (all `INTACT`, verified count = 2) —
   `POST /api/v1/dispatch/custody`. Inspect `GET /api/v1/dispatch/custody/{dispatchId}/gaps`.
4. **Confirm receipt** (seal `INTACT`, count 2, signature) — `POST /api/v1/dispatch/receipts`.
5. **Reconcile the return** (`returnedCount = 2`) — `POST /api/v1/dispatch/returns/reconcile`.
6. **Close** — `POST /api/v1/dispatch/manifests/{id}/close`. A broken seal, count mismatch, receipt
   variance or return discrepancy opens an exception case and blocks step 6 until resolved.

## 5. Test & regression gate

```bash
cd services
# Point the E2E at a PostgreSQL (recommended on hosts where Testcontainers' Docker auto-detection is flaky):
docker run -d --name sfl-fleet-vehicle-e2e -p 55443:5432 -e POSTGRES_USER=sfl -e POSTGRES_PASSWORD=sfl \
  -e POSTGRES_DB=sfl__fleet_vehicle_service_e2e postgres:16-bookworm
export SFL_FLEET_LOGISTICS_TEST_DB_URL=jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e
export SFL_TEST_DB_USERNAME=sfl SFL_TEST_DB_PASSWORD=sfl
mvn -pl sfl-fleet-logistics-service -am test
```

Last run: **389 tests, 0 failures, 0 errors, 1 skipped** (the placeholder `FleetPostgresEndToEndTest`).
The S171 suite contributes 36: `DispatchMandatoryScenariosEndToEndTest` (19 scenarios incl. CT-05),
`DispatchDomainTest` (13), `DispatchEventCatalogTest` (2), `DispatchArchitectureTest` (2). If no
PostgreSQL is reachable the E2E class is skipped with a reason (never a false pass).

## 6. Scheduled sweeps

`DispatchSweepScheduler` runs undelivered-inbound, outstanding-return, exception-SLA, dashboard-refresh
and stale-integration sweeps every `sfl.dispatch.scheduling.fixed-delay` (default `PT5M`). Each sweep is
individually toggled by runtime configuration (`dispatch.scheduling.*-enabled`) and every action is
idempotent (stable occurrence keys, flag guards, snapshot upsert), so a re-run never duplicates exception
cases, notifications, audit entries or outbox messages. Disable the whole scheduler with
`SFL_DISPATCH_SCHEDULER=false`. Runtime thresholds seeded in V20:

| Key | Default | Meaning |
|---|---|---|
| `dispatch.undelivered.window` | `PT48H` | Inbound undelivered/unclaimed escalation window |
| `dispatch.outstanding-return.window` | `P3D` | Not-yet-returned escalation window |
| `dispatch.exception.sla.default` | `PT24H` | Base exception SLA (severity-adjusted) |
| `dispatch.dashboard.freshness-threshold` | `PT15M` | Dashboard stale-data threshold |

## 7. Known environment note

On a sandboxed host that blocks loopback TCP, embedded Tomcat can fail to bind its NIO connector
(`SocketException: Invalid argument` on the loopback pipe) — the application context, Flyway, JDBC and all
beans are still proven by the `@SpringBootTest` suite; only the live HTTP bind is affected. On a normal
host the service binds `:8093` and serves all endpoints in the table above.
