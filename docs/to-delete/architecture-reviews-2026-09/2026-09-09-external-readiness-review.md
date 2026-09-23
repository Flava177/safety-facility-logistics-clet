# External-Assessment Readiness Review — safety-facility-logistics-clet

**Date:** 2026-09-09
**Scope:** Full repository as of `main` @ `cd03b9c` (PR #25 merged) — 5-module Maven reactor (`sfl-service-common`, `sfl-facilities-service`, `sfl-fleet-logistics-service`, `sfl-safety-security-service`, `sfl-portal-service`), the frontend React app, all deployment/CI configuration, and all documentation.
**Method:** Read-only. Seven independent review passes (architecture/code quality, API design, security re-verification, test quality, data/persistence/concurrency, operational readiness, documentation/onboarding), each verifying claims directly against current source rather than trusting prior audit reports. No source code was modified to produce this report.
**Relationship to prior reports:** This review treats `docs/architecture/2026-09-08-code-quality-audit.md` and `docs/security/2026-09-09-security-audit.md` as historical context only. Every claim in both was independently re-checked; where a prior fix is confirmed to have landed correctly, that is stated explicitly rather than assumed.

---

## 1. Repository readiness verdict

## **Conditionally Ready**

The engineering foundation is genuinely strong — real hexagonal layering enforced by ArchUnit, zero cross-service coupling, a CI pipeline that hard-fails on two specific past incidents (silently-skipped E2E suites, an unbuilt module) rather than trusting a green checkmark, and a security remediation that held up under independent re-verification of all 10 of its claimed fixes. But this review surfaced two **High**-severity issues a senior external reviewer would flag immediately — a systemic, unenforced optimistic-locking gap in the largest module (silent lost updates under concurrency) and a fresh regression where the repo's own documented local-run path now leaves every service fully unauthenticated-yet-inaccessible (a repeat of an incident this codebase already had once). Neither requires architectural rework, but neither should ship to an external review unaddressed.

---

## 2. Quality scorecard

| Dimension | Score | Note |
|---|---|---|
| Architecture & maintainability | **7/10** | Layering and independence are real; one 2,313-line God class was missed by the prior remediation that fixed four smaller ones |
| API design | **7/10** | Consistent conventions on ~95%+ of the surface; pagination shape is fragmented and duplicated across modules |
| Security | **7/10** | All 10 previously-claimed fixes independently confirmed correct; one new High-severity local-dev regression found |
| Testing | **8/10** | 137 test files, zero flaky-pattern smells, a CI pipeline hardened against its own past incidents; ~38 application services covered only by E2E, not unit tests |
| Data / persistence | **6/10** | Migration discipline is excellent (61 files, no repeat of a known bug class); a systemic missing-`@Version` gap in facilities-service is a real, unaddressed correctness risk |
| Operational readiness | **6.5/10** | Outbox resilience and CI rigor are real strengths; CI never builds the Docker images it ships, which is exactly the blind spot that let a Dockerfile bug through recently |
| Documentation | **6/10** | Unusually candid and specific where current; three primary onboarding docs all say "three deployable services" when a fourth (`sfl-portal-service`) has existed and been fully wired for a while |

**Overall: ~6.8/10 — Conditionally Ready.**

---

## 3. Detailed findings

Severity scale: Critical (data loss / security bypass in a realistic path) → High (real defect, bounded blast radius) → Medium → Low → Info (positive finding or cosmetic).

### 3.1 Architecture & code quality

| Sev | Location | Finding | Fix |
|---|---|---|---|
| **Critical** | `sfl-fleet-logistics-service/.../fuel/application/service/FuelApplicationService.java` (2,313 lines, 82 methods) | A God class mixing policy CRUD, transaction capture/reconciliation/void, driver-logbook lifecycle, anomaly detection, posted-price recording, CSV reporting, and audit history — more than 3× the size of any of the 4 classes a prior remediation already split, but untouched by that effort. | Apply the same collaborator-extraction pattern already proven in this repo (`BookingLifecycleCommands`, `ReadinessBlockerOperations`): split into `FuelPolicyCommands`, `FuelTransactionCommands`, `FuelLogbookCommands`, `FuelAnomalyCommands`, `FuelReportingQueries`. |
| Medium | `JdbcFuelRepository.java` (1,730 lines), `JdbcDispatchRepository.java` (977), `JdbcEmergencyRepository.java` (927) | Large hand-written JDBC repositories — many independent methods, not deeply coupled, but hard to navigate. | Split by aggregate behind the existing repository port interfaces. |
| Medium | `TripApplicationService.java` (659 lines), `VehicleApplicationService.java` (548 lines) | Both past the ~400-line threshold the prior remediation used as its own trigger; neither was flagged. | Same extraction pattern, lower priority than Fuel. |
| Low | `sfl-fleet-logistics-service/.../assets/` (AVAMP) | No `AssetArchitectureTest` exists, unlike `FuelArchitectureTest`/`DispatchArchitectureTest`/`FleetArchitectureTest` in the same module — this sub-module's layering has no automated enforcement. | Add `AssetArchitectureTest` mirroring the existing three. |
| Info | Whole reactor | Zero `TODO`/`FIXME`/`XXX` markers, zero `System.out.print`/`printStackTrace()`, zero `.orig`/`.bak` files, zero commented-out code, zero `@Deprecated` usages anywhere in `src/main`. Cross-service coupling is genuinely zero (grep-confirmed). | — |

### 3.2 API design

| Sev | Location | Finding | Fix |
|---|---|---|---|
| Medium | `sfl-facilities-service/.../shared/api/PageResponse.java` vs. 4 near-identical pagination records in the other modules (`FuelPageResponse`, `DispatchPageResponse`, fleet's own `PageResponse`, `EmergencyPageResponse`) | Facilities' page envelope (`items`, 5 fields) is structurally incompatible with the other 4 modules, which are byte-for-byte identical to each other under different names — duplicated 4× instead of shared once, and facilities diverges from all of them. | Promote one shared `PageResponse<T>` into `sfl-service-common`; align facilities' field names to match. |
| Medium | `DispatchManifestController.java:127-169` — `AssignTripRequest(UUID tripId, UUID vehicleId, UUID driverId)` | The only required `@RequestBody` in the entire codebase with no `@Valid`/`@NotNull` — 147 of 159 other `@RequestBody` usages pair validation correctly. | Add `@NotNull` to the three fields and `@Valid` on the controller parameter. |
| Low | `sfl-safety-security-service` controllers | Only ~30% of endpoints (19/64) carry Swagger `@Operation` docs vs. ~92-98% in facilities/fleet-logistics. | Backfill the remaining ~45 endpoints. |
| Low | `EmergencyApiExceptionHandler.java` vs. `IncidentApiExceptionHandler.java`/`VisitorApiExceptionHandler.java` | Three `@ControllerAdvice` beans in one module handle overlapping exception types, disambiguated only by two of three opting into `@Order`/package-scoping; correct today but fragile against a future fourth handler. | Consolidate to one exception-handler bean per service, or scope all three consistently. |
| Info | Whole reactor | `/api/v1` prefix, the `ApiResponse<T>` envelope, and `@Valid` pairing are applied with real, verified consistency (59 controllers, 159 `@RequestBody` sites checked); the 3 raw-`String` CSV export exceptions are deliberate and correct. | — |

### 3.3 Security (re-verification of the 2026-09-09 remediation, plus fresh findings)

All 10 previously-claimed fixes (weak default credentials, port binding, non-root containers, CSV formula injection ×3, rate limiting, the `@Profile` dev-bypass gate mechanism itself, Content-Disposition sanitization, the stray `httpBasic()`, Swagger consistency, scheduler pool sizing, and the full `OidcRolesConverter`/`OidcRoleClaims` rollout across all 9 call sites) were independently re-read in the current code and confirmed correct, with zero remaining `realm_access` references anywhere in `src/main`.

| Sev | Location | Finding | Fix |
|---|---|---|---|
| **High** | `services/.run/IFIMP facilities 8091.run.xml`, `FTLMP fleet logistics 8093.run.xml`, `SSEMP safety security 8092.run.xml`; `docs/development/run-spring-boot-locally.md:56-58,91` | The `@Profile({"test","local","dev"})` gate added to each `developmentSecurity` bean means `SFL_SECURITY_ENABLED=false` alone no longer opens the dev chain — an active Spring profile is also required. The 3 checked-in IntelliJ run configs and the doc's own instructions set the env var but never set a profile. Following either exactly as written registers **zero** `SecurityFilterChain` beans, falling through to Spring Security's secure-everything default — including the health probe, which is the exact incident `SafetySecurityConfiguration.java`'s own Javadoc already documents as having happened once. | Add `ACTIVE_PROFILES=local` to all 3 `.run.xml` files and an equivalent `-Dspring-boot.run.profiles=local` instruction to the run-locally doc. |
| Medium | `sfl-service-common/.../web/ratelimit/FixedWindowRateLimiter.java:23,46-52` | The per-key `ConcurrentHashMap` has no eviction/TTL — unbounded growth if `sfl.rate-limit.enabled=true` is ever turned on in a long-running deployment with many distinct client IPs × paths. Currently dormant (off by default) but undocumented as a limitation. | Sweep stale entries or cap with a bounded eviction policy (e.g. Caffeine `expireAfterWrite`). |
| Medium | `docker-compose.microservices.yml:109-111`, `docker-compose.dev.yml:84-86` | `ZITADEL_MASTERKEY` and the Zitadel admin password default to fixed, committed placeholders (`ChangeThisMasterkey32Characters!`, `Change-Me-1!`) — the same class of issue M1/M2 were supposed to close, but the masterkey is more consequential (it's Zitadel's own encryption key for stored secrets/tokens). | Same treatment as M1/M2: fail fast with no default rather than a guessable one. |
| Info | 9 call sites across 3 `SecurityConfiguration` + 6 `*ActorResolver` classes | The `sfl.security.roles-claim` default literal is duplicated verbatim 9 times rather than centralized. Cosmetic, not a bug — verified consistent everywhere today. | Hoist into a `public static final String` on `OidcRoleClaims`. |

### 3.4 Testing

| Sev | Location | Finding | Fix |
|---|---|---|---|
| Low | ~38 application-service classes reactor-wide (e.g. `BookingApplicationService`, `IncidentReportingService`) | No dedicated unit test class — coverage comes entirely from slower, DB-backed E2E scenarios. Genuinely exercised, just with no fast (<1s) feedback loop and less precise failure localization. | Add narrow unit tests (mocked ports) for the highest-risk methods — state-transition guards, financial calculations. |
| Info | `FuelMandatoryScenariosEndToEndTest.java` (15 call sites) | Uses real `Instant.now()` instead of an injected `Clock` for hour/day-scale time-window fixtures. Theoretical flakiness at a window boundary; practical risk near zero given the scale. | Inject `Clock` only if this becomes actually flaky. |
| Info (positive) | Whole reactor | 137 test files; zero `Thread.sleep`, zero `@Disabled`/`@Ignore`; 101/137 files use AssertJ consistently, zero raw JUnit `assertEquals`; 7 ArchUnit classes with real, non-vacuous rule bodies (spot-checked directly). | — |
| Info (positive) | `.github/workflows/backend.yml` | CI provisions real Postgres containers for all 3 data-owning modules and has an explicit step that fails the build if any mandatory-scenario suite's Surefire report shows a `skipped` count > 0 — built in direct response to a documented past incident (102 tests silently skipped for four build passes). Also asserts every module actually compiles into the reactor, in response to a second documented past incident (a module shipped with no `@SpringBootApplication` class, undetected). | — |

### 3.5 Data / persistence / concurrency

| Sev | Location | Finding | Fix |
|---|---|---|---|
| **High** | `sfl-facilities-service/.../shared/infrastructure/persistence/RecordMetadataEmbeddable.java:35-36`, and by extension all 33 `@Entity` classes in the module | `record_version` is a plain `@Column`, never a real JPA `@Version` — confirmed zero genuine `@Version` annotations exist anywhere in facilities-service (the one grep hit is inside a Javadoc comment). Every `expectedVersion` check compares the version in application memory against a value read earlier in the same request; the actual `UPDATE` Hibernate issues carries no `WHERE record_version = ?` guard. Two concurrent requests that both read version 5 can both pass their check and both write — a silent lost update, with no error surfaced to either caller. This is the same TOCTOU bug class as the CAPA-closure race this codebase already found and fixed via SERIALIZABLE isolation, except unaddressed here and systemic rather than one call site. Fleet-logistics (11 entities) and safety-security (2 entities) both use real `@Version` correctly — facilities-service is the outlier. | Add `@Version` to `RecordMetadataEmbeddable.recordVersion` (Hibernate supports `@Version` on an embedded field) so Hibernate enforces the check atomically at the SQL level. |
| Medium | `sfl-facilities-service/.../db/migration/V14__row_level_security.sql` vs. fleet-logistics/safety-security migrations | Postgres Row-Level-Security (the DB-level backstop for `SiteScopeGuc`-based tenant isolation) exists only in facilities-service. The other two modules rely entirely on application-layer `authorization.require(...)` checks with no independent DB-level guard if a code path ever misses one. | Extend the same RLS pattern to the other two modules, or document via ADR that this is an intentional, facilities-only decision. |
| Low | Same file, ~lines 90-101 | Facilities' RLS is applied via a one-time `DO $$ ... LOOP` that ran once at V14 — a facilities table added in a later migration with a site-scope column would not automatically get RLS, and nothing currently catches that omission. | Add a test that fails if a table with a site-scope column lacks a corresponding RLS policy, or note the one-time nature in the migration's own comment. |
| Info (positive) | 61 migration files across both modules, read/grepped in full | No repeat anywhere of the `setval`-on-empty-table bug class that caused the one migration bug this codebase already found and fixed (V13, safety-security). Every `NOT NULL ADD COLUMN` has a default; every backfill `UPDATE` has a proper `WHERE` guard. Migration craftsmanship is genuinely strong. | — |
| Info (positive) | Whole reactor | Transaction boundaries correctly avoid synchronous broker/HTTP calls inside `@Transactional` methods — the outbox pattern (write-then-drain-async) is consistently followed, not just claimed. | — |

### 3.6 Operational readiness

| Sev | Location | Finding | Fix |
|---|---|---|---|
| Medium | `.github/workflows/backend.yml:107` | CI runs `mvn clean test`, never `package`/`verify` with a Docker build step — the 4 Docker images are never built in CI. This is precisely the blind spot that let the sibling-module `COPY` bug (fixed in the immediately-preceding PR) go undetected until manually caught. | Add a `docker compose ... build` (or per-Dockerfile `docker build`) step to CI. |
| Medium | `docker-compose.microservices.yml:93`, `docker-compose.dev.yml:67` | `ghcr.io/zitadel/zitadel:latest` is unpinned, while every other image in both files is version-pinned. This is the platform's identity provider — a security-critical component whose specific behavior (issuer format, the `zitadel ready` healthcheck subcommand, `FIRSTINSTANCE` bootstrap vars) was verified against v4.17.3 specifically. A later `docker compose pull` silently moves to whatever `latest` then resolves to. | Pin to `ghcr.io/zitadel/zitadel:v4.17.3` (or the currently-deployed version) in both compose files. |
| Medium | `sfl-portal-service/src/main/resources/application.yml` (4 lines total) | No `management:` block at all, unlike its 3 siblings — no tuned liveness/readiness probe groups, and `/actuator/metrics`/`/actuator/prometheus` aren't exposed by default. A Kubernetes-style deployment couldn't distinguish portal "starting" from "broken." | Add the same `management:` block the other 3 services carry. |
| Medium | `sfl-fleet-logistics-service/pom.xml:184-208` | Spotless formatting enforcement is scoped to `fuel/**/*.java` only — one subpackage of one of five modules. The rest of the reactor has zero automated formatting enforcement. | Extend the include pattern reactor-wide, or explicitly document the narrow scope as intentional. |
| Low | Whole reactor | No `application-<profile>.yml` exists anywhere — every module runs off one `application.yml` with env-var-fallback defaults for dev and prod alike. Already flagged in the 2026-09-08 audit; still unaddressed. | Add at minimum a thin `application-prod.yml` per service that tightens local-dev-friendly defaults. |
| Low | Whole reactor | No structured/JSON logging anywhere — zero `logback*.xml` files, zero log-shipping dependency, only plain-text console output. | Adopt a JSON encoder for non-dev profiles, or document the intended log-shipping strategy if one exists outside this repo. |
| Info | `git branch -r \| grep dependabot` → 27 branches | 27 automated dependency-update PRs opened in one window, none yet reviewed. Not a defect — the scanning is now genuinely active — but a real triage backlog worth naming before it silently ages out. | Triage in a batch; the enforcer/CI gate protects against a bad merge in the meantime. |
| Info (positive) | Whole reactor | The transactional-outbox pattern gives real restart-safety to async event delivery; `/actuator/metrics`/`/actuator/prometheus` are correctly never in a `permitAll()` list on any of the 3 services that configure them; no hardcoded secrets in the CI workflow itself. | — |

### 3.7 Documentation & onboarding

| Sev | Location | Finding | Fix |
|---|---|---|---|
| **High** | `README.md:13`, `services/README.md:3`, `docs/development/run-spring-boot-locally.md:8` | All three primary onboarding documents state the reactor has "three deployable services." `sfl-portal-service` is a real, independently-buildable, Dockerfile-having, docker-compose-wired 4th service (port 8090) that appears in **zero** mentions across all three docs — a new reviewer would not know it exists from the onboarding material alone. | Add `sfl-portal-service` to all three module tables; correct "three" to "four" throughout. |
| Medium | `README.md:161-166` | States the SRS, ADRs, gap reports, and runbooks are "maintained outside this repository" — directly contradicted by `docs/adr/000*.md` and `docs/runbooks/` (5 files), both present in-repo. | Reword to clarify what's actually external vs. mirrored in-repo. |
| Medium | Root `.env.example:1-4` | References a nonexistent pre-migration single-app prototype (port 8081, `sfl_java` database, generic `SFL_DB_*`/`SFL_PORT` vars matching no current service) — the app itself was removed per the run-locally doc's own history note, but this file wasn't. | Delete or rewrite to match current multi-service reality. |
| Medium | `.env.example:7`, `deploy/env/.env.example:4` (`SFL_REDIS_HOST`/`SFL_REDIS_PASSWORD`) | Redis is provisioned in docker-compose but read by zero application code anywhere in the reactor (`grep` for `spring.data.redis`/`RedisTemplate` returns nothing) — dead, misleading env-var documentation. | Remove the lines, or note Redis as reserved-but-unused infrastructure (as `RateLimitFilter`'s own Javadoc already frames it). |
| Medium | 3 of 4 services | Only `sfl-fleet-logistics-service/.env.example` exists; facilities, safety-security, and portal have no per-service env template, despite `services/README.md` documenting running each independently. | Add matching `.env.example` files for the other 3 services. |
| Medium | `docs/development/run-spring-boot-locally.md:100` | "119 tests at the last count" — very likely stale; the most recent merged PR alone added 5 new test classes with well over a dozen new test methods. | Drop the specific number, or point at running `mvn test` directly rather than trusting a hardcoded count. |
| Low | Whole reactor | No `CONTRIBUTING.md` or PR template — the Spotless requirement (fleet-logistics only) is undocumented anywhere a first-time contributor would see it before their PR fails CI on formatting. | One paragraph in `services/README.md`'s build section. |
| Low | Whole reactor | No Kubernetes/Helm manifests or cloud-deployment documentation exist — a real, plainly-reportable gap, not fabricated. | At minimum, a short note stating this is intentionally out of scope for the current phase. |
| Info (positive) | `docs/runbooks/` (5 files), `docs/development/run-spring-boot-locally.md:65-67`, `services/README.md:115-129` | Runbooks cover real first-week failure modes substantively; the Zitadel migration is correctly reflected (not stale Keycloak language) in the run-locally guide; the API-conventions section is a genuine, specific contract, better than most repos this size provide. | — |

---

## 4. "Must fix before external review" checklist

These are the findings a senior reviewer or security assessor would very likely raise unprompted. None require architectural rework; all are bounded, concrete changes.

- [ ] **Add real JPA `@Version` to `RecordMetadataEmbeddable.recordVersion`** in facilities-service (§3.5) — closes a systemic silent lost-update race across 33 entities
- [ ] **Fix the local-dev run path**: add `ACTIVE_PROFILES=local` to the 3 checked-in `.run.xml` files and update `run-spring-boot-locally.md` accordingly (§3.3) — currently the documented path leaves every endpoint, including health, fully locked with no dev bypass
- [ ] **Split `FuelApplicationService.java`** (2,313 lines) using the already-proven collaborator-extraction pattern (§3.1)
- [ ] **Add a Docker-build step to CI** (§3.6) — closes the exact blind spot that already let one Dockerfile regression through
- [ ] **Pin `ghcr.io/zitadel/zitadel:latest`** to a specific version in both compose files (§3.6)
- [ ] **Fail-fast instead of defaulting** `ZITADEL_MASTERKEY`/admin password rather than shipping a guessable placeholder (§3.3)
- [ ] **Correct "three deployable services" → four** across `README.md`, `services/README.md`, `run-spring-boot-locally.md`, and add `sfl-portal-service` to every module table (§3.7)
- [ ] **Delete or rewrite the stale root `.env.example`** referencing a removed prototype app (§3.7)

## 5. "High-impact polish" checklist

Worth doing, lower urgency — none block an external review on their own.

- [ ] Unify pagination response shape into one shared `PageResponse<T>` in `sfl-service-common` (§3.2)
- [ ] Backfill Swagger `@Operation` docs in safety-security-service (~30% → match siblings' ~92-98%) (§3.2)
- [ ] Add `@Valid`/`@NotNull` to `DispatchManifestController.AssignTripRequest` (§3.2)
- [ ] Extend Postgres RLS to fleet-logistics/safety-security, or document the facilities-only decision via ADR (§3.5)
- [ ] Add eviction/TTL to `FixedWindowRateLimiter` before rate limiting is ever enabled in production (§3.3)
- [ ] Extend Spotless reactor-wide, or explicitly document its fuel-only scope (§3.6)
- [ ] Add a thin `application-prod.yml` per service (§3.6)
- [ ] Add structured/JSON logging for non-dev profiles (§3.6)
- [ ] Add `.env.example` for facilities, safety-security, and portal (§3.7)
- [ ] Add `CONTRIBUTING.md` naming the Spotless requirement (§3.7)
- [ ] Add `management:` block to portal-service's `application.yml` (§3.6)
- [ ] Add unit tests for the highest-risk of the ~38 application-service classes currently covered only by E2E (§3.4)
- [ ] Centralize the 9-site `roles-claim` default literal into one constant (§3.3)
- [ ] Triage the 27 open Dependabot PRs (§3.6)
- [ ] Split `TripApplicationService`/`VehicleApplicationService` once Fuel is done (§3.1)
- [ ] Add `AssetArchitectureTest` for fleet-logistics' AVAMP sub-module (§3.1)
- [ ] Reword `README.md`'s "docs live outside this repo" claim (§3.7)

## 6. Suggested final pre-push validation sequence

```bash
# 1. Clean build across the whole reactor
mvn -q clean compile

# 2. Full verify — runs the enforcer plugin, Spotless check (fuel package only, currently), and every test
#    (requires the 3 Postgres test-DB env vars; see docs/development/run-spring-boot-locally.md)
mvn -q verify

# 3. Secret scan over full git history (config already in .gitleaks.toml)
gitleaks detect --source . --log-opts="--all"

# 4. Dependency CVE scan — not currently in CI; run manually with an NVD API key for practical speed
mvn org.owasp:dependency-check-maven:check -Dnvd.api.key=$NVD_API_KEY

# 5. Container build check — NOT currently run in CI; do this manually until that gap is closed
docker compose -f deploy/compose/docker-compose.microservices.yml build

# 6. Full-stack smoke test
docker compose -f deploy/compose/docker-compose.microservices.yml up -d
curl -f http://localhost:8091/actuator/health   # facilities
curl -f http://localhost:8092/actuator/health   # safety-security
curl -f http://localhost:8093/actuator/health   # fleet-logistics
curl -f http://localhost:8090/api/v1/system/info # portal
docker compose -f deploy/compose/docker-compose.microservices.yml down -v

# 7. (Recommended, not yet wired into CI) SAST pass
semgrep --config p/java --config p/owasp-top-ten services/
```

## 7. Reviewer-facing summary

**Strengths.** This is a codebase that has clearly been through real, deliberate hardening rather than a one-time cleanup pass — the CI pipeline hard-fails on two specific historical incidents rather than trusting a green checkmark, a genuine security remediation (weak defaults, root containers, CSV injection, rate limiting, provider-agnostic auth) was independently re-verified and held up on every one of 10 claimed fixes, migration hygiene shows the team learning from its one prior migration bug and not repeating it, and the module boundaries are real — zero cross-service coupling, enforced by ArchUnit rather than convention alone. Test quality is above typical for a project this size: no flaky-pattern smells, consistent assertion style, and meaningful architecture-contract tests.

**Remaining risks.** Two findings would draw immediate attention from a careful reviewer: a systemic gap where facilities-service's optimistic-concurrency control is enforced only in application memory, not at the database level, across all 33 of its entities — a silent lost-update race under real concurrent load, in the codebase's largest and most active module. And a fresh regression where the security hardening that just landed inadvertently broke the project's own documented local-dev path, reproducing an incident the team had already fixed once elsewhere. Beyond those two, the remaining gaps are the kind that accumulate in any actively-developed system rather than signs of neglect: one 2,300-line service class the prior refactor missed, CI that doesn't build what it ships (which is exactly how a recent Docker bug slipped through), an unpinned identity-provider image, and onboarding docs that haven't caught up to a fourth service that's been live for a while. None of this suggests the team doesn't know how to fix these things — the evidence throughout is that they do, consistently, once found. The honest read is a codebase in good hands that hasn't yet closed its own feedback loop on two or three specific blind spots.

---

*This report reflects a static, read-only review as of `main` @ `cd03b9c`. Findings marked "Info (positive)" were independently confirmed, not assumed. Findings should be re-verified against the deployed environment before being treated as the final word on production risk.*
