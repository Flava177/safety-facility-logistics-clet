# S166 Fleet and Vehicle Management - Gap, Conflict and Decision Report

**Raised:** July 2026 · **Slice:** S166 Fleet and Vehicle Management · **Branch:** `fleet`

Precedence applied throughout:

1. `docs/System Mappings and SRS/SFL_SRS.docx` - **the formal SRS defines what the system must do**.
2. `docs/SFL_Phase1_Workflow_Review_and_GoLive_Readiness_Pack.md` - the current Release 1 scope and handoff position.
3. `solution.md`, `docs/System Mappings and SRS/SFL_Phase1_System_Architecture_Implementation_Guide_v2.md`,
   `docs/adr/`, `docs/integration/` - technical guidance
   that must not override or silently expand the SRS.
4. Existing code - evidence of the current implementation, not the specification.

Each conflict states the **resolution actually implemented** and, where the resolution is an interpretation,
the **owner decision still required**. No `SRS-SFL-*` identifier has been invented.

---

## C-01 - `SRS-SFL-S166-06` does not exist *(blocking interpretation, resolved)*

| | |
|---|---|
| **SRS says** | `MODULE: Fleet and Vehicle Management (S166)` defines `SRS-SFL-S166-01` … `-05` only. |
| **Workplan says** | §4.3 maps `/api/v1/fleet/trips/{id}/inspections` to "S166-06"; §15 backlog lists "S166-01..06". |
| **Conflict** | The workplan references a requirement the binding specification does not contain. |
| **Resolution** | Vehicle inspections are implemented as **supporting behaviour**, traced to `SRS-SFL-S166-01` (an inspection is a fleet operational record carrying service status and availability) and `SRS-SFL-S166-02` (the pre-trip inspection is the evidence-bearing workflow step that can block closure). The identifier `SRS-SFL-S166-06` is **not** used in code, tests or documentation. |
| **Owner decision required** | Either (a) amend the SRS to add a formal `SRS-SFL-S166-06` inspection requirement and re-tag the tests, or (b) accept the 01/02 traceability recorded in `S166_Requirement_Traceability_Matrix.md` §7 and correct the workplan. |

---

## C-02 - Workplan endpoint→requirement mapping contradicts SRS requirement semantics *(resolved in favour of the SRS)*

Workplan §4.3 table versus the SRS requirement titles:

| Endpoint | Workplan mapping | SRS meaning of that ID | Implemented mapping |
|---|---|---|---|
| `/api/v1/integrations/webhooks/telematics`, `/fleet/vehicles/{id}/movement` | S166-03 | S166-03 is **Evidence and Audit Trail** | `S166-04` (integration) + `S166-05` (stale-telematics indicator) |
| `/api/v1/fleet/emergency-logistics` | S166-04 | S166-04 is **Integrate with Related Systems** | Not implemented - see **C-12** |
| `/api/v1/fleet/drivers` (+ licence/eligibility) | S166-05 | S166-05 is **Dashboards and Reports** | `S166-01` (driver is an operational record) |
| `/api/v1/fleet/trips/{id}/inspections` | S166-06 | does not exist | `S166-01` + `S166-02` - see **C-01** |

**Resolution.** Endpoints are traced by SRS requirement *semantics*, not by the workplan's ordinal positions.

**Owner action - DONE, 1 Aug 2026.** The workplan's endpoint table now carries the mappings in the
right-hand column above, with a note stating each of the four corrections and why. `/fleet/emergency-logistics`
deliberately claims **no** requirement rather than a plausible one: it is unbuilt (**C-12**) and its home is
still an owner decision, so asserting a trace would manufacture coverage that does not exist. The §15
backlog range `S166-01..06` is now `S166-01..05`.

---

## C-03 - Two live event-naming schemes *(resolved for S166; cross-service correction outstanding)*

| | |
|---|---|
| **`docs/integration/event-catalog.md`** | Integration event type pattern is `sfl.{platform}.{event-name}.v{version}` - e.g. `sfl.ftlmp.vehicle-created.v1`. Routing key `{platform}.{event-name}.v{version}`. |
| **Existing code** | `sfl-facilities-service` publishes `sfl.facilities.work-order-created` with the version carried in a separate `event_version` column - neither the platform word (`ifimp`) nor the `.v1` suffix. |
| **Workplan §4.2/§4.3** | Uses the catalog form (`sfl.ftlmp.vehicle-created.v1`). |
| **Conflict** | Two naming schemes are in use; consumers cannot bind routing keys reliably. |
| **Resolution** | **S166 follows the event catalog**: `eventType = sfl.ftlmp.<event-name>.v1`, and the `event_version` column mirrors the suffix so the two can never disagree. `FleetEventTypeTest` asserts every published fleet event matches `^sfl\.ftlmp\.[a-z0-9-]+\.v\d+$`. |
| **Not done here** | `sfl-facilities-service` and `sfl-asset-visibility-service` event names are **not** changed - out of scope ("do not modify unrelated services"). |
| **Owner action** | Schedule a corrective slice renaming `sfl.facilities.*` → `sfl.ifimp.*.v1` and `sfl.assetvisibility.*` → `sfl.avamp.*.v1` before any external consumer binds. |

---

## C-04 - Event catalog does not cover the S166 lifecycle *(resolved by documented extension)*

The catalog's `SFL.FTLMP` group lists only `vehicle-created`, `vehicle-readiness-changed`,
`vehicle-location-received` (plus dispatch/fuel events belonging to S171/S168_fuel). `SRS-SFL-S166-01/02/03`
require change events for compliance, service, driver eligibility, assignment, inspection, workflow escalation
and evidence.

**Resolution.** The catalog is **extended, not bypassed** - `docs/integration/event-catalog.md` now carries the
full S166 fleet event list under the existing naming rule. Additions are marked so the delta is reviewable.
See `S166_Event_Contracts.md` for payload and versioning detail.

---

## C-05 - Flyway migration numbering reserved by the workplan is insufficient *(resolved)*

Workplan §4.3 reserves `V2__fleet.sql`, `V3__driver.sql`, `V4__trip_inspection.sql`, `V5__fuel.sql`,
`V6__courier_custody.sql`. S166 alone requires workflow/SLA, audit/evidence, integration inbox and dashboard
projection tables - more than three migrations - so `V5`/`V6` cannot remain reserved for S168_fuel/S171.

**Resolution.** S166 owns `V2`…`V8` with descriptive names (see `S166_Migration_Plan.md`). `S168_fuel` and
`S171` take `V9+`. Migration *content* per the workplan is unchanged; only the numbering is.
**Owner action:** update workplan §4.3 "Migrations" line.

---

## C-06 - Error envelope inconsistency between the common contract and existing code *(resolved for S166)*

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

## C-07 - The common security model has no fleet roles, no permissions on the principal *(resolved additively)*

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

## C-08 - Mandatory retention classes are enumerated for other modules, not fleet *(interpretation)*

`SRS-SFL-S166-03` validation says: *"Retention class is mandatory for CCTV, visitor, biometric, incident and
dispatch evidence."* None of those words name a fleet evidence type directly.

**Resolution implemented.** Retention class is **mandatory for every fleet evidence reference**. The strict
reading (mandatory only for dispatch-related fleet evidence) would allow trip-closure and inspection evidence to
be stored with no retention class, which conflicts with `SRS §23.6 Data Retention and Privacy` and with the
S166-03 system-managed field list that includes `retention class` unconditionally. Choosing the stricter rule
cannot under-comply.
**Owner decision required:** confirm the blanket rule, or supply the fleet-specific retention schedule.

---

## C-09 - Postgres Row-Level Security is required by the guidance but not implemented *(gap, recorded)*

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

## C-10 - `GET /api/v1/fleet/vehicles/{id}/movement` conditional scope *(resolved - implemented)*

The brief permits this endpoint *"only if supported by approved Phase 1 scope and available data"*. Telematics
ingestion is explicitly in `SRS-SFL-S166-04` scope and the ingested positions are stored locally, so the data
exists without any new external dependency. Implemented **read-only**, site-scoped, permission-gated, audited on
access, and it surfaces the telematics freshness age so `S166-05`'s stale-data indicator has a drilldown.

---

## C-11 - Notification capability has no provider in Phase 1 *(resolved by port + recorded adapter)*

`SRS-SFL-S166-02` requires notifying responsible users on assignment, overdue, escalation and blockers. No
notification provider is configured for fleet in Phase 1 (S174 owns the emergency notification provider in a
different service).

**Resolution.** `NotificationPort` with a `RecordedNotificationAdapter` that **persists a notification intent and
publishes an outbox event** - it never reports a fake success. When `sfl.fleet.notification.provider` is set to a
real provider that is not configured, resolution **fails loudly** with `IntegrationConfigurationNotFoundException`.

---

## C-12 - `POST /api/v1/fleet/emergency-logistics` deferred *(decision)*

The formal `SRS-SFL-S166-01…05` text contains no emergency-logistics requirement. The endpoint appears only in
workplan §4.3 (mis-mapped to S166-04) and in workplan §6.2, where emergency logistics is a **step in the
cross-service Emergency Incident Response saga** owned by SSEMP and scheduled for wave **W6**, not W5.

**Resolution.** No `/api/v1/fleet/emergency-logistics` endpoint is added - creating one would expand S166 beyond
approved scope. What *is* delivered, traceable to `SRS-SFL-S166-02` (operating mode is a named SLA input):
`OperatingMode.EMERGENCY` on trips and workflow items, emergency-priority SLA rules, and readiness policy support
for emergency-only/restricted-use vehicles.
**Owner decision required:** confirm the saga slice (W6) as the home for emergency logistics, or amend the SRS.

---

## C-13 - SRS filename referenced by `solution.md` does not exist *(documentation defect)* - **CLOSED 1 Aug 2026**

`solution.md` cited `docs/System Mappings and SRS/CLET_Cluster9_SFL_Phase1_SRS_v1.0.docx`; the file on disk is
`docs/System Mappings and SRS/SFL_SRS.docx`. Corrected in `solution.md` and in the go-live readiness pack, which
carried the same name twice - once as its authoritative baseline and once in its source table, both with a `.pdf`
extension that never existed either.

The reason this was worth chasing for a filename: `solution.md` opens by naming the SRS as *the contract*, and the
readiness pack names it as the baseline the Registrar's recommendation rests on. A citation that resolves to nothing
is the one kind of documentation defect that undermines the document containing it.

---

## C-14 - Root `pom.xml` single-app project still compiles legacy IFIMP code - **CLOSED 1 Aug 2026**

`src/main/java/gh/edu/clet/sfl/ifimp/**` was a second Spring Boot application at the repository root,
designated migration/reference material by `docs/architecture/microservices-realignment.md` and not part of the
`services/` reactor. **Removed** - 48 main and 2 test classes, the root `pom.xml` whose only job was to compile
them, and `scripts/dev/run-local.ps1`.

The script is why this was worse than the "reads as live code" it was recorded as. It still launched the legacy
app, and it had been *maintained* as recently as the authentication pass - its `SFL_SECURITY_ENABLED=false` line
carries a comment explaining that the line became load-bearing after A1. So somebody had edited this file
believing it was live. It pointed at a legacy Fleet runtime configuration that no longer matches the normalized
Release 1 ports (`8093` app, `5443` PostgreSQL) or the current compose-backed database, meaning it could not have
worked for weeks.

Nothing in `services/` referenced the package, and `services/pom.xml` inherits from
`spring-boot-starter-parent` directly rather than from the root pom, so the reactor was unaffected. History is
preserved on `archive/java-migration-snapshot-2026-07-21`, `archive/pre-java-cleanup-2026-07-21` and `master`,
all three of which are now on `origin` - until 1 August they existed only on one laptop.

---

## C-15 - Toolchain: build requires JDK 17+, machine default is JDK 11 *(environment note)*

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
| S168_fuel odometer source of truth | Fleet stores odometer with provenance; fuel logbooks will also write odometer readings - reconciliation rule needed | F&L Transportation & Logistics |

---

## C-16 - `requireRecordScope` was enforced nowhere *(defect, closed 31 July 2026)*

| | |
|---|---|
| **Guidance says** | `SRS-SFL-S166-01`: "Users shall only see and update records inside their assigned site scopes and roles." `FleetAccessPolicy.requireRecordScope` javadoc: "This is what keeps the limited driver/mobile user class to their own trips and inspections." |
| **What was true** | The method existed, was unit-tested by `FleetAccessPolicyTest`, and had **one** production call site - `TripApplicationService`, guarding vehicle inspection - where it was passed `null` as the owner reference. `requireRecordScope` returns immediately on a null owner, so the control was a no-op at the only place it was invoked. No read called it at all. |
| **Consequence** | A `FLEET_DRIVER` holding any trip id read that trip in full: route, purpose, operating mode and the driver it belongs to. Site scope was the only boundary, and it is the wrong one for a driver. |
| **Resolution** | The inspection call site now passes the trip's real owner reference. `TripQueryService.findById` applies the same rule, so the record read is narrowed and not only the write. Ownership is the driver's `staffReference` - the value the actor signs in as - resolved from `Trip.driverId` through `DriverProfileRepository`, which is the equivalence fuel already relied on. A supervising `FLEET_TRIP_MANAGE` passes through, so an officer or manager is unaffected. |
| **Proof** | `FleetCriticalScenariosEndToEndTest` scenario 7a: a driver reads their own trip, is refused another driver's **by id**, and an officer reads both. Asserted by refusal rather than by an absent list row, because a narrowing the collection obeys and the record does not is decorative. |

## C-17 - The mandatory-scenario suites had never executed *(process defect, closed 31 July 2026)*

`FleetCriticalScenariosEndToEndTest`, `FuelMandatoryScenariosEndToEndTest`, `FuelGapClosureEndToEndTest`,
`FuelCriticalScenariosEndToEndTest` and `DispatchMandatoryScenariosEndToEndTest` - 67 tests carrying the
SRS mandatory-scenario evidence for S166, S168_fuel and S171 - skipped on every run in this environment.

`FleetPostgresSupport` resolves its database from `SFL_FLEET_LOGISTICS_TEST_DB_URL` first and
Testcontainers second, and nothing set the variable, so resolution fell through to a Docker client that
cannot reach the Windows named pipe. **A skip reads as a pass in a summary line**, which is why this
survived several passes that reported green.

Setting the variable at the e2e container already running on 55443 runs all 67 with no code change:

```
SFL_FLEET_LOGISTICS_TEST_DB_URL=jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e
SFL_TEST_DB_USERNAME=sfl
SFL_TEST_DB_PASSWORD=sfl
```

They pass. `FleetPostgresEndToEndTest` (1 test) remains gated on `@Testcontainers` and still skips;
giving it the same support class is the outstanding half. **This belongs in CI configuration**, because
the evidence is worthless if producing it depends on a developer remembering an environment variable.

## C-18 - The audit hash chain did not verify against a real database *(critical defect, fixed 31 July 2026)*

**`SRS-SFL-S166-03` requires a tamper-evident audit trail. It was not tamper-evident; it was
permanently reporting tampered, and nothing was looking.**

`S152_Gap_And_Conflict_Report.md` §8a recorded two defects that broke the *facilities* chain and warned
in writing that this service was likely to carry both - "that is outside this pass's scope and is not
asserted here, but it should be checked before S166 is relied on for evidence." It was never checked.
Checking it took one test.

| | |
|---|---|
| **Symptom** | `verifyChain()` against the e2e database: `intact=false`, `recordsChecked=201`, `firstDivergentSequence=8`, `reason=Record hash does not match the stored content`. |
| **Cause** | `AuditHashChain.canonical` hashes `occurredAt.toString()`. The JVM clock yields nanoseconds; `timestamptz` stores microseconds. The value hashed on the way in was therefore not the value read back on replay - S152's **D-05**, verbatim. |
| **Scope** | **Every record written by a real clock.** Sequence 7 (`2026-07-21 08:00:00+00`, the tests' fixed clock) verifies; sequence 8 (`2026-07-31 17:36:11.346663+00`, the first record written outside a test) does not. In production, that is every record. |
| **Why four passes missed it** | Every existing chain-intact assertion - `TripApplicationServiceTest`, `VehicleApplicationServiceTest` - uses an in-memory `RecordingAuditPort`, which round-trips nothing and so cannot see a storage-precision defect. The one end-to-end check, `DispatchMandatoryScenariosEndToEndTest:370`, asserts `isNotNull()` rather than `intact()`. And the unit clocks are fixed at whole seconds, which truncate to themselves. |
| **Fix** | `JpaAuditAdapter.storedPrecision()` truncates to `ChronoUnit.MICROS` before the instant is hashed or stored - the same fix facilities applied for D-05. |
| **Proof** | `FleetAuditChainPostgresTest` writes through the real JPA adapter, with a real clock, against PostgreSQL, and replays the whole chain from genesis. It fails on the old code and passes on the new. |

**D-04 does not apply to this service.** S152's other defect - jsonb reordering object keys so the
stored payload is not the hashed payload - is already neutralised here, because
`AuditRecordEntity.toDomain(ObjectMapper)` re-canonicalises the stored JSON through `CanonicalJson` on
read. Fleet solved it by re-canonicalising where facilities solved it by storing `TEXT`. Both work;
recorded so nobody "fixes" one into the other.

### What to do about an existing environment

**Pre-fix records cannot be repaired.** A hash chain has no mechanism for amending history - that is
the property it exists to provide - so any database carrying records written before this fix will keep
reporting tampered at the first of them, forever. There are two honest options and no third:

1. **Truncate the chain and restart it at genesis**, recording in writing that the audit history before
   the cut is unverifiable. Acceptable for the e2e and development databases, which is what was done
   here.
2. **Keep the history and record a known divergence point**, so an auditor is told that records before
   sequence *N* were written under a defect and cannot be replayed, and that everything after *N* can.

**Neither is acceptable silently.** Whichever is chosen must be on the go-live record, because "the
audit chain does not verify" and "the audit chain does not verify for a known and documented reason"
are very different statements to an auditor - and only one of them is survivable.

**No production environment exists yet**, so in practice this is closed by option 1 before first
deploy. The requirement is that the first production record is written by the fixed code.

---

## C-19 - A site could record only one manual fuel purchase *(defect, fixed 5 August 2026)*

**`SRS-SFL-S168fuel-01` requires manual capture. The second manual capture at any site was refused by
a unique constraint, and had been since V10.**

| | |
|---|---|
| **Symptom** | `ERROR: duplicate key value violates unique constraint "uq_fuel_provider_transaction". Detail: Key (site_code, source_system, provider_transaction_id)=(SITE, MANUAL, null) already exists.` |
| **Cause** | The constraint is `UNIQUE NULLS NOT DISTINCT`. A driver at a pump has no provider transaction reference - there is no integration involved - so the column is null, and NULLS NOT DISTINCT makes every null equal to every other. The first manual purchase consumed the key `(site, 'MANUAL', NULL)` and every later one collided with it. |
| **Scope** | Any site whose operator left the optional "provider transaction reference" blank, which is the normal case. It became unconditional when the rebuilt capture form stopped asking for the field at all - correctly, since the field belongs to the provider integration and means nothing to a driver. |
| **Why it was not seen sooner** | Every existing fuel end-to-end scenario supplies a distinct `providerTransactionId` (`PROVIDER-1`, `PROVIDER-2`, …), because they were written to exercise the *provider* path. No test had ever captured two manual transactions at one site. The unit suites use an in-memory repository with no constraints at all. |
| **Fix** | V31 relaxes the constraint to `NULLS DISTINCT`, PostgreSQL's default. Duplicate detection for real provider references is unchanged; rows carrying no reference stop policing each other. This is what the application already assumed - `FuelRepository.findProviderTransaction` returns empty immediately for a null reference, so the duplicate check was never intended to apply to these rows. |
| **Proof** | `FuelPriceAndEvidenceControlsEndToEndTest.manual_captures_do_not_collide_on_a_null_provider_reference` captures two manual purchases at one site and then confirms a re-delivered provider reference is still recognised as the same purchase. |

## C-20 - Evidence upload wrote the child row before its parent *(defect, fixed 5 August 2026)*

**Every evidence upload failed with a 500 the first time it ran against a real database.**

| | |
|---|---|
| **Symptom** | `ERROR: insert or update on table "fleet_evidence_files" violates foreign key constraint "fleet_evidence_files_evidence_id_fkey"` on every call to `POST /api/v1/fleet/evidence/files`. |
| **Cause** | Evidence metadata is mapped with JPA; evidence content is written with plain JDBC; the content table has a foreign key onto the metadata table. Hibernate defers its INSERT to the end of the transaction, so the JDBC write reached PostgreSQL while the parent row existed only in the persistence context. |
| **Why the unit test passed** | `InMemoryEvidenceFileStore` and `InMemoryEvidenceRepository` are maps. There is no persistence context to defer anything and no foreign key to violate, so the whole class of defect is invisible to them - and tightening the doubles would not help, because the behaviour being missed belongs to Hibernate rather than to the code under test. |
| **Fix** | `EvidenceRepository.saveAndFlush`, used by the upload path only. `save` is deliberately unchanged: every other caller writes one row and never reads it back through a second mechanism in the same transaction, and flushing them all would give up the batching write-behind exists for. |
| **General lesson** | Any adapter mixing JPA and raw JDBC inside one transaction has this hazard wherever the JDBC statement depends on a JPA write. There are two such pairings in this service today; both are documented at the port. |

## C-21 - Google Places is disabled on the configured key *(environment defect, open 5 August 2026)*

**Address suggestions cannot work until somebody changes a Google Cloud setting. No code change in
this repository will fix it.**

| | |
|---|---|
| **Symptom** | Typing in an origin, destination or fuel-station field produces no suggestions, ever. |
| **Cause** | Project `428834127125` has **Places API (New)** disabled. Confirmed directly: `places.googleapis.com/v1/places:autocomplete` answers `PERMISSION_DENIED / SERVICE_DISABLED`, and the legacy endpoint answers `REQUEST_DENIED / LegacyApiNotActivatedMapError`. Maps JavaScript *is* enabled, which is why the script loads and only the lookups fail. |
| **Action required** | Enable Places API (New) on the project, then restrict the browser key to the dashboard's HTTP referrers and to the Places and Maps JavaScript APIs. A browser key ships inside the bundle by design; restriction is what makes it safe, not concealment. |
| **What was fixed here** | Only the silence. Every failure used to become an empty suggestion list, which is indistinguishable from "Google knows no such place" - so a disabled API looked exactly like a working field finding nothing. Lookups now return a reason, `PlaceField` shows it under the control, and the full error is logged once. The bootstrap also gained the `callback` parameter that `loading=async` requires. |
| **Not a blocker** | The fields remain free text with browser-stored recent values, which is the required offline posture (SRS §2.5, §23.3) - a driver at a station with no signal must still be able to say where they are. |

## C-22 - The production bundle shipped with no Google Places key *(defect, fixed 5 August 2026)*

**Address autocomplete could never work in the dashboard the services actually serve, and the dev
server hid it.**

| | |
|---|---|
| **Symptom** | Suggestions worked nowhere. On `localhost:5005` the cause was the disabled API (C-21); on `localhost:8093/ui` there was a second cause underneath it, and fixing the first would not have revealed the second. |
| **Cause** | `.env.production` carried `VITE_GOOGLE_MAPS_API_KEY=` - an *empty assignment*, not an absent one. Vite layers `.env` then `.env.[mode]` with the mode file winning, so the empty value overrode the key in `.env`. Every bundle built on a developer machine shipped without one. |
| **Why the intent was right and the result wrong** | The comment explained the reasoning correctly - the file is tracked, and a committed key cannot be rotated quietly - but an empty assignment does not mean "unset", it means "set to nothing", and it outranks the file that has the real value. |
| **Fix** | The assignment is removed rather than blanked. A real `VITE_GOOGLE_MAPS_API_KEY` in the build environment still wins (Vite applies process env after the files), so deployment is unchanged; local builds now inherit `.env` as intended. |
| **Related** | The same session found that the IntelliJ run configurations serve `/ui` from the jar's bundled `dist`, so a UI change is invisible on 8093 until the jar is rebuilt with `-Pui`. Both are now in `CLAUDE.md`, because between them they make a delivered change look undelivered. |

## C-23 - Asset visibility permissions never reached the dashboard *(defect, fixed 6 August 2026)*

**`/actor/permissions` merged three of the four matrices this deployable carries, and the omission
read as a denial rather than as silence.**

| | |
|---|---|
| **Symptom** | `ASSET_REFERENCE_READ` and `ASSET_REFERENCE_MANAGE` were absent from every response, for every role that holds them. |
| **Cause** | `ActorPermissionsController` unioned `FleetPermissionMatrix`, `FuelPermissionMatrix` and `DispatchPermissionMatrix`. `AssetVisibilityPermissionMatrix` arrived when the AVAMP deployable was folded into this service on 5 August and was never added. |
| **Why it is a denial, not an unknown** | The dashboard's fail-open is per *set*, not per service: once any source answers, `granted` is non-null and anything absent from it evaluates false. This is documented at the call site in `actorPermissions.ts` - which also said "four matrices, one deployable, one answer" while three were being asked. |
| **Why nothing broke** | The asset register has no screens yet. It would have broken on the day it got one, presenting as a feature that worked for nobody with nothing in any log to explain it - which is the worst moment to find it. |
| **Fix** | The matrix is merged. Verified live: a `FLEET_MANAGER` now receives 72 permissions including `ASSET_REFERENCE_READ`, and no `EMERGENCY_*` - S174 stays out deliberately, because SSEMP answers for its own. |
| **Proof** | `ActorPermissionsCoverageTest` asserts the union role by role rather than in aggregate, so a fifth matrix added later fails without anybody remembering to extend a fixture. |

## C-24 - AVAMP retired as a programme *(decision, 6 August 2026)*

**A programme with no deployable and no screens.** Its two entities were folded into the fleet
service on 5 August; entitlement to it granted nothing the shell could show, and `programmesFor`
returned a fourth code that no sidebar section, route or label consumed.

Asset visibility survives as a **module of FTLMP** and keeps its own `asset_visibility` schema - the
schema boundary was never the programme boundary, and §2.5 and §2.6 of the SRS are unaffected. The
only behavioural change is that `INTEGRATION_ENGINEER` and `SERVICE_INTEGRATION` now carry three
programme codes instead of four.

Three programmes, three services, three ports - which is the shape the routes, the run
configurations and the startup banners already describe.
