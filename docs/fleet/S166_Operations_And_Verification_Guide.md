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
| `sfl-fleet-postgres` | Local app/runtime database | `5434` | `sfl_fleet_db` |
| `sfl-fleet-e2e-postgres` | E2E verification database | `55432` | `sfl_fleet_e2e_db` |

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
docker exec -it sfl-fleet-postgres psql -U sfl -d sfl_fleet_db
```

Open the E2E database:

```powershell
docker exec -it sfl-fleet-e2e-postgres psql -U sfl -d sfl_fleet_e2e_db
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
| `SFL_DB_URL` | `jdbc:postgresql://localhost:5434/sfl_fleet_db` |
| `SFL_DB_USERNAME` | `sfl` |
| `SFL_DB_PASSWORD` | `sfl` |
| `SFL_TEST_DB_URL` | `jdbc:postgresql://localhost:55432/sfl_fleet_e2e_db` |
| `SFL_TEST_DB_USERNAME` | `sfl` |
| `SFL_TEST_DB_PASSWORD` | `sfl` |

These variables affect only the current PowerShell session. They do not change the global Java 11 setup used for
SORMAS.

## 4. Verification commands

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
Tests run: 326, Failures: 0, Errors: 0, Skipped: 1
```

The skipped test is the older Testcontainers auto-detection probe. The critical E2E suite uses
`SFL_TEST_DB_URL` and ran all 16 scenarios against the local Docker PostgreSQL E2E database:

```text
FleetCriticalScenariosEndToEndTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

## 5. Running the Fleet service locally

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

- `http://localhost:8093/fleet/index.html`
- `http://localhost:8093/actuator/health`
- `http://localhost:8093/api/v1/system/info`

## 6. IntelliJ run configuration

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
SFL_DB_URL=jdbc:postgresql://localhost:5434/sfl_fleet_db
SFL_DB_USERNAME=sfl
SFL_DB_PASSWORD=sfl
SFL_SECURITY_ENABLED=false
SFL_FLEET_EVENT_TRANSPORT=local
```

Start the Docker database before running the app.

## 7. Embedded Tomcat note

This service is a Spring Boot microservice. It should run with Spring Boot's embedded web server, not an external
Payara domain. Payara is appropriate for SORMAS' Jakarta EE/GlassFish-style setup, but SFL should be run as a
Spring Boot application. The embedded Tomcat server is started automatically by `spring-boot:run` or by running
`FleetLogisticsServiceApplication` from IntelliJ.
