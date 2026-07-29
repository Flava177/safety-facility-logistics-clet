# SFL Microservices Realignment Plan

Date: July 2026

## Assessment

The repository had three overlapping implementation directions:

1. A legacy .NET implementation under `src/SFL.*`, `tests/SFL.*`, `SFL.slnx` and Visual Studio artifacts.
2. A single Spring Boot application under `src/main` with IFIMP facilities and maintenance code.
3. The updated SRS/workflow decision originally requiring four deployable Spring Boot microservices, now with
   S174 Emergency Mass Notification separated as a fifth deployable by ADR 0004.

The .NET implementation has now been removed from this Java project. The single Spring Boot app remains only as migration/reference material until its useful IFIMP code is moved into `services/sfl-facilities-service`.

## Gap List Against Updated SRS / Workflow Plan

- The generated SRS still contains earlier wording that describes one Spring Boot modular platform. That is outdated.
- The root Maven project is still a single application and should not be treated as the target architecture.
- IFIMP code exists only in the old single-app package layout.
- Safety/security, fleet/logistics and asset visibility service foundations did not exist before this realignment.
- Service-local migration, outbox and idempotent inbox conventions needed to be established per microservice.
- Docker/local development needs to move from one app to five service artifacts.

## Replacement Wording For SRS

Replace any SRS wording equivalent to:

> The implementation baseline is a Spring Boot modular monolith with bounded contexts aligned to SFL.IFIMP, SFL.SSEMP, SFL.FTLMP and SFL.AVAMP-Lite.

with:

> The implementation baseline is a Spring Boot microservices architecture composed of five deployable bounded-context services: `sfl-facilities-service`, `sfl-safety-security-service`, `sfl-fleet-logistics-service`, `sfl-asset-visibility-service` and `sfl-emergency-notification-service`. Each service owns its database schema, migrations, API boundary, domain model, outbox and idempotent inbox. Cross-service workflows use APIs, events and sagas through the wider CLET microservices ecosystem; services must not share databases or depend on another service's internal persistence model. S174 is separated from the original safety-security grouping by ADR 0004 for emergency notification availability, retry, callback, degraded-mode and blast-radius reasons.

Replace any SRS wording equivalent to:

> SFL is implemented as one backend artifact or one deployable SFL backend.

with:

> SFL Phase 1 is implemented as five deployable Spring Boot service artifacts, integrated through the enterprise API gateway, IAM, event broker, audit/evidence, notification, reporting and document/object-storage services.

## Programme, System and Service Map

Three counts that do not line up one-to-one, and each is real: **13 systems**, **4 programme modules**,
**5 deployable services**.

| Programme | Systems | Deployable service | Local port |
| --- | --- | --- | --- |
| **SFL.IFIMP** | S152 CAFM/IWMS, S153 CMMS, S159 Room & resource booking | `sfl-facilities-service` | 8091 |
| **SFL.SSEMP** | S160 Visitor, S160a Access control, S161 CCTV/VMS, S162 Intrusion & alarms, S162a Fire & life safety, S163 HSE incident | `sfl-safety-security-service` | 8092 |
| **SFL.SSEMP** | S174 Emergency mass notification | `sfl-emergency-notification-service` | 8095 |
| **SFL.FTLMP** | S166 Fleet & vehicle, S168 Fuel & driver logbooks, S171 Courier & dispatch | `sfl-fleet-logistics-service` | 8093 |
| **SFL.AVAMP** | cross-cutting asset and device reference for all 13 | `sfl-asset-visibility-service` | 8094 |

Read it in three directions, because each answers a different question:

- **A programme is not a service.** FTLMP is one deployable carrying three systems. SSEMP is **two**
  deployables carrying seven. Launching a programme means starting *its services*, plural where it is
  plural.
- **A service is not a system.** `sfl-fleet-logistics-service` holds three modules under
  `gh.edu.clet.sfl.fleetlogistics` — `fleet`, `fuel`, `dispatch`. Searching one package is not searching
  the service; searching one service is not searching the programme.
- **S174's split is a deployment decision, not a regrouping.** It remains SFL.SSEMP / Emergency
  Communications in every user-facing surface. ADR 0004 separated the *deployable* for availability,
  fast-lane latency, callback volume, retry isolation, degraded mode and blast radius — not the
  programme.

AVAMP is the odd one out on purpose: it is the asset and device reference layer supporting all 13
systems, not a fourteenth system. It should not become a duplicate asset register in Phase 1.

### What this means for launching

| Launch | Start |
| --- | --- |
| **IFIMP** | `sfl-facilities-service` |
| **SSEMP** | `sfl-safety-security-service` **and** `sfl-emergency-notification-service` |
| **FTLMP** | `sfl-fleet-logistics-service` |
| **AVAMP** | `sfl-asset-visibility-service` |

Plus the per-service Postgres instances (`compose.service-dbs.yml`) and, in a real environment, the
gateway, IAM, broker, audit/evidence and object storage.

### What this means for the operator

A driver or a head of fleet signs in and sees fleet, fuel and dispatch. They do **not** see CCTV access
management, intrusion detection or visitor badges — those are SSEMP. A manager or superadmin sees
everything; that exception is what makes the rule worth having.

Navigation is therefore scoped by **programme entitlement**, never by deployment topology: that S174 is
its own service and S166/S168/S171 share one must not be inferable from a sidebar. See
[ADR 0005](../adr/0005-programme-scoped-portals-and-navigation-entitlement.md), which also records the
one place the current build does not yet conform, and why the mechanism waits on IAM.

**IAM is not integrated yet.** Centralised auth and Zitadel are planned, not done. Roles reach the
services through `X-SFL-*` development headers today, so any navigation filtering is a usability control
and not a security control — every service authorises every call independently, and that must stay true.

---

## Target Structure

```text
services/
  pom.xml
  sfl-service-common/
  sfl-facilities-service/
  sfl-safety-security-service/
  sfl-fleet-logistics-service/
  sfl-asset-visibility-service/
  sfl-emergency-notification-service/
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
2. Add the service Maven workspace under `services/`.
3. Add shared conventions for DTOs, errors, site-scoped principals and integration event envelopes.
4. Add service-local Flyway migrations for metadata, outbox and inbox tables.
5. Verify the service workspace builds independently.
6. Migrate the existing IFIMP facility/fault Java vertical slice into `sfl-facilities-service`.
7. Add AVAMP-Lite asset/device reference APIs before other services depend on device IDs.
8. Add safety/security service foundations and purchased-system adapter ports.
9. Add fleet/logistics service foundations and fuel/dispatch workflows.
10. Add cross-service sagas for hall readiness, emergency incident response and secure dispatch.
11. Add local Docker Compose for the services plus Postgres, broker, Redis and IAM.
12. Add contract, architecture, API, integration and event tests.

## Current Foundation Changes

- Removed .NET source/test/project artifacts from the IntelliJ project.
- Added `services/` Maven workspace for SFL microservices, now including the ADR-0004 S174 emergency notification deployable.
- Added `sfl-service-common` shared convention module.
- Added one service-local `V1__service_foundation.sql` migration per deployable service.
- Updated root README and root app description to make the old single app a migration reference, not the target architecture.
