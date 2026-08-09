# SFL Microservices Realignment Plan

Date: July 2026. **Consolidated to three deployables on 5 August 2026 - see the note below.**

> ## Superseded in part, 5 August 2026
>
> This document planned **five** deployable services. The platform now runs **three**, one per
> programme: `sfl-facilities-service` (IFIMP), `sfl-safety-security-service` (SSEMP) and
> `sfl-fleet-logistics-service` (FTLMP). `sfl-emergency-notification-service` and
> `sfl-asset-visibility-service` were folded into SSEMP and FTLMP respectively and no longer exist.
>
> **What was wrong with the five-service plan, in its own terms.** It asserted that the SRS wording
> describing "one Spring Boot modular platform" was outdated and should be replaced. That was
> backwards. SRS §2.1 names a modular baseline, §2.5 specifies "PostgreSQL operational store with
> **schema per module**", and §2.6 forbids cross-schema foreign keys - none of which was superseded
> by anything. The repository convention is that where the SRS and a note disagree, the SRS wins and
> the note is corrected. This is that correction.
>
> **What the five-service plan got right, and what is kept.** Every boundary rule below still holds
> verbatim: schema per bounded context, no cross-schema foreign keys or joins, own IDs, outbox and
> idempotent inbox per context, contracts and events rather than shared tables. Consolidation moved
> the *deployable* count. It did not merge a single schema, and must not: `emergency_notification`
> and `asset_visibility` remain intact inside their host service's database.
>
> **Why three and not four or five.** The system mapping (§30A.6.9) assigns every Cluster 9 system to
> one of three F&L units - Building & Infrastructure, Health Safety & Security, and Transportation &
> Logistics - and the thirteen Fast-Track systems fall out 3 / 7 / 3 across them. Three deployables
> match the three units that own, fund and release them.

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
- Docker/local development needs to move from one app to per-programme service artifacts.

## Replacement Wording For SRS

Replace any SRS wording equivalent to:

> The implementation baseline is a Spring Boot modular monolith with bounded contexts aligned to SFL.IFIMP, SFL.SSEMP, SFL.FTLMP and SFL.AVAMP-Lite.

with:

> The implementation baseline is a Spring Boot architecture composed of three deployable programme
> services - `sfl-facilities-service` (SFL.IFIMP), `sfl-safety-security-service` (SFL.SSEMP,
> including S174 Emergency Mass Notification) and `sfl-fleet-logistics-service` (SFL.FTLMP, including
> the SFL.AVAMP-Lite asset and device reference layer). Each **bounded context** owns its database
> schema, migrations, API boundary, domain model, outbox and idempotent inbox; a service may host more
> than one context and does so through separate schemas, never a shared one. No foreign key, join or
> view may cross a schema boundary in either direction, including between two schemas hosted by the
> same service. Cross-context workflows use APIs, events and sagas through the wider CLET
> microservices ecosystem.

Replace any SRS wording equivalent to:

> SFL is implemented as one backend artifact or one deployable SFL backend.

with:

> SFL Phase 1 is implemented as three deployable Spring Boot service artifacts, one per programme,
> integrated through the enterprise API gateway, IAM, event broker, audit/evidence, notification,
> reporting and document/object-storage services.

## Programme, System and Service Map

Counts that do not line up one-to-one, and each is real: **13 systems**, **4 programme modules**,
**3 deployable services**, **5 schemas**.

| Programme | Systems | Deployable service | Schema | Local port |
| --- | --- | --- | --- | --- |
| **SFL.IFIMP** | S152 CAFM/IWMS, S153 CMMS, S159 Room & resource booking | `sfl-facilities-service` | `facilities` | 8091 |
| **SFL.SSEMP** | S160 Visitor, S160a Access control, S161 CCTV/VMS, S162 Intrusion & alarms, S162a Fire & life safety, S163 HSE incident | `sfl-safety-security-service` | `safety_security` | 8092 |
| **SFL.SSEMP** | S174 Emergency mass notification | `sfl-safety-security-service` | `emergency_notification` | 8092 |
| **SFL.FTLMP** | S166 Fleet & vehicle, S168 Fuel & driver logbooks, S171 Courier & dispatch | `sfl-fleet-logistics-service` | `fleet_logistics` | 8093 |
| **SFL.AVAMP** | cross-cutting asset and device reference for all 13 | `sfl-fleet-logistics-service` | `asset_visibility` | 8093 |

Read it in three directions, because each answers a different question:

- **A programme is now a service**, which is the one thing consolidation simplified: launching a
  programme means starting exactly one process. SSEMP used to be two deployables carrying seven
  systems, and the plural caught people out.
- **A service is not a system.** `sfl-fleet-logistics-service` holds three modules under
  `gh.edu.clet.sfl.fleetlogistics` - `fleet`, `fuel`, `dispatch`. Searching one package is not searching
  the service; searching one service is not searching the programme.
- **A service is not a schema either.** SSEMP and FTLMP own two schemas each. The schema is where the
  boundary is enforced - no foreign key crosses it - and that is unchanged by two contexts now sharing
  a process and a database.

- **S174 was never regrouped, only re-deployed, twice.** ADR 0004 split the *deployable* for
  availability, fast-lane latency, callback volume, retry isolation, degraded mode and blast radius;
  consolidation put it back. It has been SFL.SSEMP / Emergency Communications throughout, on
  `/api/v1/emergency/**` throughout, and in `emergency_notification` throughout. The costs ADR 0004
  named are real and are now carried inside one process - see that ADR's amendment.

AVAMP is the odd one out on purpose: it is the asset and device reference layer supporting all 13
systems, not a fourteenth system. It should not become a duplicate asset register in Phase 1.

### What this means for launching

| Launch | Start |
| --- | --- |
| **IFIMP** | `sfl-facilities-service` |
| **SSEMP** | `sfl-safety-security-service` (S174 included) |
| **FTLMP** | `sfl-fleet-logistics-service` (AVAMP included) |

Plus the three Postgres instances (`compose.service-dbs.yml`) and, in a real environment, the
gateway, IAM, broker, audit/evidence and object storage.

### What this means for the operator

A driver or a head of fleet signs in and sees fleet, fuel and dispatch. They do **not** see CCTV access
management, intrusion detection or visitor badges - those are SSEMP. A manager or superadmin sees
everything; that exception is what makes the rule worth having.

Navigation is therefore scoped by **programme entitlement**, never by deployment topology: that S174 is
its own service and S166/S168/S171 share one must not be inferable from a sidebar. See
[ADR 0005](../adr/0005-programme-scoped-portals-and-navigation-entitlement.md), which also records the
one place the current build does not yet conform, and why the mechanism waits on IAM.

**IAM is not integrated yet.** Centralised auth and Zitadel are planned, not done. Roles reach the
services through `X-SFL-*` development headers today, so any navigation filtering is a usability control
and not a security control - every service authorises every call independently, and that must stay true.

---

## Target Structure

```text
services/
  pom.xml
  sfl-service-common/
  sfl-facilities-service/
  sfl-safety-security-service/     # safety_security + emergency_notification
  sfl-fleet-logistics-service/     # fleet_logistics + asset_visibility
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
- Added `services/` Maven workspace for SFL microservices.
- Added `sfl-service-common` shared convention module.
- Added one service-local `V1__service_foundation.sql` migration per deployable service.
- Updated root README and root app description to make the old single app a migration reference, not the target architecture.
