# SFL Platform — Architecture & Code-Quality Audit
**Date:** 2026-09-08
**Scope:** `services/` monorepo — `sfl-service-common`, `sfl-facilities-service`, `sfl-fleet-logistics-service`, `sfl-safety-security-service`, `sfl-portal-service`, plus CI (`.github/workflows/backend.yml`) and deploy (`deploy/compose/*`, `deploy/idp/*`). Read-only; no files modified. ~87,000 lines of Java across 894 files, verified via direct reading of controllers/services/entities/migrations/tests plus targeted greps for known anti-patterns.

**Headline judgment:** This is a genuinely well-architected codebase for its stage — consistent hexagonal layering, ArchUnit-enforced boundaries in two of the four modules, real outbox/idempotency patterns, no field injection, no swallowed exceptions, no test-suite smells (`Thread.sleep`, `@Disabled`, flaky patterns) anywhere. It is **not yet** safe to present as "production-ready" without fixing a small number of concrete, verified defects — most importantly one that breaks the deploy stack outright and one that produces silent data loss under concurrent writes in the newest module.

---

## sfl-service-common (shared kernel — every finding here has outsized blast radius)

**[Medium-High] `IntegrationEventEnvelope` has no validation and an 11-arg positional constructor**
`services/sfl-service-common/src/main/java/gh/edu/clet/sfl/common/events/IntegrationEventEnvelope.java:7-19`
A plain record; six consecutive `String` params are trivially transposable with no compiler help; `eventVersion` can be ≤0; core fields can be null. Every service constructs and publishes these — one mis-ordered call corrupts downstream integration events with nothing to catch it.
*Fix:* compact constructor enforcing non-null/non-blank + `eventVersion ≥ 1`; add a builder/named-factory like `ApiError.of`.
*External-assessment impact:* Yes.

**[Medium] `ApiResponse<T>` permits an invalid state**
`services/sfl-service-common/src/main/java/gh/edu/clet/sfl/common/api/ApiResponse.java:3-13`
Nothing stops both `data` and `error` being set, or both null — despite this wrapping every JSON response in the platform.
*Fix:* compact constructor asserting exactly one of `data`/`error` is set, or make the canonical constructor private and force callers through `ok`/`failed`.

**[Medium] `SflPermission`/`SflRole` are single ever-growing, all-domain enums**
`.../security/SflPermission.java` (240 lines, ~140 constants), `.../security/SflRole.java` (49 lines, ~30 roles) — every domain edits the same two files. Deliberate, documented tradeoff, but nothing enforces "additive only" at compile time.
*Fix:* CI/ArchUnit rule forbidding removal of existing constants between releases.

**[Medium, scope-limited] No shared exception→`ApiError` mapping for `AuthorizationException`** — each service must reinvent 403 handling.
`.../security/AuthorizationException.java`. *Fix:* optional auto-configured `@ControllerAdvice`.

**[Low/Improvement] `SiteScopeGuc` builds SQL by string concatenation** (`.../security/SiteScopeGuc.java:76`) — correctly defended by a strict allow-list, but will trip static-analysis scanners and has zero test coverage of its own. *Fix:* add tests asserting `encode()` rejects injection characters; suppress the scanner finding with a documented reason.

**[Low] Thin test coverage** — `AuthorizationPolicyTest` never exercises `hasPermission` against a real matrix; `SiteScopeGuc` untested.

---

## sfl-portal-service (thin by design, but orphaned operationally)

**[High] Excluded from CI entirely**
`.github/workflows/backend.yml:103,109-121` — the `-pl` build list and the "every service context loads" smoke-check loop both omit `sfl-portal-service`. The workflow's own comment describes this exact failure mode happening previously to `sfl-safety-security-service` (silently broken for weeks). It's now reproduced for portal.
*Fix:* add `sfl-portal-service` to both.
*External-assessment impact:* Yes — the CI file's own commentary contradicts its current module list.

**[High] Absent from `docker-compose.microservices.yml`** despite its own pom description calling it "the one dashboard bundle for all three platforms." No deployment path exists for it at all.
*Fix:* add a service block.

**[Medium] `SystemController`'s comment is factually wrong**
`services/sfl-portal-service/src/main/java/gh/edu/clet/sfl/portal/api/SystemController.java:18-21,34-38` — claims it hand-rolls its response "because this module has no dependency on the API envelope," but the module's own `pom.xml:32-36` already depends on `sfl-service-common`, which is where `ApiResponse` lives. The hand-built map duplicates `ApiResponse`'s shape with no compile-time guarantee of sync.
*Fix:* return `ApiResponse.ok(...)` or correct the comment.

**[Low] Zero test files**, compounding with the CI exclusion above — no automated signal at all that the module still boots.

---

## sfl-facilities-service (IFIMP) — strongest module overall

**[High] Outbox drainer marks messages `PUBLISHED` without a broker acknowledgment**
`services/sfl-facilities-service/src/main/java/gh/edu/clet/sfl/facilities/shared/infrastructure/messaging/FacilitiesOutboxDrainer.java:136-145`, `.../AmqpFacilitiesEventTransport.java:45-50`, `application.yml` (rabbitmq block) — `RabbitTemplate.send` returns as soon as the message hits the local socket buffer, not on broker ack; no `publisher-confirm-type`/`publisher-returns` configured. An unroutable or lost message is silently marked done, undermining the class's own documented "at-least-once" claim.
*Fix:* enable `publisher-confirm-type: correlated` + `publisher-returns: true`; only mark `PUBLISHED` on confirm callback.
*External-assessment impact:* Yes — contradicts a headline reliability claim in the module's own docs.

**[Medium] `httpBasic()` enabled with no `UserDetailsService`**
`.../shared/config/FacilitiesSecurityConfiguration.java:41-51` — Spring Boot will auto-generate a random single-user credential logged at startup; an undocumented second auth path on a module whose docs describe only OIDC/JWT.
*Fix:* remove unless a specific M2M use case exists.

**[Medium] Inconsistent pagination across 7 endpoints** — `BookingController.java:150`, `WorkOrderController.java:158`, `FacilityFaultController.java:77`, `ReadinessController.java:119,154`, `BookingSetupTaskController.java:55`, `FacilitiesGovernanceController.java:66` expose only `limit`, no offset/cursor — can never reach row N+1, while `FacilityAssetController.java:67` and `FacilitiesMasterDataController.java:234` correctly use `PageResponse<T>`.
*Fix:* extend `PageResponse` pattern to all seven.

**[Medium] `FetchType.EAGER` on `@OneToMany`** — `ReadinessAssessmentEntity.java:61-63`, `ReadinessChecklistEntity.java:61`. *Fix:* switch to `LAZY` + explicit `JOIN FETCH` where needed.

**[Medium] Controller/HTTP-layer test coverage thin** — only 1 of 14 controllers (`FacilitiesMasterDataControllerTest`) has any MockMvc-level test; the large domain "MandatoryScenarios" suites never exercise `@Valid` binding, JSON (de)serialization, or the exception-handler's HTTP-status routing.

**[Low] Dead exception handler / stale doc** — `.../shared/api/FacilitiesApiExceptionHandler.java:87-99` references `WorkOrderService`/`AuthorizationPolicy`, neither of which exist in the module.

**[Low] Blanket `IllegalArgumentException`/`IllegalStateException` handlers** (`FacilitiesApiExceptionHandler.java:193-206`) mask accidental bugs as 422 domain refusals.

**[Low-Medium] Identical actor/channel/idempotency-key boilerplate copy-pasted into 13 controllers.**

**[Low] Several application services 630–830 lines** (`FacilitiesMasterDataService`, `ReadinessApplicationService`, `BookingApplicationService`) — past comfortable review size.

**[Low] Default `sfl`/`sfl` fallback creds in `application.yml`** — safe since every env overrides it, but a credential scanner will flag it.

*What's solid:* hexagonal layering enforced by `FacilitiesArchitectureTest` (ArchUnit); correctly-implemented `FOR UPDATE SKIP LOCKED` outbox with backoff; clean pom; well-indexed migrations (spot-checked); no flaky-test smells.

---

## sfl-fleet-logistics-service (FTLMP + AVAMP) — largest module, uneven internally

**[High] `fuel` package has drastically different, unreadable code style**
`.../fuel/application/service/FuelApplicationService.java:63,66,68,74,82` — lines 413–587 characters, one statement per file. `.../fuel/infrastructure/persistence/JdbcFuelRepository.java` has lines up to **911 characters**. No formatter (Spotless/Checkstyle) in `pom.xml` to have caught this. Sits right next to a cleanly-formatted `dispatch` package implementing the identical pattern.
*Fix:* add a formatter to the build, reformat `fuel/**`, enforce in CI.
*External-assessment impact:* Yes — immediately visible, undermines confidence built elsewhere in the module.

**[High] `fuel` and `dispatch` have almost no unit-test coverage vs. `fleet`** — `fleet` has 37 test files covering every application service individually with hand-built test doubles; `fuel` has 5, `dispatch` has 4, and **no application service in either has a dedicated unit test** — coverage depends entirely on a handful of slow Testcontainers e2e suites.

**[High] Dispatch carrier-status endpoint has a documented, unresolved auth gap**
`.../dispatch/api/DispatchIntegrationController.java:87-112` — the class's own Javadoc admits this endpoint has only a permission check, deliberately *not* the HMAC signature verification its sibling `scannerEvent` endpoint uses, because "the day that adapter persists anything it becomes a write path with no gate at all." Currently inert (logging-only adapter) but any authenticated caller with the right permission can post arbitrary carrier status today.
*Fix:* route through the same secure integration inbox (signature + allowlist + idempotency) before the adapter is ever made to persist.

**[Medium] N+1 query** — `.../dispatch/application/service/DispatchScanService.java:175-183` loops calling `findItem` per manifest line despite a batch `findItemsByIds` already existing on the same port.

**[Medium] Unbatched per-row inserts in bulk CSV import** — `DispatchScanService.java:84-96`, imports allowed up to 20MB, single long-held transaction.

**[Medium] `FuelSweepScheduler` bypasses its own repository port** — injects `JdbcTemplate` directly and hand-writes SQL, breaking the hexagonal boundary the sibling `DispatchSweepScheduler` respects; untestable with a fake repository.

**[Medium] Generic JDK exceptions load-bearing in the HTTP contract** — `.../fleet/api/FleetApiExceptionHandler.java:128-140` globally maps any `IllegalArgumentException`/`IllegalStateException` to 400/409 and echoes the raw message, so incidental exceptions (e.g. `UUID.fromString` failures) get silently reclassified as client errors instead of alerting as bugs.

**[Medium] Dockerfile port mismatch** — `Dockerfile:9` (`EXPOSE 8080`) vs. `application.yml:61` (`server.port: 8093`), no `ENV SFL_PORT` set. A container run per its own Dockerfile won't receive traffic.

**[Medium] Zero OpenAPI operation-level annotations** — 31 `@Tag` class annotations, **zero** `@Operation`/`@ApiResponse`/`@Schema`/`@Parameter` anywhere in the module.

**[Medium] Missing indexes** — `fuel_reconciliations.policy_id` (FK) and several `fuel_anomaly_cases` reference columns unindexed (`V12__fuel_reconciliation_and_anomalies.sql`), spot-checked not exhaustive.

**[Low] Swagger UI/`api-docs` `permitAll()` even in prod chain** (`FleetSecurityConfiguration.java:60`) — exposes full API surface including a privileged `/outbox/{id}/replay` endpoint.

**[Low] `.env.example` ships `SFL_SECURITY_ENABLED=false` and default `sfl`/`sfl` creds as active example values**, not commented placeholders.

*What's solid:* exhaustive `switch` over error codes with no `default` (compile-time safety net); whitelisted SQL sort keys; correct outbox/inbox with HMAC+idempotency for the one real external boundary; secure-by-default JWT config with loud warning if disabled; zero field injection, zero swallowed exceptions.

---

## sfl-safety-security-service (SSEMP) — newest module, real concurrency defects

**[CRITICAL] Optimistic locking is not DB-enforced for S160/S163 — real lost-update race**
`.../incident/infrastructure/persistence/SecurityIncidentJpaEntity.java:86-87` and the equivalent in `VisitorVisitJpaEntity.java` map `record_version` as a plain `@Column`, not `@Version`. `SecurityIncidentRepositoryAdapter.java:30-34` issues a plain `UPDATE` with no `WHERE record_version=?` guard. `RecordMetadata.requireVersion()` only compares against the in-memory value read at the start of the *same* transaction — it cannot stop two concurrent transactions that both read version N from both succeeding, silently overwriting each other. Contrast the older S174 module (`emergency/infrastructure/persistence/JdbcEmergencyRepository.java:159-176`), which does a genuine `UPDATE ... WHERE id=? AND version=?` compare-and-swap. Corroborating: `incident/api/IncidentApiExceptionHandler.java:78-83` has a handler for `OptimisticLockingFailureException` that can never fire on this code path — dead code masking the gap.
*Fix:* add `@Version`, or replicate S174's CAS pattern; remove/repurpose the now-reachable handler.
*External-assessment impact:* Yes — a live two-concurrent-PATCH test finds this immediately, and it contradicts the module's own Javadoc.

**[High] Shared global audit sequence races across subdomains**
`platform/infrastructure/persistence/AuditAdapter.java:89-93` computes `SELECT MAX(sequence_no)+1` then inserts, no lock/sequence, against a `UNIQUE(sequence_no)` constraint (`V10__safety_security_platform_audit.sql`). Any two concurrent audit writes anywhere in the service (visitor check-in racing incident triage) can collide and abort an otherwise-valid transaction.
*Fix:* use a Postgres `SEQUENCE` or `SELECT ... FOR UPDATE` on a counter row.

**[High] Zero test coverage for version-conflict/optimistic-locking behavior anywhere in the module** — no test would have caught the Critical finding above, and none exists to prevent recurrence.

**[High] Zero test coverage for authorization/permission-denial paths** — no test anywhere asserts that an actor lacking the required permission is actually rejected, despite `access.require(...)` gating every mutating operation.

**[Medium] CAPA closure gate has a narrow TOCTOU window** — `CorrectiveActionJpaRepository.countOpenMandatory` reads under default `READ COMMITTED` with no row lock inside `IncidentClosureService.java:42-63`; a mandatory CAPA opened concurrently is invisible to the closing transaction.

**[Medium] Pagination inconsistent/regressed within the same service** — the older S174 module uses proper `Pageable`/`Page<>`; the newer `SecurityIncidentController.java:170-178` and `VisitorVisitController.java:123-133` return a bare `List` capped at 500 with no offset/cursor/total-count.

**[Medium] Outbox has no drainer at all** (self-documented: `OutboxEventPublisher.java:22-23`, "events are recorded, not delivered") — every `events.publish(...)` call across all three subdomains currently has no consumer.

**[Medium] 14 tests for 156 source files, no service-layer unit tests** — `IncidentClosureService`, `IncidentTriageService`, `VisitorDecisionService`, etc. have no dedicated unit test; coverage relies on domain tests + broad e2e scenarios.

**[Improvement] `RecordedWatchlistGateway` always returns "not flagged," no environment guard** against it silently running in a production profile with real visitors.

**[Improvement] `RecordMetadata` duplicated near-verbatim 3×** across incident/visitor/emergency.

*Important positive finding:* the specific bugs the project history flagged as previously fixed — the self-approval check (`VisitorDecisionService.java:60-65`) and the CAPA-closure hard gate (`SecurityIncident.java:150-159`) — are correctly implemented **and** covered by tests (`VisitorMandatoryScenariosEndToEndTest.java:117`, `SecurityIncidentDomainTest.java:140-154`). The defect isn't in that logic; it's the absence of DB-level concurrency enforcement around it, which is a different, previously-unverified property. Migrations are well-indexed, including a purpose-built partial index for the closure-gate query; secrets/config hygiene is good and fails closed.

---

## Cross-cutting: CI & Deploy

**[Critical] Keycloak realm import path is broken**
`deploy/compose/docker-compose.microservices.yml` mounts `../keycloak:/opt/keycloak/data/import:ro`, but no `deploy/keycloak` directory exists — the realm file actually lives at `deploy/idp/sfl-realm.json`. Keycloak boots with no `sfl` realm, its healthcheck never passes, and every app container's `depends_on: condition: service_healthy` blocks forever — **the documented microservices compose stack cannot start.**
*Fix:* mount `../idp:/opt/keycloak/data/import:ro`.
*External-assessment impact:* Yes — this is one of the first things a reviewer would try.

**[Medium] Postgres major version drift** — `docker-compose.dev.yml` uses `postgres:18-alpine`; CI and `docker-compose.microservices.yml` use `postgres:16-*`.

**[Medium] Dependency versions duplicated identically across 3 sibling module poms** (`archunit.version`, `springdoc-openapi.version`, `testcontainers.version`) instead of centralized in the parent — currently consistent by luck, not enforced.

**[Low] Seed credentials in plaintext in `deploy/idp/sfl-realm.json`** (`"secret": "change-me"`, ~20 `Password@Clet1` persona passwords) — low risk as dev-only seed data, but nothing in the repo enforces this file stays dev-only.

**[Low/Improvement] No health checks or resource limits on app/DB containers** in `docker-compose.microservices.yml`, except Keycloak.

---

## Executive Readiness Score: **6.5 / 10**

The engineering discipline is real and above-average for a codebase this size — hexagonal architecture, ArchUnit boundary enforcement, secure-by-default configs with documented incident history, honest self-documentation of known gaps (S174 outbox drainer absence, dispatch's inert auth gap, watchlist stub). That's not common. But it is not yet safe to present unqualified as "production-ready": there is one deploy-blocking defect (Keycloak mount), one genuine data-integrity race in the newest module (optimistic locking), and a visible quality cliff in one large package (`fuel`). Fix the "must fix" list below and this moves comfortably into 8+ territory.

## Top 10 Issues to Fix Before Publishing

1. **[Critical]** Keycloak realm import path broken — compose stack cannot start (`docker-compose.microservices.yml`)
2. **[Critical]** Optimistic locking not DB-enforced for S160/S163 — real lost-update race (`SecurityIncidentJpaEntity.java`, `VisitorVisitJpaEntity.java`)
3. **[High]** Outbox drainer marks messages published without broker ack (`FacilitiesOutboxDrainer.java`, facilities)
4. **[High]** Dispatch carrier-status endpoint has no signature verification, documented gap (`DispatchIntegrationController.java`, fleet)
5. **[High]** `fuel` package code style is wildly inconsistent with the rest of the codebase (lines up to 911 chars)
6. **[High]** Zero authorization-denial and version-conflict test coverage in safety-security-service
7. **[High]** `sfl-portal-service` excluded from CI and absent from deploy compose entirely
8. **[High]** Shared audit sequence races under concurrency, causing spurious transaction failures (safety-security)
9. **[Medium-High]** Shared-kernel data contracts (`ApiResponse`, `IntegrationEventEnvelope`) lack validation — outsized blast radius
10. **[Medium]** Dockerfile port mismatch in fleet-logistics service (`EXPOSE 8080` vs. actual port 8093)

## Prioritized Remediation Plan

**Must fix before push (correctness, security, or deploy-blocking):**
- Fix the Keycloak realm mount path
- Add `@Version`/DB-level CAS for `SecurityIncident`/`VisitorVisit` records
- Fix the facilities outbox drainer to wait for RabbitMQ publisher confirms before marking `PUBLISHED`
- Fix or explicitly gate the dispatch carrier-status endpoint's auth
- Fix the fleet-logistics Dockerfile port mismatch
- Add `sfl-portal-service` to CI and to the microservices compose file
- Fix the safety-security audit-sequence race (use a real `SEQUENCE`)

**Strongly recommended (before an external technical assessment):**
- Reformat the `fuel` package and add a formatter (Spotless/Checkstyle) enforced in CI
- Add application-service unit tests for `fuel`/`dispatch` and for safety-security's incident/visitor services
- Add authorization-denial and optimistic-lock regression tests in safety-security
- Harden `ApiResponse`/`IntegrationEventEnvelope` with compact constructors/validation
- Standardize pagination (facilities' 7 bare-`limit` endpoints; safety-security's S160/S163 vs. S174)
- Add OpenAPI `@Operation`/`@ApiResponse` annotations across fleet-logistics
- Remove or justify `httpBasic()` in facilities' security config
- Add a row lock (or pre-commit re-check) to the CAPA closure gate
- Centralize dependency version management in the parent pom
- Add container health checks and pin one Postgres version across environments

**Polish:**
- Remove the dead/stale exception-handler comment in facilities (`FacilitiesApiExceptionHandler.java:87-99`)
- Collapse the 13-controller actor/channel/idempotency-key boilerplate into a `HandlerMethodArgumentResolver`
- Split facilities' 630–830-line application services
- Fix `SystemController`'s inaccurate comment in `sfl-portal-service`
- Add a startup guard warning if `RecordedWatchlistGateway` is active outside dev/test
- Add tests for `SiteScopeGuc`/`AuthorizationPolicy` in the shared kernel
- Clean up `.env.example` so insecure defaults aren't the active example values

---

**What was not exhaustively verified** (flagged honestly rather than omitted): full body of every application service in fleet-logistics; all 32 fleet-logistics migrations for index coverage beyond the sample checked; OpenAPI schema completeness beyond annotation presence; whether the full test suites actually pass end-to-end; idempotency handling on visitor check-in/out specifically.
