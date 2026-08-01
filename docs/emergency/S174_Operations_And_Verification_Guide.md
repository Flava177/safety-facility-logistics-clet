# S174 Emergency Mass Notification - Operations and Verification Guide

S174 is implemented as `services/sfl-emergency-notification-service`, a separate Spring Boot 4.1 / Java 17
microservice with schema `emergency_notification` and default local port `8095`.

## Local prerequisites

- JDK 17.
- Maven 3.9+.
- PostgreSQL 16 for local runtime or full E2E verification.

Set Java 17 in PowerShell before running Maven if Java 11 is the global default:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## Run the service

```powershell
docker compose -f compose.emergency-db.yml up -d emergency-postgres

$env:SFL_EMERGENCY_NOTIFICATION_DB_URL='jdbc:postgresql://localhost:5445/sfl_emergency_notification_service'
$env:SFL_DB_USERNAME='sfl'
$env:SFL_DB_PASSWORD='sfl'
$env:SFL_SECURITY_ENABLED='false'

cd services
mvn -pl sfl-emergency-notification-service -am spring-boot:run
```

Runtime entry points:

| Endpoint | Purpose |
|---|---|
| `http://localhost:8095/actuator/health` | Health |
| `http://localhost:8095/swagger-ui.html` | API explorer |
| `http://localhost:8095/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8093/ui/emergency` | Emergency screens in the SFL Operations dashboard |
| `http://localhost:8095/emergency/` | Redirects to the above, via `sfl.dashboard.base-url`. The page that used to be here was retired by ADR 0006 |

Development actor headers:

```text
X-SFL-User=emergency.coordinator
X-SFL-Display-Name=Emergency Coordinator
X-SFL-Roles=EMERGENCY_COORDINATOR,SECURITY_DIRECTOR
X-SFL-Sites=ACCRA
X-SFL-Source-Channel=WEB
X-Correlation-ID=local-emergency
```

## Verification

Run from the `services` reactor:

```powershell
cd services
mvn -pl sfl-emergency-notification-service -am test
```

For database-backed E2E verification on hosts where Testcontainers cannot reach Docker, provide a PostgreSQL
database explicitly:

```powershell
docker compose -f compose.emergency-db.yml up -d emergency-e2e-postgres

$env:SFL_EMERGENCY_NOTIFICATION_TEST_DB_URL='jdbc:postgresql://localhost:55445/sfl_emergency_notification_service_e2e'
$env:SFL_TEST_DB_USERNAME='sfl'
$env:SFL_TEST_DB_PASSWORD='sfl'
mvn -pl sfl-emergency-notification-service -am test
```

Current local verification result:

```text
S174 reactor: BUILD SUCCESS
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
```

The PostgreSQL E2E scenarios ran against the supplied emergency notification Postgres container on `localhost:55445`.
Release 1 uses the recorded outbound notification adapter; live delivery is deferred to the later CLET Comms integration.

## S166/S168/S171 regression gate

The S174 slice only changes shared RBAC enums additively. Re-run the existing fleet/fuel/dispatch critical gate:

```powershell
cd services
mvn -pl sfl-fleet-logistics-service -am `
  '-Dtest=FleetCriticalScenariosEndToEndTest,FuelCriticalScenariosEndToEndTest,DispatchMandatoryScenariosEndToEndTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Current local frontend result:

```text
Full services reactor: BUILD SUCCESS
Surefire reports: tests=452, failures=0, errors=0, skipped=1
```

The one skipped test is an existing Docker/Testcontainers-gated probe outside S174.

## Idempotent activation creation

`POST /api/v1/emergency/activations` and `POST /api/v1/emergency/activations/break-glass` persist
`Idempotency-Key` results in `emergency_notification.command_idempotency_keys`. A retry with the same key and
same payload returns the original activation; a retry with a changed payload fails with
`EMERGENCY_IDEMPOTENCY_KEY_CONFLICT`. Provider callbacks remain idempotent through the secure integration inbox.
