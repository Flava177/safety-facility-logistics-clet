# SFL Emergency Mass Notification Service

S174 Emergency Mass Notification is a separate Spring Boot deployable for SFL.SSEMP / Emergency Communications.
It owns schema `emergency_notification` and integrates with other SFL services through APIs, events and recorded
provider-neutral ports only.

## Local URLs

| URL | Purpose |
|---|---|
| `http://localhost:8095/emergency/` | Service-served page, superseded by the dashboard (ADR 0006) |
| `http://localhost:8095/swagger-ui.html` | Swagger UI |
| `http://localhost:8095/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8095/actuator/health` | Health |

## Run Locally

```powershell
cd "C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL"
docker compose -f compose.service-dbs.yml up -d emergency-notification-postgres

$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:SFL_EMERGENCY_NOTIFICATION_DB_URL='jdbc:postgresql://localhost:5445/sfl_emergency_notification_service'
$env:SFL_DB_USERNAME='sfl'
$env:SFL_DB_PASSWORD='sfl'
$env:SFL_SECURITY_ENABLED='false'

cd services
mvn -pl sfl-emergency-notification-service -am spring-boot:run
```

## Verify

```powershell
cd "C:\Users\Daniel Adjei\Documents\CLET\Projects\SFL\SFL"
docker compose -f compose.service-dbs.yml up -d emergency-notification-e2e-postgres
$env:SFL_EMERGENCY_NOTIFICATION_TEST_DB_URL='jdbc:postgresql://localhost:55445/sfl_emergency_notification_service_e2e'
$env:SFL_TEST_DB_USERNAME='sfl'
$env:SFL_TEST_DB_PASSWORD='sfl'

cd services
mvn -pl sfl-emergency-notification-service -am test
```

Use `Idempotency-Key` on state-creating activation requests. The service replays duplicate create/break-glass
activation requests with the same payload and rejects key reuse with a different payload.
