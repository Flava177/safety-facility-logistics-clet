# S166 Fleet Operations and Verification Guide

Date: 2026-07-22
Service: `services/sfl-fleet-logistics-service`
Branch: `fleet`

This guide describes how to run the S166 Fleet and Vehicle Management service locally, how to start the
PostgreSQL databases used by the service and E2E tests, and how to verify the module before review.

## 1. Java runtime

SFL services use Spring Boot `4.1.0` and compile with Java release `17`.

Recommended local setup:

- Keep Java 11 as the global default when working on SORMAS.
- Use Java 17 only in the SFL terminal or IntelliJ project.
- Use `use-sfl-env.ps1` from the repository root to set Java 17 and the Fleet DB variables for the current
  PowerShell session.

Verify the active SFL terminal:

```powershell
java -version
```

Expected major version:

```text
17
```

## 2. Local PostgreSQL databases

The repository includes `compose.fleet-db.yml` with two PostgreSQL containers:

| Container | Purpose | Host port | Database |
|---|---|---:|---|
| `sfl-fleet-vehicle-postgres` | Local app/runtime database | `5443` | `sfl__fleet_vehicle_service` |
| `sfl-fleet-vehicle-e2e-postgres` | E2E verification database | `55443` | `sfl__fleet_vehicle_service_e2e` |

The image is `postgres:16-bookworm` to stay close to production-like Debian-based PostgreSQL behaviour and
avoid Alpine-specific native-library surprises.

Start Docker Desktop first, then run from the repository root:

```powershell
docker compose -f compose.fleet-db.yml up -d
```

Check running containers:

```powershell
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}"
```

Stop containers while keeping local Fleet data:

```powershell
docker compose -f compose.fleet-db.yml down
```

Stop containers and delete local Fleet data:

```powershell
docker compose -f compose.fleet-db.yml down -v
```

Open the app database:

```powershell
docker exec -it sfl-fleet-vehicle-postgres psql -U sfl -d sfl__fleet_vehicle_service
```

Open the E2E database:

```powershell
docker exec -it sfl-fleet-vehicle-e2e-postgres psql -U sfl -d sfl__fleet_vehicle_service_e2e
```

## 3. Environment variables

Load the SFL local environment from the repository root:

```powershell
.\use-sfl-env.ps1
```

That script sets:

| Variable | Value |
|---|---|
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` |
| `SFL_FLEET_LOGISTICS_DB_URL` | `jdbc:postgresql://localhost:5443/sfl__fleet_vehicle_service` |
| `SFL_DB_USERNAME` | `sfl` |
| `SFL_DB_PASSWORD` | `sfl` |
| `SFL_FLEET_LOGISTICS_TEST_DB_URL` | `jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e` |
| `SFL_TEST_DB_USERNAME` | `sfl` |
| `SFL_TEST_DB_PASSWORD` | `sfl` |
| `SFL_RABBITMQ_HEALTH_ENABLED` | `false` |

These variables affect only the current PowerShell session. They do not change the global Java 11 setup used for
SORMAS.

## 4. Local API exploration

After starting the Fleet service from IntelliJ or Maven, the local review entry points are:

| Endpoint | Purpose |
|---|---|
| `http://localhost:8093/actuator/health` | Liveness/readiness health |
| `http://localhost:8093/api/v1/system/info` | Service metadata |
| `http://localhost:8093/swagger-ui.html` | Interactive Swagger API tester |
| `http://localhost:8093/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8093/ui/fleet` | Fleet screens in the SFL Operations dashboard |
| `http://localhost:8093/fleet/` | Redirects to the above. The page that used to be here was retired by ADR 0006 |

In Swagger UI, use **Authorize** to set the development actor headers used when `SFL_SECURITY_ENABLED=false`:

| Header | Suggested local value |
|---|---|
| `X-SFL-User` | `fleet.manager` |
| `X-SFL-Display-Name` | `Fleet Manager` |
| `X-SFL-Roles` | `FLEET_MANAGER,FLEET_OFFICER` |
| `X-SFL-Sites` | `HQ,ACCRA` |
| `X-SFL-Source-Channel` | `SWAGGER` |
| `X-Correlation-ID` | `swagger-local` |
| `Idempotency-Key` | A fresh unique value for each POST/PATCH request |

## 5. Verification commands

Run from the `services` reactor root:

```powershell
cd "C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL"
.\use-sfl-env.ps1
docker compose -f compose.fleet-db.yml up -d
cd services
mvn -pl sfl-fleet-logistics-service -am test
```

Expected result:

```text
BUILD SUCCESS
```

The current full fleet verification result is:

```text
Tests run: 423, Failures: 0, Errors: 0, Skipped: 0
```

The skipped test is the older Testcontainers auto-detection probe. The critical E2E suite uses
`SFL_FLEET_LOGISTICS_TEST_DB_URL` and ran all 16 scenarios against the local Docker PostgreSQL E2E database:

```text
FleetCriticalScenariosEndToEndTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

## 6. Running the Fleet service locally

Start the DB and load the environment:

```powershell
cd "C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL"
.\use-sfl-env.ps1
docker compose -f compose.fleet-db.yml up -d
cd services
mvn -pl sfl-fleet-logistics-service -am spring-boot:run
```

Default service port:

```text
8093
```

Open:

- `http://localhost:8093/fleet/index.html` — a notice page that redirects to `/ui/fleet`
- `http://localhost:8093/actuator/health`
- `http://localhost:8093/api/v1/system/info`

## 7. IntelliJ run configuration

Use the Spring Boot application class:

```text
gh.edu.clet.sfl.fleetlogistics.FleetLogisticsServiceApplication
```

Set the project SDK or run configuration JRE to Java 17.

Set the working directory to:

```text
C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL\services\sfl-fleet-logistics-service
```

Set environment variables in the run configuration:

```text
SFL_FLEET_LOGISTICS_DB_URL=jdbc:postgresql://localhost:5443/sfl__fleet_vehicle_service
SFL_DB_USERNAME=sfl
SFL_DB_PASSWORD=sfl
SFL_SECURITY_ENABLED=false
SFL_FLEET_EVENT_TRANSPORT=local
```

Start the Docker database before running the app.

## 8. Embedded Tomcat note

This service is a Spring Boot microservice. It should run with Spring Boot's embedded web server, not an external
Payara domain. Payara is appropriate for SORMAS' Jakarta EE/GlassFish-style setup, but SFL should be run as a
Spring Boot application. The embedded Tomcat server is started automatically by `spring-boot:run` or by running
`FleetLogisticsServiceApplication` from IntelliJ.
