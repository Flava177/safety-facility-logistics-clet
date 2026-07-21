# SFL Microservices Realignment Plan

Date: July 2026

## Assessment

The repository had three overlapping implementation directions:

1. A legacy .NET implementation under `src/SFL.*`, `tests/SFL.*`, `SFL.slnx` and Visual Studio artifacts.
2. A single Spring Boot application under `src/main` with IFIMP facilities and maintenance code.
3. The updated SRS/workflow decision requiring four deployable Spring Boot microservices.

The .NET implementation has now been removed from this Java project. The single Spring Boot app remains only as migration/reference material until its useful IFIMP code is moved into `services/sfl-facilities-service`.

## Gap List Against Updated SRS / Workflow Plan

- The generated SRS still contains earlier wording that describes one Spring Boot modular platform. That is outdated.
- The root Maven project is still a single application and should not be treated as the target architecture.
- IFIMP code exists only in the old single-app package layout.
- Safety/security, fleet/logistics and asset visibility service foundations did not exist before this realignment.
- Service-local migration, outbox and idempotent inbox conventions needed to be established per microservice.
- Docker/local development needs to move from one app to four service artifacts.

## Replacement Wording For SRS

Replace any SRS wording equivalent to:

> The implementation baseline is a Spring Boot modular monolith with bounded contexts aligned to SFL.IFIMP, SFL.SSEMP, SFL.FTLMP and SFL.AVAMP-Lite.

with:

> The implementation baseline is a Spring Boot microservices architecture composed of four deployable bounded-context services: `sfl-facilities-service`, `sfl-safety-security-service`, `sfl-fleet-logistics-service` and `sfl-asset-visibility-service`. Each service owns its database schema, migrations, API boundary, domain model, outbox and idempotent inbox. Cross-service workflows use APIs, events and sagas through the wider CLET microservices ecosystem; services must not share databases or depend on another service's internal persistence model.

Replace any SRS wording equivalent to:

> SFL is implemented as one backend artifact or one deployable SFL backend.

with:

> SFL Phase 1 is implemented as four deployable Spring Boot service artifacts, integrated through the enterprise API gateway, IAM, event broker, audit/evidence, notification, reporting and document/object-storage services.

## Target Structure

```text
services/
  pom.xml
  sfl-service-common/
  sfl-facilities-service/
  sfl-safety-security-service/
  sfl-fleet-logistics-service/
  sfl-asset-visibility-service/
```

Each deployable service follows:

```text
src/main/java/gh/edu/clet/sfl/{service}/
  api/
  application/command/
  application/query/
  application/workflow/
  application/port/
  domain/model/
  domain/event/
  domain/policy/
  infrastructure/persistence/
  infrastructure/messaging/
  infrastructure/integration/
  infrastructure/security/
src/main/resources/db/migration/
```

## Step-by-Step Implementation Sequence

1. Remove legacy .NET implementation files from the Java project.
2. Add the four-service Maven workspace under `services/`.
3. Add shared conventions for DTOs, errors, site-scoped principals and integration event envelopes.
4. Add service-local Flyway migrations for metadata, outbox and inbox tables.
5. Verify the service workspace builds independently.
6. Migrate the existing IFIMP facility/fault Java vertical slice into `sfl-facilities-service`.
7. Add AVAMP-Lite asset/device reference APIs before other services depend on device IDs.
8. Add safety/security service foundations and purchased-system adapter ports.
9. Add fleet/logistics service foundations and fuel/dispatch workflows.
10. Add cross-service sagas for hall readiness, emergency incident response and secure dispatch.
11. Add local Docker Compose for the four services plus Postgres, broker, Redis and IAM.
12. Add contract, architecture, API, integration and event tests.

## Current Foundation Changes

- Removed .NET source/test/project artifacts from the IntelliJ project.
- Added `services/` Maven workspace for the four SFL microservices.
- Added `sfl-service-common` shared convention module.
- Added one service-local `V1__service_foundation.sql` migration per deployable service.
- Updated root README and root app description to make the old single app a migration reference, not the target architecture.
