# CLET Cluster 9 — Safety, Facilities & Logistics

The SFL Directorate platform for the Centre for Language and Educational Technology (CLET).
Phase 1 covers thirteen Fast-Track systems across four programmes:

| Programme  | What it covers                                   |
| ---------- | ------------------------------------------------ |
| **IFIMP**  | Integrated Facilities & Infrastructure Management |
| **SSEMP**  | Safety, Security & Emergency Management          |
| **FTLMP**  | Fleet, Transport & Logistics Management          |
| **AVAMP**  | Asset Visibility & Asset Management (Lite)       |

Five deployable Spring Boot services and one React dashboard. Each service owns its own schema,
its own migrations and its own API boundary — services talk through APIs and events, never through
each other's tables.

## Release 1 scope

Release 1 is closed as a **7-system demo build**, not the full 13-system Phase 1.

**Built and demoable**

| System | What                              |
| ------ | --------------------------------- |
| S152   | Facility management — CAFM / IWMS |
| S153   | Maintenance management — CMMS     |
| S159   | Room & resource booking           |
| S166   | Fleet & vehicle management        |
| S168   | Fuel management & driver logbooks |
| S171   | Mailroom, courier & dispatch      |
| S174   | Emergency mass notification       |

**Not in the demo** — S160 visitor management, S160a access control, S161 CCTV/VMS,
S162 intrusion, S162a fire & life safety, S163 HSE incident and near-miss.

S174 uses a recorded outbound adapter in this build. Real notification delivery is deferred to the
integration with the external Comms system.

## Layout

```
services/                            Java 17 · Spring Boot 4.1 · Maven multi-module
  sfl-facilities-service             IFIMP  — S152, S153, S159             :8091
  sfl-safety-security-service        SSEMP  — scaffold only                :8092
  sfl-fleet-logistics-service        FTLMP  — S166, S168, S171             :8093
  sfl-asset-visibility-service       AVAMP-Lite                            :8094
  sfl-emergency-notification-service S174                                  :8095
  sfl-service-common                 Shared kernel — principal, RBAC, error and event envelopes
frontend/sfl-operations-ui           React 19 · TypeScript · Vite — a client of the APIs, built
                                     and deployed separately from the services
scripts/                             Local development helpers
deploy/                              Deployment assets
```

S174 is a separate deployable rather than a module of another service because emergency
notification needs independent availability, callback, retry, degraded-mode and blast-radius
behaviour.

## The backend serves no user interface

The services expose APIs. Nothing more. There is no bundled dashboard, no static pages, and no
service that redirects a browser into one.

That is deliberate, so a front end can be replaced without touching the backend. The React
dashboard in `frontend/sfl-operations-ui` is one client of these APIs rather than part of them, and
anything that speaks HTTP can take its place.

The one thing this costs you: **CORS is now load-bearing.** While the dashboard was served from a
service origin, the browser never had to be persuaded to allow the calls. A UI on its own origin
does. Every service reads `SFL_CORS_ALLOWED_ORIGINS`, and a front end whose origin is not in that
list fails in the browser while every equivalent `curl` succeeds — a genuinely confusing way to lose
an afternoon.

## Quick start

One command starts the databases and the backend services, each in its own window:

```powershell
.\start-backend.ps1
```

| Service      | Port   | Systems                                |
| ------------ | ------ | -------------------------------------- |
| `facilities` | `8091` | S152 CAFM/IWMS, S153 CMMS, S159 booking |
| `fleet`      | `8093` | S166 fleet, S168 fuel, S171 dispatch    |
| `emergency`  | `8095` | S174 emergency notification             |

Each one serves `/api/v1` for its API, `/swagger-ui.html` for the docs, `/v3/api-docs` for the
OpenAPI document and `/actuator/health` for health. `/` redirects to the Swagger page.

There is no sign-in step locally (`sfl.security.enabled=false`); a client sends `X-SFL-*` actor
headers instead.

Useful switches:

```powershell
.\start-backend.ps1 -Services fleet              # one service
.\start-backend.ps1 -Services all                # all five
.\start-backend.ps1 -SkipDb                      # databases already running
.\start-backend.ps1 -UiOrigin http://localhost:4200   # let your front end through CORS
.\start-backend.ps1 -Secure                      # require authentication
```

## Plugging in a user interface

Point it at the ports above and make sure the backend allows its origin:

```powershell
.\start-backend.ps1 -UiOrigin http://localhost:4200
```

Every request needs the actor headers the services read — `X-SFL-User`, `X-SFL-Display-Name`,
`X-SFL-Roles`, `X-SFL-Sites` — plus `X-Correlation-ID` on every call and `Idempotency-Key` on
state-creating POSTs. Read `X-Correlation-ID` back off responses and put it in error messages; it is
the only thing that ties a failure on screen to a line in a service log.

To run the existing React dashboard against the backend, with hot reload:

```powershell
.\scripts\dev\run-fleet-dev.ps1
```

That serves it on <http://localhost:5005>, which is already an allowed origin.

## Build and test

```powershell
docker compose -f compose.service-dbs.yml up -d
cd services
..\mvnw.cmd test
```

Details for each half are in [`services/README.md`](services/README.md) and
[`frontend/sfl-operations-ui/README.md`](frontend/sfl-operations-ui/README.md).

## Environment

Three things that have each cost somebody an afternoon.

**Java 17, and the machine default may not be it.** The reactor needs 17+. This machine's `PATH`
has carried `C:\Program Files\Zulu\zulu-11\bin`, and Maven picks that up silently and fails with
errors that never mention the JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
```

**A plain `mvnw test` skips the end-to-end suites and still reports green.** The suites are gated on
three environment variables, and CI fails the build if anything skipped. To reproduce CI locally,
start the databases and set:

```powershell
$env:SFL_FACILITIES_TEST_DB_URL = 'jdbc:postgresql://localhost:55441/sfl_facilities_migration_test'
$env:SFL_FLEET_LOGISTICS_TEST_DB_URL = 'jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e'
$env:SFL_EMERGENCY_NOTIFICATION_TEST_DB_URL = 'jdbc:postgresql://localhost:55445/sfl_emergency_notification_service_e2e'
```

The facilities one points at a **dedicated, empty** database on purpose: the migration suite asserts
a genesis audit hash of all zeros, so running it against a database with any history fails with four
errors that look like defects and are residue. Drop and recreate it if that happens.

**The "Invalid VCS root mapping" warning on project open.** `.idea/vcs.xml` acquires a Git root
mapping for `frontend/sfl-operations-ui`, which is neither a repository nor a submodule — it is a
directory inside this one. Remove the second `<mapping>` line, leaving only the project-root
mapping. `.idea/` is gitignored, so this recurs for everyone who opens the project.

## Build vs buy

Build CLET-owned workflow, governance, records, APIs, dashboards, audit and evidence references, and
integration adapters.

Buy or integrate specialist platforms and hardware: CCTV/VMS, access-control devices, biometric
readers, fire and life-safety panels, intrusion panels, SMS/voice/signage providers, fuel provider
feeds, GPS/telematics, and RFID/barcode devices.

Shared ecosystem services — IAM, API gateway, notification, audit and evidence, integration gateway
and event broker, reporting, document and object storage — are integrated through adapters and are
not duplicated inside SFL services.

## A note on documentation

The SRS, the architecture decision records, the per-system design notes, gap reports and runbooks
are maintained outside this repository. The requirement source of truth is the Cluster 9 SFL Phase 1
SRS; the parent CLET digital system mapping remains authoritative for system IDs, Cluster 9 scope
and phase classification.
