# SFL Java Backend

This repository is being realigned to the updated SFL SRS and Phase 1 microservices workflow plan.

The active target architecture is five deployable Spring Boot microservices. The original Phase 1 workplan
started with four deployables, and S174 is recorded as a controlled deviation in ADR 0004 because emergency
notification needs independent availability, callback, retry, degraded-mode and blast-radius behavior.

- `services/sfl-facilities-service` for S152, S153 and S159.
- `services/sfl-safety-security-service` for S160, S160a, S161, S162, S162a and S163.
- `services/sfl-fleet-logistics-service` as the service artifact for S166 Fleet and Vehicle Management, with
  S168_fuel and S171 treated as separate future modules under the same technical boundary.
- `services/sfl-asset-visibility-service` for AVAMP-Lite and future S168 asset tagging/RFID/barcode inventory.
- `services/sfl-emergency-notification-service` for S174 Emergency Mass Notification.

The previous .NET implementation has been removed from this project. The older single Spring Boot app under `src/main` is retained only as Java migration/reference material while its IFIMP vertical slice is moved into the new service layout.

## Running Fleet & Vehicle Management locally

One command starts the databases, builds the SFL Operations dashboard and runs the Fleet service,
which then serves the API, Swagger and the dashboard together on port 8093:

```powershell
.\start-fleet.ps1
```

| URL                                     | What               |
| --------------------------------------- | ------------------ |
| <http://localhost:8093/ui/>             | Operations dashboard |
| <http://localhost:8093/swagger-ui.html> | Swagger UI         |
| <http://localhost:8093/v3/api-docs>     | OpenAPI JSON       |

`http://localhost:8093/` redirects to the dashboard. There is no sign-in step locally
(`sfl.security.enabled=false`); the dashboard sends the `X-SFL-*` actor headers instead.

For front-end work with hot reload, use `.\scripts\dev\run-fleet-dev.ps1`, which runs the service
on 8093 and the dashboard on 5005. Details are in `frontend/sfl-operations-ui/README.md`.

## Source Documents

- `docs/System Mappings and SRS/SFL_SRS.docx`
- `docs/System Mappings and SRS/SFL_Phase1_Microservices_Build_Workflow_Plan.md`
- `docs/System Mappings and SRS/CLET_Comprehensive_Digital_System_Mapping_v2.docx`
- `docs/System Mappings and SRS/CLET_Cluster9_FSL_System_Architecture_Document_FULLY_INTEGRATED.docx`
- `docs/System Mappings and SRS/SFL_Phase1_System_Architecture_Implementation_Guide_v2.md`

The parent mapping document remains the source of truth for system IDs, Cluster 9 scope and phase classification. The microservices workflow plan is the source of truth for implementation architecture.

## Build Foundation

```powershell
docker compose -f compose.service-dbs.yml up -d
cd services
..\mvnw.cmd test
```

## Local Service Databases

Each deployable owns a separate PostgreSQL database boundary. Start the local runtime and E2E databases with:

```powershell
docker compose -f compose.service-dbs.yml up -d
```

For single-service work, use `compose.facilities-db.yml`, `compose.safety-security-db.yml`,
`compose.fleet-db.yml`, `compose.asset-visibility-db.yml` or `compose.emergency-db.yml`.

| Service | Runtime DB | Runtime port | E2E DB | E2E port |
|---|---|---:|---|---:|
| `sfl-facilities-service` | `sfl_facilities_service` | `5441` | `sfl_facilities_service_e2e` | `55441` |
| `sfl-safety-security-service` | `sfl_safety_security_service` | `5442` | `sfl_safety_security_service_e2e` | `55442` |
| `sfl-fleet-logistics-service` | `sfl__fleet_vehicle_service` | `5443` | `sfl__fleet_vehicle_service_e2e` | `55443` |
| `sfl-asset-visibility-service` | `sfl_asset_visibility_service` | `5444` | `sfl_asset_visibility_service_e2e` | `55444` |
| `sfl-emergency-notification-service` | `sfl_emergency_notification_service` | `5445` | `sfl_emergency_notification_service_e2e` | `55445` |

## S174 Emergency Notification

Local S174 entry points:

- Service: `services/sfl-emergency-notification-service`
- Port: `8095`
- `http://localhost:8095/emergency/` redirects to the dashboard's emergency screens. Configure the
  target with `sfl.dashboard.base-url` — only the fleet service packages the bundle. See
  [ADR 0006](docs/adr/0006-one-dashboard-and-the-retirement-of-the-per-service-pages.md)
- Swagger UI: `http://localhost:8095/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8095/v3/api-docs`
- Health: `http://localhost:8095/actuator/health`

Local database support:

```powershell
docker compose -f compose.service-dbs.yml up -d
cd services
mvn -pl sfl-emergency-notification-service -am test
```

Each service owns its database schema, outbox, inbox, package boundary and deployable artifact. Shared ecosystem services such as IAM, API gateway, notification, audit/evidence, integration gateway/event broker, reporting and document/object storage are integrated through adapters and are not duplicated inside SFL services.

## Build vs Buy Rule

Build CLET-owned workflow, governance, records, APIs, dashboards, audit/evidence references and integration adapters. Buy or integrate specialist platforms/hardware such as CCTV/VMS, access-control devices, biometric readers, fire/life-safety panels, intrusion panels, SMS/voice/signage providers, fuel provider feeds, GPS/telematics and RFID/barcode devices.
