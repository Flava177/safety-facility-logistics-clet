# sfl-fleet-logistics-service

Spring Boot service artifact that currently delivers `S166 Fleet and Vehicle Management`.

Naming note: the artifact path remains `sfl-fleet-logistics-service` because the Phase 1 architecture groups
S166, S168_fuel and S171 under the same deployable service boundary. This S166 deliverable is Fleet and Vehicle
Management only; Logistics/dispatch work is a separate module and is not included here.

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
SFL_DB_URL=jdbc:postgresql://localhost:5434/sfl_fleet_db
SFL_DB_USERNAME=sfl
SFL_DB_PASSWORD=sfl
SFL_SECURITY_ENABLED=false
SFL_FLEET_EVENT_TRANSPORT=local
```

The service uses Spring Boot's embedded web server. No external Payara/Tomcat installation is required.

## Fleet schedulers

| Scheduler | Property | Default |
|---|---|---|
| Outbox drain | `SFL_FLEET_OUTBOX_SCHEDULER` | `true` |
| SLA evaluation | `SFL_FLEET_SLA_SCHEDULER` | `true` |
| Compliance/service sweep | `SFL_FLEET_COMPLIANCE_SCHEDULER` | `true` |
| Compliance/service sweep cron | `SFL_FLEET_COMPLIANCE_CRON` | `0 5 1 * * *` |
| Dashboard refresh | `SFL_FLEET_DASHBOARD_SCHEDULER` | `true` |

