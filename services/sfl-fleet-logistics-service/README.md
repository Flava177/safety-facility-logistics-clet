# sfl-fleet-logistics-service

Spring Boot service artifact that delivers `S166 Fleet and Vehicle Management`,
`S168_fuel Fuel Management and Driver Logbooks` and
`S171 Mailroom / Courier and Dispatch Tracking`.

Naming note: the artifact path remains `sfl-fleet-logistics-service` because the Phase 1 architecture groups
S166, S168_fuel and S171 under the same deployable service boundary. S171 is the courier/dispatch feature
package `gh.edu.clet.sfl.fleetlogistics.dispatch` inside this deployable (schema `fleet_logistics`).

## S171 review entry points

- Operational console: `http://localhost:8093/dispatch/`
- Swagger UI: `http://localhost:8093/swagger-ui.html` — Dispatch Items, Inbound Mail, Dispatch Manifests,
  Chain of Custody, Dispatch Receipts, Return Reconciliation, Dispatch Exceptions, Dispatch Integrations,
  Dispatch Dashboards and Reports
- OpenAPI JSON: `http://localhost:8093/v3/api-docs`
- REST base path: `/api/v1/dispatch`
- Final implementation report: `docs/dispatch/S171_Final_Implementation_Report.md`
- Operations and verification guide: `docs/dispatch/S171_Operations_And_Verification_Guide.md`
- Requirement traceability matrix: `docs/dispatch/S171_Requirement_Traceability_Matrix.md`
- Migrations: `V16`–`V20` (schema `fleet_logistics`); runtime toggle `SFL_DISPATCH_SCHEDULER`.

## S168_fuel review entry points

- Operational console: `http://localhost:8093/fuel/`
- Swagger: `http://localhost:8093/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8093/v3/api-docs`
- Requirements and final report: `docs/fuel/`

Boundary rules:
- Own this service's database schema only: `fleet_logistics`.
- Publish cross-service changes through the service outbox.
- Consume external events idempotently through the service inbox.
- Store vendor payloads through adapters; do not leak vendor models into domain packages.
- Store evidence file references and hashes only; do not store large files or CCTV video in this database.

## S166 review entry points

- Final implementation report: `docs/fleet/S166_Final_Implementation_Report.md`
- Requirement traceability matrix: `docs/fleet/S166_Requirement_Traceability_Matrix.md`
- API inventory: `docs/fleet/S166_API_Inventory.md`
- Test plan: `docs/fleet/S166_Test_Plan.md`
- Operations and verification guide: `docs/fleet/S166_Operations_And_Verification_Guide.md`
- Operational console: `/fleet/index.html`
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

## Local verification

Run from the `services` reactor root:

```powershell
cd "C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL"
.\use-sfl-env.ps1
docker compose -f compose.fleet-db.yml up -d
cd services
mvn -pl sfl-fleet-logistics-service -am test -q
```

The critical PostgreSQL E2E suite uses `SFL_TEST_DB_URL` and runs against the local Docker E2E database. The older
Testcontainers probe may skip on Docker Desktop environments where the Java Docker client cannot auto-detect a
valid Docker API endpoint.

## Local app startup

Start the Fleet service from IntelliJ by running:

```text
gh.edu.clet.sfl.fleetlogistics.FleetLogisticsServiceApplication
```

Use Java 17 and these environment variables:

```text
SFL_DB_URL=jdbc:postgresql://localhost:5444/sfl_fleet_db
SFL_DB_USERNAME=sfl
SFL_DB_PASSWORD=sfl
SFL_SECURITY_ENABLED=false
SFL_FLEET_EVENT_TRANSPORT=local
SFL_FLEET_NOTIFICATION_PROVIDER=recorded
SFL_RABBITMQ_HEALTH_ENABLED=false
```

The service uses Spring Boot's embedded web server. No external Payara/Tomcat installation is required.

Default local ports:

| Component | Port |
|---|---:|
| Fleet app | `8093` |
| Fleet PostgreSQL | `5444` |
| Fleet E2E PostgreSQL | `55432` |

## Swagger/OpenAPI

With the Fleet service running locally, open:

```text
http://localhost:8093/swagger-ui.html
http://localhost:8093/v3/api-docs
```

Swagger groups the S166 Fleet and Vehicle Management endpoints into System, Vehicles, Drivers, Trips, Workflow,
Evidence, Audit, Integrations, and Dashboards and Reports.

For local testing, click **Authorize** in Swagger UI and enter development header values such as:

| Header | Example |
|---|---|
| `X-SFL-User` | `fleet.manager` |
| `X-SFL-Display-Name` | `Fleet Manager` |
| `X-SFL-Roles` | `FLEET_MANAGER,FLEET_OFFICER` |
| `X-SFL-Sites` | `HQ,ACCRA` |
| `X-SFL-Source-Channel` | `SWAGGER` |
| `X-Correlation-ID` | `swagger-local` |
| `Idempotency-Key` | A fresh unique value for each mutating request |

## Fleet schedulers

| Scheduler | Property | Default |
|---|---|---|
| Outbox drain | `SFL_FLEET_OUTBOX_SCHEDULER` | `true` |
| SLA evaluation | `SFL_FLEET_SLA_SCHEDULER` | `true` |
| Compliance/service sweep | `SFL_FLEET_COMPLIANCE_SCHEDULER` | `true` |
| Compliance/service sweep cron | `SFL_FLEET_COMPLIANCE_CRON` | `0 5 1 * * *` |
| Dashboard refresh | `SFL_FLEET_DASHBOARD_SCHEDULER` | `true` |

