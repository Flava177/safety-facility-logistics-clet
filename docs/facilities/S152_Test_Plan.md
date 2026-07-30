# S152 CAFM / IWMS — Test Plan

- Service: `sfl-facilities-service`
- Run: `cd services && ..\mvnw.cmd -pl sfl-facilities-service -am test`
- Result at the time of writing: **147 tests, 0 failures, 12 skipped** (the skips are the Testcontainers
  class, see §5)

## 1. Layers and what each is for

| Layer | Classes | What it proves | Speed |
|---|---|---|---|
| Domain | `FacilitiesMasterDataTest`, `FacilityAssetTest`, `ReadinessPolicyTest`, `ReadinessBlockerTest`, `AuditHashChainTest` | Invariants and pure rules, exercised directly on the aggregates | instant |
| Policy | `FacilitiesPermissionMatrixTest` | Who may do what | instant |
| Application | `S152MandatoryScenariosTest`, `WorkOrderServiceTest` | Authorisation, idempotency, duplicate detection, readiness recomputation, audit and events | fast, in-memory adapters |
| Contract | `FacilitiesMasterDataControllerTest` | Status codes, the error envelope, the correlation ID | fast, WebMvc slice |
| Architecture | `FacilitiesArchitectureTest` | The dependency rule a compiler cannot check | fast |
| Integration | `FacilitiesMigrationIntegrationTest` | The migrations and constraints against real PostgreSQL | slow, needs Docker |

The application tests run against **in-memory adapters** deliberately: they are about decisions that
live above persistence, and a failure should point at a rule rather than at a mapping. Persistence and
the migrations are covered separately.

## 2. The ten mandatory scenarios

Each is a `@Nested` class in `S152MandatoryScenariosTest`, named for the scenario.

| # | Scenario | Tests | Notes |
|---|---|---|---|
| 1 | Create site → building → floor → room | 2 | Also asserts a floor with an unknown building is `INVALID_PARENT_REFERENCE` |
| 2 | Reject a duplicate room code in the same site | 3 | Includes: an archived record releases its identifier |
| 3 | Reject a negative room capacity | 1 | Rejected in the domain; the controller test covers the Bean Validation path |
| 4 | Register room, device and zone references | 3 | Includes: a device cannot point at a space in another site; a zone member must belong to the zone's site |
| 5 | Submit a readiness assessment with blockers | 8 (with 6, 7) | Includes: an unanswered item counts as failed; an unknown item code is refused |
| 6 | Prevent READY while critical blockers are open | ↑ | The invariant the whole system turns on |
| 7 | Resolve the blocker and mark the room READY | ↑ | Also: a second assessment supersedes the first's blockers |
| 8 | Show the readiness dashboard summary | 3 | Includes the stale-data warning and examination mode |
| 9 | Enforce site-scoped access | 6 | Includes: every denial is audited; a list filters rather than refuses; no-scope has its own code |
| 10 | Publish audit and outbox events for state changes | 4 | Includes: the chain stays intact across a whole workflow |

Two further nested classes cover behaviour the brief implies rather than lists: **asset-driven
readiness** (3 tests) and **the examination lock** (2), plus **idempotency** (3).

## 3. The rules worth reading the tests for

These are the assertions that encode a decision rather than a mechanism.

- `ReadinessPolicyTest.a_high_score_does_not_override_a_critical_blocker` — a room can score 100 and
  still be `BLOCKED`. Severity decides; the score reports.
- `ReadinessPolicyTest.a_never_assessed_space_is_unknown_rather_than_ready` — an unassessed examination
  hall is not a passed one.
- `ReadinessPolicyTest.scores_by_weight_rather_than_by_count` — three items, one weight-3 pass and two
  weight-1 failures, is 60% not 33%.
- `S152MandatoryScenariosTest.an_unanswered_item_counts_as_failed` — a checklist half-skipped is not a
  pass; defaulting the other way would let a hall go ready on an empty submission.
- `…a_low_criticality_failure_is_advisory_and_does_not_block` — a failed noticeboard light does not
  stop an examination.
- `…a_repeated_status_change_does_not_duplicate_the_blocker` — one fault, one blocker.
- `…a_locked_space_still_accepts_a_readiness_outcome` — the lock protects the space's definition, not
  the assessment of it.
- `…every_denial_is_audited` — a refused attempt is the event a compliance review looks for.
- `FacilitiesPermissionMatrixTest.a_facilities_manager_runs_the_estate_but_does_not_declare_examination_mode`
  — declaring an examination is a centre-level decision.

## 4. Contract tests

`FacilitiesMasterDataControllerTest` — 12 tests, one per wire-visible behaviour:

| Behaviour | Expected |
|---|---|
| Create a site | `201` + `Location` header |
| Missing required field | `400 VALIDATION_FAILED` + `fieldErrors[0].field` |
| Negative capacity | `400 VALIDATION_FAILED` on `capacity` |
| Unauthorised scope | `403 UNAUTHORIZED_SCOPE` with the SRS wording verbatim |
| No site scope at all | `403 NO_SCOPE` |
| Duplicate identifier | `409 DUPLICATE_IDENTIFIER` |
| Version conflict | `409 VERSION_CONFLICT` |
| Missing record | `404 RECORD_NOT_FOUND` |
| READY refused | `422 READINESS_BLOCKED` |
| Locked space | `422 READINESS_LOCKED` |
| Correlation ID | echoed |
| Room response | carries derived `availableForBooking` / `availableForExamination` |

The **exact SRS error wording** is asserted, not just the code — the requirement states the message, so
the message is part of the contract.

## 5. Integration tests and the Docker skip

`FacilitiesMigrationIntegrationTest` (12 tests) starts a `postgres:16-alpine` container, runs V1–V8 and
asserts:

- all eight migrations applied and Hibernate `ddl-auto: validate` passed against every entity
- every S152 table exists in the `facilities` schema
- the record-metadata columns are on all seven estate tables
- the pre-S152 `active` column is gone and its meaning moved to `lifecycle_status`
- the audit chain head is seeded at genesis
- the audit table refuses UPDATE and DELETE
- the runtime-configuration defaults are seeded, and only one active value may exist per key and scope
- a blocker cannot be resolved without who, when and why
- a space cannot be locked without recording who locked it
- an asset code is unique within a site
- the V7 checklist seed matches the sites that existed when it ran

It is annotated `@Testcontainers(disabledWithoutDocker = true)`, so a developer without a reachable
Docker daemon still gets a green build. **The 12 skips in the current run are this class** — the Docker
CLI is available in the development environment but the Java client cannot reach the named pipe.

Because those tests were skipped rather than passing, the migrations were verified **directly** instead,
by running the service against the compose e2e database (`localhost:55441`) with the schema dropped
first. That run is what caught the four defects in §7. See the Operations and Verification Guide for
the exact commands.

## 6. Architecture tests

`FacilitiesArchitectureTest` — nine rules:

1. the domain layer imports no framework
2. nothing points into `infrastructure` *(excluding `maintenance`, see below)*
3. the domain does not depend on application or api
4. controllers do not reach into persistence
5. the application layer does not import Spring Data or web types
6. readiness does not reach into another module's persistence
7. the estate's domain and application do not depend on readiness or the dashboard
8. JPA entities live only in persistence packages
9. no provider name outside an adapter

**Rule 2 excludes `maintenance`.** `WorkOrderService` and `FacilityFaultService` inject their Spring
Data repositories directly, so S153's application layer names its own JPA types. That predates S152 and
is out of scope for this pass; the exclusion is recorded rather than silent, and the S153 build should
introduce a repository port and remove it.

**Rule 7 stops at the application boundary.** A controller is the composition root for one request, and
`FacilitiesMasterDataController` legitimately routes `PATCH /rooms/{id}/readiness` to the readiness
service so the critical-blocker rule applies to a manual status exactly as to a derived one.

## 7. What live verification caught that the unit tests did not

Recorded because it is the argument for running the thing:

| Defect | Symptom | Fix |
|---|---|---|
| `CHAR(64)` hash columns | Hibernate `validate` refused to start against PostgreSQL | `VARCHAR(64)` + length check in V5 |
| Two unannotated constructors on `WorkOrderService` | The service could not start at all — pre-existing, never noticed because it had never been run against a database | `@Autowired` on the production constructor |
| Blockers saved before the assessment they reference | FK violation on every assessment with a failure | Build blockers in memory, save the assessment, then save them |
| `jsonb` audit payloads | PostgreSQL reorders object keys, so **every** record replayed as tampered | Store payloads as `TEXT` |
| Nanosecond timestamps | Hashed at nanosecond precision, stored at microsecond — same effect | Truncate to microseconds before hashing |
| Config supersede-then-insert | Partial unique index fired; Hibernate ordered the INSERT before the UPDATE | Flush between them |
| FK violation reported as `DUPLICATE_IDENTIFIER` | Misleading error | Discriminate on SQL state 23503 |

None of these is reachable from an in-memory double. The lesson for S153 and S159 is to run the service
against a database as part of the build, not after it.

## 8. Coverage gaps, stated plainly

- **No persistence test for the estate repositories.** The JPA adapters are exercised only through the
  migration test's schema assertions and the live run. A `@DataJpaTest` per adapter would close it.
- **No concurrency test on the audit chain head.** The pessimistic lock is the right design and is
  untested under contention.
- **No contract tests for the readiness, asset, dashboard or governance controllers.** Only the estate
  controller has them; the others are covered at the application layer and by the live run.
- **The dashboard snapshot writer does not exist**, so nothing tests it.
