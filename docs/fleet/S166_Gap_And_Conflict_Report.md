# S166 Fleet and Vehicle Management — Gap, Conflict and Decision Report

**Raised:** July 2026 · **Slice:** S166 Fleet and Vehicle Management · **Branch:** `fleet`

Precedence applied throughout:

1. `docs/System Mappings and SRS/SFL_SRS.docx` — **the formal SRS defines what the system must do**.
2. `docs/SFL_Phase1_Implementation_Workplan.md` — how approved requirements are built.
3. `solution.md`, `docs/System Mappings and SRS/SFL_Phase1_System_Architecture_Implementation_Guide_v2.md`,
   `docs/SFL_Phase1_Microservices_Build_Workflow_Plan.md`, `docs/adr/`, `docs/integration/` — technical guidance
   that must not override or silently expand the SRS.
4. Existing code — evidence of the current implementation, not the specification.

Each conflict states the **resolution actually implemented** and, where the resolution is an interpretation,
the **owner decision still required**. No `SRS-SFL-*` identifier has been invented.

---

## C-01 — `SRS-SFL-S166-06` does not exist *(blocking interpretation, resolved)*

| | |
|---|---|
| **SRS says** | `MODULE: Fleet and Vehicle Management (S166)` defines `SRS-SFL-S166-01` … `-05` only. |
| **Workplan says** | §4.3 maps `/api/v1/fleet/trips/{id}/inspections` to "S166-06"; §15 backlog lists "S166-01..06". |
| **Conflict** | The workplan references a requirement the binding specification does not contain. |
| **Resolution** | Vehicle inspections are implemented as **supporting behaviour**, traced to `SRS-SFL-S166-01` (an inspection is a fleet operational record carrying service status and availability) and `SRS-SFL-S166-02` (the pre-trip inspection is the evidence-bearing workflow step that can block closure). The identifier `SRS-SFL-S166-06` is **not** used in code, tests or documentation. |
| **Owner decision required** | Either (a) amend the SRS to add a formal `SRS-SFL-S166-06` inspection requirement and re-tag the tests, or (b) accept the 01/02 traceability recorded in `S166_Requirement_Traceability_Matrix.md` §7 and correct the workplan. |

---

## C-02 — Workplan endpoint→requirement mapping contradicts SRS requirement semantics *(resolved in favour of the SRS)*

Workplan §4.3 table versus the SRS requirement titles:

| Endpoint | Workplan mapping | SRS meaning of that ID | Implemented mapping |
|---|---|---|---|
| `/api/v1/integrations/webhooks/telematics`, `/fleet/vehicles/{id}/movement` | S166-03 | S166-03 is **Evidence and Audit Trail** | `S166-04` (integration) + `S166-05` (stale-telematics indicator) |
| `/api/v1/fleet/emergency-logistics` | S166-04 | S166-04 is **Integrate with Related Systems** | Not implemented — see **C-12** |
| `/api/v1/fleet/drivers` (+ licence/eligibility) | S166-05 | S166-05 is **Dashboards and Reports** | `S166-01` (driver is an operational record) |
| `/api/v1/fleet/trips/{id}/inspections` | S166-06 | does not exist | `S166-01` + `S166-02` — see **C-01** |

**Resolution.** Endpoints are traced by SRS requirement *semantics*, not by the workplan's ordinal positions.
**Owner action:** correct the workplan §4.3 table.

---

## C-03 — Two live event-naming schemes *(resolved for S166; cross-service correction outstanding)*

| | |
|---|---|
| **`docs/integration/event-catalog.md`** | Integration event type pattern is `sfl.{platform}.{event-name}.v{version}` — e.g. `sfl.ftlmp.vehicle-created.v1`. Routing key `{platform}.{event-name}.v{version}`. |
| **Existing code** | `sfl-facilities-service` publishes `sfl.facilities.work-order-created` with the version carried in a separate `event_version` column — neither the platform word (`ifimp`) nor the `.v1` suffix. |
| **Workplan §4.2/§4.3** | Uses the catalog form (`sfl.ftlmp.vehicle-created.v1`). |
| **Conflict** | Two naming schemes are in use; consumers cannot bind routing keys reliably. |
| **Resolution** | **S166 follows the event catalog**: `eventType = sfl.ftlmp.<event-name>.v1`, and the `event_version` column mirrors the suffix so the two can never disagree. `FleetEventTypeTest` asserts every published fleet event matches `^sfl\.ftlmp\.[a-z0-9-]+\.v\d+$`. |
| **Not done here** | `sfl-facilities-service` and `sfl-asset-visibility-service` event names are **not** changed — out of scope ("do not modify unrelated services"). |
| **Owner action** | Schedule a corrective slice renaming `sfl.facilities.*` → `sfl.ifimp.*.v1` and `sfl.assetvisibility.*` → `sfl.avamp.*.v1` before any external consumer binds. |

---

## C-04 — Event catalog does not cover the S166 lifecycle *(resolved by documented extension)*

The catalog's `SFL.FTLMP` group lists only `vehicle-created`, `vehicle-readiness-changed`,
`vehicle-location-received` (plus dispatch/fuel events belonging to S171/S168_fuel). `SRS-SFL-S166-01/02/03`
require change events for compliance, service, driver eligibility, assignment, inspection, workflow escalation
and evidence.

**Resolution.** The catalog is **extended, not bypassed** — `docs/integration/event-catalog.md` now carries the
full S166 fleet event list under the existing naming rule. Additions are marked so the delta is reviewable.
See `S166_Event_Contracts.md` for payload and versioning detail.

---

## C-05 — Flyway migration numbering reserved by the workplan is insufficient *(resolved)*

Workplan §4.3 reserves `V2__fleet.sql`, `V3__driver.sql`, `V4__trip_inspection.sql`, `V5__fuel.sql`,
`V6__courier_custody.sql`. S166 alone requires workflow/SLA, audit/evidence, integration inbox and dashboard
projection tables — more than three migrations — so `V5`/`V6` cannot remain reserved for S168_fuel/S171.

**Resolution.** S166 owns `V2`…`V8` with descriptive names (see `S166_Migration_Plan.md`). `S168_fuel` and
`S171` take `V9+`. Migration *content* per the workplan is unchanged; only the numbering is.
**Owner action:** update workplan §4.3 "Migrations" line.

---

## C-06 — Error envelope inconsistency between the common contract and existing code *(resolved for S166)*

`sfl-service-common` defines `ApiResponse<T>{data,error}` and `ApiError{code,message,correlationId,timestamp}`,
but `sfl-facilities-service`'s `@RestControllerAdvice` returns an ad-hoc `{status,error,message,timestamp}` body
and its controllers return bare domain objects rather than `ApiResponse`.

**Resolution.** S166 uses the common envelope on **every** fleet endpoint: success as `ApiResponse.ok(...)`,
failure as `ApiResponse.failed(ApiError.of(code, message, correlationId))`, where `message` reproduces the SRS
*Error States* wording **verbatim** and `code` is the machine-readable constant listed in the traceability
matrix. Field-level validation detail is carried in an extra `fieldErrors` member of the fleet-local
`FleetApiError` response DTO so that `sfl-service-common` stays untouched.
**Owner action:** align the other services in their own slices.

---

## C-07 — The common security model has no fleet roles, no permissions on the principal *(resolved additively)*

| Observed | Consequence |
|---|---|
| `SflRole` contains no fleet roles | The SRS user classes (Fleet/Logistics Officer, Fleet Manager, Driver) cannot be expressed |
| `SflPermission` contains no fleet permissions | Sensitive-field and privileged-transition rules cannot be expressed |
| `SiteScopedPrincipal` carries roles + sites but **no permissions** | `AuthorizationPolicy` can only check roles and sites |

**Resolution (deliberately minimal, no breaking change).**

- **Additive only** in `sfl-service-common`: new `SflRole` constants (`FLEET_LOGISTICS_OFFICER`, `FLEET_MANAGER`,
  `FLEET_DRIVER`, `COMPLIANCE_OFFICER`, `FLEET_REPORTING_VIEWER`, `SERVICE_INTEGRATION`) and new `SflPermission`
  constants (`FLEET_*`). Adding enum constants changes no existing signature and no existing behaviour, so no
  other service is touched.
- **Permission derivation stays in the fleet feature**: `domain/policy/FleetPermissionMatrix` maps role → granted
  fleet permissions and `application/service/FleetAccessPolicy` enforces permission + site scope + record scope +
  sensitive-field + privileged-transition rules. No fleet business logic is pushed into the shared library.

**Owner decision required.** Long term, permissions should arrive as JWT claims and live on
`SiteScopedPrincipal`. That is a breaking change to a shared record used by three services and is therefore
**not** made here. Until then the role→permission matrix is the single source of truth and is unit-tested.

---

## C-08 — Mandatory retention classes are enumerated for other modules, not fleet *(interpretation)*

`SRS-SFL-S166-03` validation says: *"Retention class is mandatory for CCTV, visitor, biometric, incident and
dispatch evidence."* None of those words name a fleet evidence type directly.

**Resolution implemented.** Retention class is **mandatory for every fleet evidence reference**. The strict
reading (mandatory only for dispatch-related fleet evidence) would allow trip-closure and inspection evidence to
be stored with no retention class, which conflicts with `SRS §23.6 Data Retention and Privacy` and with the
S166-03 system-managed field list that includes `retention class` unconditionally. Choosing the stricter rule
cannot under-comply.
**Owner decision required:** confirm the blanket rule, or supply the fleet-specific retention schedule.

---

## C-09 — Postgres Row-Level Security is required by the guidance but not implemented *(gap, recorded)*

`solution.md` and workplan §7 require *"Postgres Row-Level Security + repository site-scope filter driven by the
principal's SiteScopes"*. The SRS itself (`S166-01`) requires only that *"Users shall only see and update records
inside their assigned site scopes and roles"*.

**Implemented.** Site scope is enforced in the application layer on every command and query, and every list query
is site-filtered at the SQL level by the caller's authorised sites (not filtered in memory after loading).
**Not implemented.** Database-enforced RLS policies. RLS needs a decision on how the request principal reaches
the database session (per-request `SET LOCAL app.site_scopes`, or a per-tenant DB role). Connection pooling makes
this an operational decision, not a code-local one.
**Owner decision required:** approve the session-GUC approach so RLS can be added in a follow-up migration.

---

## C-10 — `GET /api/v1/fleet/vehicles/{id}/movement` conditional scope *(resolved — implemented)*

The brief permits this endpoint *"only if supported by approved Phase 1 scope and available data"*. Telematics
ingestion is explicitly in `SRS-SFL-S166-04` scope and the ingested positions are stored locally, so the data
exists without any new external dependency. Implemented **read-only**, site-scoped, permission-gated, audited on
access, and it surfaces the telematics freshness age so `S166-05`'s stale-data indicator has a drilldown.

---

## C-11 — Notification capability has no provider in Phase 1 *(resolved by port + recorded adapter)*

`SRS-SFL-S166-02` requires notifying responsible users on assignment, overdue, escalation and blockers. No
notification provider is configured for fleet in Phase 1 (S174 owns the emergency notification provider in a
different service).

**Resolution.** `NotificationPort` with a `RecordedNotificationAdapter` that **persists a notification intent and
publishes an outbox event** — it never reports a fake success. When `sfl.fleet.notification.provider` is set to a
real provider that is not configured, resolution **fails loudly** with `IntegrationConfigurationNotFoundException`.

---

## C-12 — `POST /api/v1/fleet/emergency-logistics` deferred *(decision)*

The formal `SRS-SFL-S166-01…05` text contains no emergency-logistics requirement. The endpoint appears only in
workplan §4.3 (mis-mapped to S166-04) and in workplan §6.2, where emergency logistics is a **step in the
cross-service Emergency Incident Response saga** owned by SSEMP and scheduled for wave **W6**, not W5.

**Resolution.** No `/api/v1/fleet/emergency-logistics` endpoint is added — creating one would expand S166 beyond
approved scope. What *is* delivered, traceable to `SRS-SFL-S166-02` (operating mode is a named SLA input):
`OperatingMode.EMERGENCY` on trips and workflow items, emergency-priority SLA rules, and readiness policy support
for emergency-only/restricted-use vehicles.
**Owner decision required:** confirm the saga slice (W6) as the home for emergency logistics, or amend the SRS.

---

## C-13 — SRS filename referenced by `solution.md` does not exist *(documentation defect)*

`solution.md` cites `docs/System Mappings and SRS/CLET_Cluster9_SFL_Phase1_SRS_v1.0.docx`. The file on disk is
`docs/System Mappings and SRS/SFL_SRS.docx`. No functional impact; recorded so the reference can be corrected.

---

## C-14 — Root `pom.xml` single-app project still compiles legacy IFIMP code *(pre-existing, untouched)*

`src/main/java/gh/edu/clet/sfl/ifimp/**` remains a second Spring Boot application at the repository root.
`docs/architecture/microservices-realignment.md` designates it migration/reference material only. It is **not**
modified by this slice and does not participate in the `services/` reactor.

---

## C-15 — Toolchain: build requires JDK 17+, machine default is JDK 11 *(environment note)*

`services/pom.xml` inherits `spring-boot-starter-parent:4.1.0` with `java.version=17`; the machine's `JAVA_HOME`
points at Zulu 11, which cannot build the reactor. Builds and tests for this slice were run with
`JAVA_HOME="C:/Program Files/JetBrains/IntelliJ IDEA 2026.1/jbr"` (JDK 25). Recorded in
`S166_Operations_And_Verification_Guide.md` so CI and other developers configure a supported JDK.

---

## Open gaps carried forward (not blockers for this slice)

| Gap | Impact | Owner |
|---|---|---|
| RLS policies (C-09) | Defence-in-depth only; application enforcement is in place and tested | DTI platform |
| Real HRMS/CMMS/telematics/notification contracts | Simulator adapters cannot prove vendor field mappings | Integration owner |
| `sfl.facilities.*` / `sfl.assetvisibility.*` event renaming (C-03) | Cross-service routing-key inconsistency | Each service team |
| Permissions as JWT claims (C-07) | Role→permission matrix must be kept in sync with IAM | IAM / DTI |
| S168_fuel odometer source of truth | Fleet stores odometer with provenance; fuel logbooks will also write odometer readings — reconciliation rule needed | F&L Transportation & Logistics |
