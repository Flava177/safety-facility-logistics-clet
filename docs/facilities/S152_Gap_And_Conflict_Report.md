# S152 CAFM / IWMS — Gap and Conflict Report

- System: **S152 Computer-Aided Facility Management (CAFM) / IWMS**
- Platform: **SFL.IFIMP** — Operational owner: F&L, Building & Infrastructure Unit
- Phase: **Fast-Track**
- Service: `services/sfl-facilities-service` — port `8091`, database `sfl_facilities_service`, schema `facilities`
- Requirements: `SRS-SFL-S152-01` … `SRS-SFL-S152-05`
- Written before implementation, against the code as it stood on branch `feat/s152-cafm-iwms-backend`.

S152 is not one more module beside S153 and S159. The mapping is explicit that it **hosts** them:
Maintenance (S153), Space Planning (S158) and Room & Resource Booking (S159) are sub-systems of the
CAFM layer, and S162a, S166, S169 and S173 all dereference its location and asset records. This
report is therefore written as much about *what S152 must expose to its sub-systems* as about what it
does itself.

---

## 1. What already exists

### 1.1 `masterdata` — the estate registry

Six aggregates, all Java records, all framework-free, all correct as far as they go:

| Aggregate | File | Persisted as |
|---|---|---|
| `Site` | `masterdata/domain/Site.java` | `facilities.sites` |
| `Building` | `masterdata/domain/Building.java` | `facilities.buildings` |
| `FacilityFloor` | `masterdata/domain/FacilityFloor.java` | `facilities.facility_floors` |
| `FacilityRoom` | `masterdata/domain/FacilityRoom.java` | `facilities.facility_rooms` |
| `Zone` | `masterdata/domain/Zone.java` | `facilities.zones` |
| `DeviceReference` | `masterdata/domain/DeviceReference.java` | `facilities.device_references` |

Supporting enums: `LocationReadinessStatus` (`UNKNOWN`, `READY`, `DEGRADED`, `BLOCKED`),
`DeviceReferenceType` (13 constants), `DeviceOperationalStatus` (5 constants).

`Site` carries the shared normalisation helpers (`normalizeCode`, `blankToNull`, `requireText`) that
the other five aggregates call — a small piece of coupling worth keeping, since it is what makes code
normalisation uniform across the estate.

One application service, `FacilitiesMasterDataService`, holds seven commands and six queries. One
controller, `FacilitiesMasterDataController`, exposes them at `/api/v1/facilities/*` with Bean
Validation on inline request records. Persistence is JPA through `JpaFacilitiesRepositoryAdapter`
behind the `FacilitiesRepository` port.

### 1.2 `maintenance` — the S153 foundation

`FacilityFault` → `WorkOrder` with `OPEN` → `ASSIGNED` → `CLOSED`. `WorkOrderService` is the only
place in the service that currently **authorises** anything: it calls `AuthorizationPolicy.requireAnyRole`
and `requireSiteAccess` against role sets declared as constants. This is the pattern S152 must adopt
and generalise, not invent.

### 1.3 `shared` — the service platform

`ServiceOutbox` port with `JpaServiceOutbox` writing to `facilities.outbox_messages`;
`FacilitiesApiExceptionHandler` mapping `IllegalArgumentException` → 400, `AuthorizationException` → 403,
`MethodArgumentNotValidException` → 400; `DevActorHeaderResolver` reading the `X-SFL-*` headers;
CORS, security and `Clock` configuration.

### 1.4 Migrations

`V1__service_foundation.sql` (schema, `service_metadata`, `outbox_messages`, `inbox_messages`),
`V2__facilities_master_data.sql` (the six estate tables), `V3__facility_faults.sql`,
`V4__work_orders.sql`. Next free version is **V5**.

### 1.5 Tests

Four test classes: `FacilitiesMasterDataTest`, `FacilityFaultTest`, `WorkOrderTest`,
`WorkOrderServiceTest`. All unit-level. There is **no** WebMvc contract test, no persistence test, no
integration test and no architecture test in this service.

### 1.6 The static page

`src/main/resources/static/index.html` — 1 406 lines of hand-rolled Bootstrap titled "SFL Facilities
Dashboard", with Command Center / Registry / Maintenance / Asset Visibility / Settings tabs. Per
[ADR 0006](../adr/0006-one-dashboard-and-the-retirement-of-the-per-service-pages.md) it is the one
page that was **not** retired, because nothing supersedes it. It calls the same
`/api/v1/facilities/*` endpoints this work extends.

---

## 2. What is partial

| Area | State today | Why it is not enough for S152 |
|---|---|---|
| **Record lifecycle** | `Site.active` boolean; nothing on the other five | `SRS-SFL-S152-01` requires `active, inactive, suspended and archived` lifecycle states "where applicable" |
| **System-managed fields** | `createdAt` only | `SRS-SFL-S152-01` requires record UUID, site scope, created by/date, **last modified by/date, version, source channel and audit correlation ID** |
| **Optimistic locking** | absent | Required by the same clause (`version`); nothing today detects a stale write |
| **Duplicate blocking** | DB unique constraints only | The constraint fires as a 500-level integrity error, not the `Duplicate Identifier` envelope the SRS names |
| **Authorization** | `maintenance` only | `masterdata` commands and queries are **completely unauthorised** — any caller can create a site or read every room in every site. `SRS-SFL-S152-01` requires "Users shall only see and update records inside their assigned site scopes and roles" |
| **Readiness** | one enum column + free-text note, set by hand | `SRS-SFL-S152-01` names a `readiness profile` as a maintained record; `-05` requires readiness indicators, unavailable-room reporting and examination-readiness risk. A hand-set string cannot answer "why is this room not ready" |
| **Audit** | none | `SRS-SFL-S152-03` requires actor, timestamp, before/after, source channel, correlation ID, **append-only and hash-chained** |
| **Idempotency** | none | `SRS-SFL-S152-04` requires idempotency keys on integration messages; the same applies to retried state-creating commands |
| **Runtime configuration** | none | `SRS-SFL-S152-02` requires SLA/escalation rules evaluated from "the runtime configuration active at the time of evaluation"; NFR 23.8 requires readiness checklists to be runtime-configurable and versioned |
| **Dashboards** | none | `SRS-SFL-S152-05` is an entire requirement with its own error states (`Data Stale`, `No Scope`, `Restricted Drilldown`) |
| **Operating mode** | none | NFR 23.3: "Platform mode changes, such as Routine to Examination Mode, must be explicit, audited and reversible only by authorised roles" |
| **Error envelope** | `Map.of(status, error, message, timestamp)` | No error **code**, no correlation ID. The SRS names specific error states per requirement; they must be machine-readable and testable |
| **OpenAPI** | not on the classpath | Step 10 requires `/v3/api-docs` and `/swagger-ui.html` to render. `springdoc-openapi-starter-webmvc-ui` is in the fleet pom and **absent** from the facilities pom |
| **Facility asset** | `DeviceReference` only | `SRS-SFL-S152-01` lists `facility asset` as a distinct maintained record, and §21.1 makes it the parent of work orders, preventive schedules and evidence. A CCTV camera reference is not a chiller |

---

## 3. What must be added

Grouped by the requirement that demands it.

**SRS-SFL-S152-01 — operational records**
`RecordMetadata` value object (created/modified by and at, version, source channel, correlation ID);
`RecordLifecycleStatus` (`ACTIVE`, `INACTIVE`, `SUSPENDED`, `ARCHIVED`); `SpaceType`; typed duplicate
detection; site-scope and role enforcement on every command and query; the `FacilityAsset` aggregate
with category, criticality, operational status, custodian, service interval and warranty; zone
membership (a zone that covers rooms and devices, not just a code).

**SRS-SFL-S152-02 — workflow and operating mode**
`OperatingMode` (`ROUTINE`, `EXAMINATION`) on the site, with an explicit, audited, role-gated
transition; a readiness lock that examination mode engages; runtime configuration read at evaluation
time.

**SRS-SFL-S152-03 — evidence and audit**
`AuditEvent` + `AuditHashChain` + `AuditChainVerification`, a single-row chain-head table so
concurrent appends cannot interleave, an append-only database trigger, and an integrity-check endpoint.

**SRS-SFL-S152-04 — integration**
`IdempotencyPort` with an operation/key/fingerprint store; a replay with the same key and the same
payload returns the original result, a replay with a different payload is a client error.

**SRS-SFL-S152-05 — dashboards**
A readiness read model: site summary, space summary, blockers by severity, spaces unavailable for
booking or examination use, stale readiness data, and maintenance-linked readiness risk. Snapshot
persistence with a configured freshness threshold and a stale-data warning.

**Readiness, spanning -01, -02 and -05**
`ReadinessChecklist` / `ReadinessChecklistItem` (runtime-configurable, versioned, effective-dated),
`ReadinessAssessment` / `ReadinessAssessmentItem`, `ReadinessBlocker` with severity, and the rule the
whole system turns on: **a space cannot be READY while a critical blocker is open.**

---

## 4. What must not be duplicated

This is the half of the report that protects the work already shipped.

1. **`sfl-service-common` stays free of facilities business rules.** `SflRole`, `SflPermission`,
   `SiteScopedPrincipal`, `ActorContext` and `AuthorizationPolicy` are reused as they are. New
   permission *constants* are added to `SflPermission` — additive only, matching what S166, S168 and
   S171 each did — but the **role → permission mapping lives in the facilities module**, exactly as
   `FleetPermissionMatrix` lives in the fleet module.

2. **The existing six estate tables are extended, not replaced.** Renaming `facilities.sites` to
   `facilities_sites` would break V2–V4, six JPA entities, `WorkOrderService`, the four existing tests
   and the 1 406-line static page, in exchange for a prefix the schema name already supplies. *This is
   a deliberate deviation from the table names suggested in the build brief; see §8.*

3. **The `maintenance` module is not rewritten.** S153 is out of scope. S152 gives it a place to
   attach — `facility_asset_id` on a fault, readiness that reacts to an open work order — without
   changing its aggregates or its lifecycle.

4. **`ServiceOutbox` is reused.** Event recording already works and the outbox table already exists;
   S152 adds delivery-state columns to it rather than a second outbox.

5. **AVAMP-Lite is not absorbed.** `sfl-asset-visibility-service` owns cross-programme *device and
   asset reference identity*. S152's `FacilityAsset` is the **fixed plant attached to a space** that
   maintenance is raised against — a chiller, a lift, a generator, a distribution board. The two are
   linked by an optional `asset_reference_id` pointing at AVAMP by value, never by foreign key, and
   never by reading its schema.

6. **No cross-schema foreign key, no cross-service database read.** Everything stays inside
   `sfl_facilities_service` / `facilities`.

7. **`DevActorHeaderResolver` is superseded, not deleted.** The new `FacilitiesActorResolver` handles
   the JWT path *and* the header path from one interface, mirroring `FleetActorResolver`; the old
   resolver remains a Spring bean so nothing that injects it breaks.

---

## 5. Package structure

The service-level platform goes in `shared`, not in a feature package. This is the deliberate
difference from the fleet module, and it is the whole point of building CAFM first: audit,
idempotency, runtime configuration, the actor resolver, the error envelope and the permission matrix
are inherited by `maintenance` (S153) and by `booking` (S159) when it arrives, rather than rebuilt
per module.

```
gh.edu.clet.sfl.facilities
├── shared/                             ← the CAFM platform every IFIMP module inherits
│   ├── api/                            FacilitiesActorResolver, CorrelationIdFilter,
│   │                                   FacilitiesApiExceptionHandler, ApiErrorResponse,
│   │                                   DevActorHeaderResolver (retained)
│   ├── application/                    ServiceOutbox (retained)
│   │   └── port/                       AuditPort, IdempotencyPort, RuntimeConfigurationPort
│   ├── domain/
│   │   ├── audit/                      AuditAction, AuditEvent, AuditHashChain,
│   │   │                               AuditChainVerification, SourceChannel
│   │   ├── error/                      FacilitiesErrorCode + typed exceptions
│   │   ├── model/                      RecordMetadata, RecordLifecycleStatus, OperatingMode
│   │   └── policy/                     FacilitiesPermissionMatrix
│   ├── infrastructure/persistence/     outbox (retained) + audit, idempotency, runtime config
│   └── config/                         existing three + FacilitiesOpenApiConfiguration
├── masterdata/                         ← S152 estate register (extended in place)
│   ├── domain/                         existing six + FacilityAsset, SpaceType, AssetCategory,
│   │                                   AssetCriticality, AssetOperationalStatus, ZoneMembership
│   ├── application/                    FacilitiesMasterDataService (extended),
│   │                                   FacilityAssetService, OperatingModeService, commands
│   ├── api/                            FacilitiesMasterDataController (extended),
│   │                                   FacilityAssetController
│   └── infrastructure/persistence/
├── readiness/                          ← NEW: the readiness engine
│   ├── domain/                         ReadinessChecklist, ReadinessChecklistItem,
│   │                                   ReadinessAssessment, ReadinessAssessmentItem,
│   │                                   ReadinessBlocker, BlockerSeverity, ReadinessOutcome
│   ├── application/                    ReadinessApplicationService, commands
│   ├── api/                            ReadinessController
│   └── infrastructure/persistence/
├── dashboard/                          ← NEW: the S152-05 read model
│   ├── domain/                         FacilityDashboardSnapshot, metric records
│   ├── application/                    FacilityDashboardService
│   ├── api/                            FacilityDashboardController
│   └── infrastructure/persistence/
├── maintenance/                        ← S153, untouched except for the asset link
└── api/                                SystemController (retained)
```

Dependency rule, ArchUnit-enforced: `api → application → domain`, `infrastructure → application/domain`,
nothing points into `infrastructure`, and `domain` imports no framework.

---

## 6. Proposed API inventory

All under `/api/v1/facilities`. `C` marks a state-changing operation that accepts `Idempotency-Key`.

| Area | Method | Path | Permission |
|---|---|---|---|
| Sites | `POST` C | `/sites` | `FACILITIES_SITE_MANAGE` |
| | `GET` | `/sites` | `FACILITIES_SITE_READ` |
| | `GET` | `/sites/{siteId}` | `FACILITIES_SITE_READ` |
| | `PATCH` | `/sites/{siteId}` | `FACILITIES_SITE_MANAGE` |
| | `PATCH` | `/sites/{siteId}/operating-mode` | `FACILITIES_OPERATING_MODE_CHANGE` |
| | `PATCH` | `/sites/{siteId}/lifecycle` | `FACILITIES_SITE_MANAGE` |
| Buildings | `POST` C | `/buildings` | `FACILITIES_SPACE_MANAGE` |
| | `GET` | `/buildings` | `FACILITIES_SPACE_READ` |
| | `GET` | `/buildings/{buildingId}` | `FACILITIES_SPACE_READ` |
| Floors | `POST` C | `/floors` | `FACILITIES_SPACE_MANAGE` |
| | `GET` | `/buildings/{buildingId}/floors` | `FACILITIES_SPACE_READ` |
| | `GET` | `/floors/{floorId}` | `FACILITIES_SPACE_READ` |
| Rooms/spaces | `POST` C | `/rooms` | `FACILITIES_SPACE_MANAGE` |
| | `GET` | `/rooms` (site, building, floor, type, readiness, mode, page) | `FACILITIES_SPACE_READ` |
| | `GET` | `/rooms/{roomId}` | `FACILITIES_SPACE_READ` |
| | `PATCH` | `/rooms/{roomId}` | `FACILITIES_SPACE_MANAGE` |
| | `PATCH` | `/rooms/{roomId}/readiness` | `FACILITIES_READINESS_ASSESS` |
| | `PATCH` | `/rooms/{roomId}/lifecycle` | `FACILITIES_SPACE_MANAGE` |
| Zones | `POST` C | `/zones` | `FACILITIES_ZONE_MANAGE` |
| | `GET` | `/zones` | `FACILITIES_ZONE_READ` |
| | `GET` | `/zones/{zoneId}` | `FACILITIES_ZONE_READ` |
| | `POST` | `/zones/{zoneId}/members` | `FACILITIES_ZONE_MANAGE` |
| | `DELETE` | `/zones/{zoneId}/members/{memberId}` | `FACILITIES_ZONE_MANAGE` |
| Devices | `POST` C | `/device-references` | `FACILITIES_DEVICE_REFERENCE_REGISTER` |
| | `GET` | `/device-references` (site, type, room) | `FACILITIES_DEVICE_REFERENCE_READ` |
| | `GET` | `/device-references/{deviceId}` | `FACILITIES_DEVICE_REFERENCE_READ` |
| Assets | `POST` C | `/assets` | `FACILITIES_ASSET_MANAGE` |
| | `GET` | `/assets` (site, room, category, criticality, status) | `FACILITIES_ASSET_READ` |
| | `GET` | `/assets/{assetId}` | `FACILITIES_ASSET_READ` |
| | `PATCH` | `/assets/{assetId}/status` | `FACILITIES_ASSET_MANAGE` |
| | `PATCH` | `/assets/{assetId}/location` | `FACILITIES_ASSET_MANAGE` |
| Readiness | `POST` C | `/readiness/checklists` | `FACILITIES_READINESS_CHECKLIST_MANAGE` |
| | `GET` | `/readiness/checklists` | `FACILITIES_READINESS_READ` |
| | `GET` | `/readiness/checklists/{checklistId}` | `FACILITIES_READINESS_READ` |
| | `PATCH` | `/readiness/checklists/{checklistId}` | `FACILITIES_READINESS_CHECKLIST_MANAGE` |
| | `POST` C | `/readiness/assessments` | `FACILITIES_READINESS_ASSESS` |
| | `GET` | `/readiness/assessments` (site, room, outcome) | `FACILITIES_READINESS_READ` |
| | `GET` | `/readiness/assessments/{assessmentId}` | `FACILITIES_READINESS_READ` |
| | `PATCH` | `/readiness/blockers/{blockerId}/resolution` | `FACILITIES_READINESS_ASSESS` |
| | `GET` | `/readiness/blockers` (site, severity, open) | `FACILITIES_READINESS_READ` |
| | `POST` | `/readiness/rooms/{roomId}/lock` | `FACILITIES_READINESS_OVERRIDE` |
| | `DELETE` | `/readiness/rooms/{roomId}/lock` | `FACILITIES_READINESS_OVERRIDE` |
| Dashboard | `GET` | `/dashboard` | `FACILITIES_DASHBOARD_READ` |
| | `GET` | `/dashboard/rooms` | `FACILITIES_DASHBOARD_DRILLDOWN` |
| | `GET` | `/dashboard/blockers` | `FACILITIES_DASHBOARD_DRILLDOWN` |
| | `GET` | `/dashboard/unavailable` | `FACILITIES_DASHBOARD_DRILLDOWN` |
| Audit | `GET` | `/audit` (resource, actor, action, site) | `FACILITIES_AUDIT_READ` |
| | `GET` | `/audit/integrity` | `FACILITIES_AUDIT_INTEGRITY_CHECK` |
| Config | `GET` | `/configuration` | `FACILITIES_CONFIG_READ` |
| | `PUT` | `/configuration/{key}` | `FACILITIES_CONFIG_MANAGE` |
| Actor | `GET` | `/actor/permissions` | authenticated |

---

## 7. Proposed migrations

| Version | File | Contents |
|---|---|---|
| **V5** | `V5__facilities_platform_foundation.sql` | append-only guard function; `facility_audit_records` + `facility_audit_chain_state`; `facility_runtime_configuration` + seeded defaults; `facility_idempotency_keys`; outbox delivery-state columns |
| **V6** | `V6__facilities_estate_model.sql` | lifecycle/metadata/version columns on the six estate tables; `space_type`, `bookable`, `examination_capable`, `area_sqm`, `cost_centre` on rooms; `operating_mode` on sites; `facility_assets`; `facility_zone_memberships`; data migration for existing rows |
| **V7** | `V7__facilities_readiness.sql` | `facility_readiness_checklists`, `facility_readiness_checklist_items`, `facility_readiness_assessments`, `facility_readiness_assessment_items`, `facility_readiness_blockers`, `facility_readiness_locks` |
| **V8** | `V8__facilities_dashboard_snapshots.sql` | `facility_dashboard_snapshots` + metric rows |

Every new table is prefixed `facility_` inside schema `facilities`, which distinguishes S152 platform
tables from the six pre-existing estate tables without renaming them.

---

## 8. Conflicts and deviations from the build brief

| # | Brief said | Built as | Why |
|---|---|---|---|
| C-01 | Tables named `facilities_sites`, `facilities_buildings`, `facilities_rooms`, `facilities_zones`, `facilities_device_references` | Existing names kept (`facilities.sites`, `.buildings`, `.facility_floors`, `.facility_rooms`, `.zones`, `.device_references`); **new** tables prefixed `facility_` | Renaming six live tables breaks V2–V4, six JPA entities, `WorkOrderService`, four tests and the static page. The schema `facilities` already namespaces them; `facilities.facilities_sites` stutters. The brief offered the names as an example ("for example") |
| C-02 | `facilities_outbox_events`, `facilities_audit_events` "if not already present/reused" | `facilities.outbox_messages` reused and extended; audit added as `facility_audit_records` | The outbox already exists and is written by two modules. Audit is named for the fleet precedent (`fleet_audit_records`) so the two services read alike |
| C-03 | "Facility system / utility reference" as a domain aggregate | Delivered as `FacilityAsset` with `AssetCategory` covering HVAC, electrical, plumbing, lift, generator, fire system, water, security and building fabric | The SRS names `facility asset`, not `facility system`. One aggregate with a category enum beats two aggregates that overlap; utility systems are assets with a category |
| C-04 | Idempotency "where applicable" | Applied to every state-**creating** POST; not to PATCH transitions | A PATCH transition is already guarded by the optimistic version and the state machine, so a second identical PATCH is either a no-op or an invalid-transition error. Adding keys there would be ceremony without a failure mode |
| C-05 | Build S152 "so those modules can use it" | `maintenance` is given a nullable `facility_asset_id` link and readiness reads open work orders; **no S153 workflow is built** | The brief's own non-goal. The link is the extension point; the workflow is a later pass |
| C-06 | Operating mode "where supported by SRS" | Built | It is supported: NFR 23.3 requires Routine→Examination mode changes to be explicit, audited and role-reversible, and `SRS-SFL-S152-02` and `-05` both make operating mode an input |
| C-07 | Permission model | Roles map to permissions in `FacilitiesPermissionMatrix` inside the facilities module | `SiteScopedPrincipal` carries roles and site scopes but no permissions. Deriving in-module matches the fleet, fuel, dispatch and emergency precedent and keeps `sfl-service-common` free of IFIMP rules. Replaceable by token claims later without touching call sites |
| C-08 | "PostgreSQL/Flyway integration tests" | Testcontainers, skipped automatically when no Docker daemon is present | The fleet service already does this; a developer without Docker still gets a green unit build. **In this environment the Java Docker client cannot reach the named pipe, so those 12 tests skip.** The migrations were therefore verified directly, by running the service against the compose e2e database with the schema dropped — which is what found the defects in §8a |
| C-09 | Nothing in the brief | `FacilitiesArchitectureTest` excludes `maintenance` from the "nothing points into infrastructure" rule | `WorkOrderService` and `FacilityFaultService` inject Spring Data repositories directly. Pre-existing, out of scope for S152, and recorded in the test's own javadoc rather than silently allowed. The S153 pass should introduce a repository port and delete the exclusion |

## 8a. Defects found by running the service against a real database

Added after implementation. Every one of these was invisible to the unit suite — 135 tests were green
while the service could not start — and each was found by dropping the schema and running
`spring-boot:run` against the compose e2e database. They are recorded because the pattern matters more
than the individual bugs: **an in-memory double cannot tell you your schema is wrong.**

| # | Defect | Where | Fix |
|---|---|---|---|
| D-01 | `CHAR(64)` hash columns | V5 | Hibernate maps `String(length=64)` to `VARCHAR(64)` and refuses `CHAR(64)` under `ddl-auto: validate`; the service would not start. Changed to `VARCHAR(64)` with an explicit length `CHECK`. The fleet service needed a corrective migration for the identical mistake (`V9_1`); V5 now carries the note |
| D-02 | Two unannotated constructors on `WorkOrderService` | pre-existing, untouched by this branch | Spring considers non-public constructors as candidates, so with two unannotated it looked for a no-arg one and failed. **The facilities service had never started with this class present.** `@Autowired` on the production constructor |
| D-03 | Readiness blockers saved before the assessment they reference | `ReadinessApplicationService.submitAssessment` | Foreign-key violation on every assessment containing a failure. Blockers are now built in memory, the assessment is saved, then the blockers — which also required computing the outcome from the about-to-be-raised blockers rather than from the store |
| D-04 | Audit payloads stored as `jsonb` | V5, `AuditRecordEntity` | PostgreSQL normalises jsonb by reordering object keys, so the payload read back was never the payload hashed and **every record replayed as tampered**. Stored as `TEXT` |
| D-05 | Nanosecond timestamps hashed, microsecond timestamps stored | `JpaAuditAdapter` | Same effect as D-04, from the other direction. Truncated to microseconds before hashing |
| D-06 | Configuration supersede-then-insert | `JpaRuntimeConfigurationAdapter` | Hibernate ordered the INSERT ahead of the superseding UPDATE in one flush, tripping the partial unique index. Explicit `flush()` between them |
| D-07 | Foreign-key violations reported as `DUPLICATE_IDENTIFIER` | `FacilitiesApiExceptionHandler` | Both arrive as `DataIntegrityViolationException`. Now discriminated on SQL state 23503 → `INVALID_PARENT_REFERENCE` |
| D-08 | Audit search bound typed nulls | `JpaAuditAdapter` | `(:param is null or col = :param)` fails with "could not determine data type of parameter". Rebuilt as a `Specification`, which omits absent filters entirely |

D-04 and D-05 are worth flagging beyond this service: **`sfl-fleet-logistics-service` stores its audit
before/after values as `jsonb` and does not truncate its timestamps**, so its hash chain is likely to
replay as broken for the same two reasons. That is outside this pass's scope and is not asserted here,
but it should be checked before S166 is relied on for evidence.

## 9. Known risks carried forward

- **No authentication.** `sfl.security.enabled=false` locally; the actor is asserted by header. S152
  authorises correctly against whatever actor it is given, but identity is not yet verified anywhere
  in this platform. Unchanged by this work, and it widens with every system added.
- **No outbox drainer in this service.** Events are recorded in `facilities.outbox_messages` and never
  published; only `sfl-fleet-logistics-service` has an AMQP transport. The delivery-state columns
  added in V5 are the schema half of that work.
- **No React UI.** This pass is backend-only per the brief. Until an IFIMP module exists in
  `frontend/sfl-operations-ui` and `S152` is added to `SystemCode` in `programmeModel.ts`, a
  `FACILITIES_MANAGER` signing in to the dashboard still sees an empty sidebar, and the 1 406-line
  static page remains the only facilities UI.
- **Readiness recomputation is synchronous.** Assessment submission recomputes the space's readiness in
  the same transaction. That is correct and simple at Phase 1 volumes; a site-wide recompute triggered
  by a checklist change is deliberately not built.
