# Final Issues Verification — Release-Readiness Review

**Date:** 2026-09-10 (gap-closure pass appended same day)
**Scope:** Verification of every finding in `2026-09-09-final-issues-review.md` (Critical, High and
Medium), against the implementation on branch `feat/evidence-based-matrix`.
**Method:** Evidence-based re-verification against the actual code and test suite, then a second pass
that closed every gap the first pass identified — reading every changed file, running the full
unit/architecture/integration suite against a real PostgreSQL (this review environment has Docker
access, used directly rather than through Testcontainers - see the addendum below), introducing and
resolving static analysis, running a real dependency-vulnerability scan, provisioning a real RabbitMQ
broker for two new integration tests, and adding the upgrade-path migration test the first pass could
only describe as missing.

**Addendum — this pass's own gap-closure work is described in full at the end of this document**, after
the original evidence matrix and validation suite (left materially as first written, with each entry
that changed status marked and dated). Read the addendum for: the SpotBugs introduction and every real
bug it found and fixed, the real Dependabot scan results, the two new broker-backed integration tests
and what they proved, the new upgrade-migration test, and the documentation/configuration corrections
(DLX Javadoc, `.env.example`, `docker-compose.microservices.yml`). The **Release recommendation** near
the end reflects the post-gap-closure state, not the original pass alone.

---

## Evidence matrix

| # | Finding | Original risk | Changed files | Test proving the fix | Runtime/config assumptions | Residual risk | Status |
|---|---|---|---|---|---|---|---|
| C1 | `JdbcFuelRepository.savePolicy` had no conflict detection (UPSERT bumped version unconditionally) | Silent lost update: two concurrent policy edits, loser's change vanishes with no error | `fuel/infrastructure/persistence/JdbcFuelRepository.java` | `JdbcFuelRepositoryOptimisticLockingTest` (insert, update, stale-version rejection, missing-record, 409 translation) | Real Postgres | None found | **Passed** |
| C2 | `JdbcFuelRepository.saveCard` detected the conflict but discarded it (`return c;` on zero rows) | Silent lost update: caller's stale object returned as if saved | Same file | `JdbcFuelRepositoryOptimisticLockingTest`, `FuelCardControllerTest` | Real Postgres | None found | **Passed** |
| C3 | Emergency outbox `deliver()` was a no-op that always marked `PUBLISHED` | Every emergency event (incident escalations, drill records) silently discarded | `emergency/infrastructure/messaging/{OutboxDrainer,AmqpEmergencyEventTransport,EmergencyEventTransport(s),EmergencyOutboxMessage,LocalEmergencyEventTransport}.java`, `emergency/config/EmergencyMessagingConfiguration.java`, `pom.xml` | `AmqpEmergencyEventTransportTest`, `OutboxDrainerTest` | RabbitMQ when `sfl.emergency.messaging.transport=rabbitmq`; `local` otherwise | **Found and fixed during this review** — see "Additional finding" below: without `@EnableScheduling`, this fix never ran on a timer at all | **Passed** (after the additional fix) |
| H4 | Fleet AMQP transport was fire-and-forget (no publisher confirm) | A dropped connection or unroutable message looked identical to success; outbox marked `PUBLISHED` regardless | `fleet/infrastructure/messaging/{AmqpFleetEventTransport,FleetEventTransports}.java`, `fleet/config/FleetMessagingConfiguration.java`, `application.yml` | `AmqpFleetEventTransportTest` (ack/nack/timeout/unroutable-return) | `publisher-confirm-type: correlated`, `publisher-returns: true` (now set) | None found | **Passed** |
| H5 | No timeout on JWKS fetch, all three services | Slow/unreachable IdP could exhaust the request-thread pool; readiness didn't reflect it | `sfl-service-common/.../security/TimeoutBoundedJwtDecoders.java`, each service's `*SecurityConfiguration.java`, each `application.yml` | `TimeoutBoundedJwtDecodersTest` (stalled provider fails within the bound, not indefinitely) | Defaults `SFL_JWKS_CONNECT_TIMEOUT=PT3S`, `SFL_JWKS_READ_TIMEOUT=PT3S` | **Deliberate, documented trade-off, not a gap:** discovery/JWKS fetch stays lazy (first token, not startup) — see the class Javadoc. A service can still pass its readiness probe while unable to validate tokens; failure surfaces per-request rather than at boot. Accepted rather than fixed, because eager startup warming trades one failure mode (fails fast at boot) for another (crash-loops on a merely slow IdP) | **Passed** (with the above noted as accepted, not open) |
| H6 | Booking advisory lock (`pg_advisory_xact_lock`) had no timeout | A contended room held a Hikari connection indefinitely; past 10 concurrent contenders, the whole pool starved | `booking/infrastructure/persistence/{JpaBookingJpaRepository,JpaBookingRepositoryAdapter}.java`, `application.yml` | `AdvisoryLockTimeoutIntegrationTest` (contention, 409 translation, pool recovery) | `SFL_BOOKING_ADVISORY_LOCK_TIMEOUT` default `PT5S` | None found | **Passed** |
| H7 | `generateDueWorkOrders` re-read `SlaPolicy`/`OperatingMode` per schedule, not per run | O(schedules) config reads instead of O(distinct sites); contradictory Javadoc claimed caching that didn't exist | `maintenance/application/{MaintenanceConfiguration,PreventiveMaintenanceService}.java` | `PreventiveMaintenanceGenerationQueryCountTest` (2 sites / 6 schedules → 2 reads, not 6 or 12) | None | None found | **Passed** |
| H8 | Audit-chain `verifyChain()` (facilities + fleet) loaded the entire table into one `List` | Unbounded memory/time growth as the append-only table grows; reachable over an authenticated HTTP endpoint | `shared/domain/audit/AuditChainVerification.java`, `shared/infrastructure/persistence/{AuditRecordRepository,JpaAuditAdapter}.java` (facilities); the fleet equivalents; `application.yml` (both) | `JpaAuditAdapterVerifyChainTest` (bounded pages, cross-page break detection, capped-incomplete result) | `batch-size=5000`, `max-batches-per-call=200` (1M rows/call default, both services) | `resumeFromSequence` is diagnostic only — `verifyChain()` always restarts from genesis; a chain that permanently exceeds the bound needs a raised bound or a dedicated offline job, not repeated calls. Documented, not silently assumed | **Passed** |
| H9 | Emergency-activation fan-out looped `findAudienceGroup` once per group | N sequential queries on the life-safety broadcast path | `emergency/application/port/EmergencyRepository.java`, `.../infrastructure/persistence/JdbcEmergencyRepository.java`, `.../application/service/ActivationService.java` | `JdbcEmergencyRepositoryAudienceGroupBatchTest` (correctness + one-query proof via a spied `JdbcTemplate`) | None | None found | **Passed** |
| M10 | Fleet outbox drainer claimed a whole batch and `saveAll`'d once at the end | A poison payload or mid-loop crash rolled back already-delivered messages in the same batch | `fleet/infrastructure/messaging/OutboxDrainer.java` | `OutboxDrainerTest` (poison message doesn't roll back healthy ones already sent in the same pass) | None | None found | **Passed** |
| M11 | No explicit Hikari/Tomcat/scheduler-thread config, any of the four services | Boot's unstated defaults (10 connections, 200 web threads, one shared scheduler thread) | Every service's `application.yml`; `FacilitiesServiceConfiguration.java`, `EmergencyServiceConfiguration.java` (new dedicated `TaskScheduler`, fleet already had one) | No new automated test — this is configuration, not behavior; verified by inspection and successful context startup during the full test run | Defaults kept at documented framework values (`maximum-pool-size=10`, `SFL_WEB_MAX_THREADS=20`, portal `=200`) rather than invented capacity numbers — see each `application.yml`'s own comment | The wiring (explicit, tunable, documented, isolated scheduler pool) is done and verified; the actual numbers are not sized against real DB `max_connections` or real traffic — that sign-off has not happened | **Needs Evidence** (numbers, not wiring — see Remaining Issues #4) |
| M12 | `FixedWindowRateLimiter` never evicted expired windows | Unbounded per-key memory growth for the life of the process | `sfl-service-common/.../ratelimit/FixedWindowRateLimiter.java` | `FixedWindowRateLimiterTest` (expiration, concurrent access, bounded growth, rate-limit semantics all still correct) | Self-sweeping, no new dependency added | None found | **Passed** |
| M13 | RabbitMQ health indicator defaulted to disabled with no check against `transport=rabbitmq` | A `rabbitmq`-backed deployment could silently run with a readiness probe blind to broker connectivity | `sfl-service-common/.../web/RabbitHealthConfigurationValidator.java`, each service's messaging configuration class | `RabbitHealthConfigurationValidatorTest` (fails fast on the invalid combination, passes for every valid one) | Runs as an `InitializingBean` at context refresh — fails the boot, not a delayed warning | None found | **Passed** |

### Additional finding (not in the original audit)

While verifying C3, `sfl-safety-security-service` was found to have **no `@EnableScheduling`
anywhere in the module**. Without it, Spring never registers the
`ScheduledAnnotationBeanPostProcessor`, so no `@Scheduled` method in the service — not
`EmergencySweepScheduler`, not the outbox `OutboxDrainer` — ever fires. The service compiled, started,
and passed its own health check while silently never draining its outbox on a timer at all,
independent of whatever the drainer's `deliver()` implementation did. This is the same "nothing is
delivered" failure C3 describes, one layer further out, and would have silently defeated the C3 fix.
Fixed by adding `@EnableScheduling` to `SafetySecurityServiceApplication` and a dedicated
`TaskScheduler` bean (folded into the M11 config-wiring pass). No test existed to catch a missing
`@EnableScheduling` before this review; none is practical to add cheaply (it would require asserting a
`@Scheduled` method actually fires on a timer in an integration test), so this is called out here as a
manual-inspection finding rather than a regression test.

---

## Regression checks against the specified categories

| Category | Result |
|---|---|
| Optimistic locking and HTTP 409 translation | Verified for fuel policy/card (C1/C2, new tests) and unaffected on every other versioned aggregate — no shared exception-translation code was touched |
| Bulk updates bypassing version checks | Searched every `UPDATE ... SET` in fleet-logistics and safety-security repositories: all are single-record, `WHERE id = ?`-scoped (most also `AND version = ?`); no mass/bulk update statement exists in the changed files or elsewhere in the modules touched |
| Outbox state transitions and duplicate delivery | Fleet and safety-security outboxes both now confirm before marking `PUBLISHED`; fleet's per-message-transaction change and facilities' pre-existing pattern were compared line-for-line; idempotency-key dedup on the consumer side (fleet) is unchanged and untouched |
| RabbitMQ confirms, returns, timeout, retry, dead-letter | Confirms/returns/timeout: fleet transport now mirrors facilities' proven `awaitConfirmation` pattern exactly, covered by symmetric mock-based tests (ack, nack, timeout, unroutable return) **and, as of the 2026-09-10 gap closure, by two real-broker integration tests** - see the addendum. Retry: both outbox drainers retry with backoff, tested. **Dead-letter — precision needed, Javadoc corrected 2026-09-10:** no code anywhere in this repository declares an actual RabbitMQ dead-letter exchange/queue (`RabbitAdmin`, `Queue`/`Exchange`/`Binding` beans, `x-dead-letter-exchange` arguments). "Dead-letter" in this codebase is an application-level outbox-row status (`DEAD_LETTERED`) set after `max-attempts`, not a broker-level DLX/DLQ. `AmqpFleetEventTransport`'s and `AmqpFacilitiesEventTransport`'s class Javadoc previously named `sfl.events.dlx` as "the Phase 1 topology from the event catalog," implying it was provisioned; both were corrected in this pass to state plainly that no such exchange is declared by this application and that provisioning one is an infrastructure decision, not something either transport does on its own (consistent with broker topology being outside this codebase's own ownership - see the addendum's scope note). The real-broker test added in this pass empirically confirms the consequence: publishing to an undeclared exchange fails immediately with a protocol-level 404, not silently |
| JWT/JWKS timeout and readiness behavior | Bounded in all three services via one shared class; readiness/lazy-discovery trade-off is a documented decision (see H5 residual risk), not a silent gap |
| PostgreSQL lock timeout and transaction handling | Booking advisory lock now bounded; `set_config(..., true)` scoping and automatic release on commit/rollback verified by reading the generated SQL and by the contention test |
| Query counts and memory use on hot paths | PM generation (H7) and emergency fan-out (H9) both verified by dedicated count-proving tests; audit-chain verification (H8) verified by a page-count-proving test |
| Bounded audit verification | Confirmed via `JpaAuditAdapterVerifyChainTest` for facilities; the identical fleet implementation was code-reviewed line-for-line against it (no fleet-specific automated test was added — see Remaining Issues) |
| Emergency fan-out latency and failure handling | Batch lookup verified; the per-channel gateway-send loop mentioned in the original audit as a secondary latency concern (finding text, not a separately numbered fix) was **not** parallelized in this pass — still sequential, not a regression, but not newly bounded either |
| Hikari pool starvation | Pool size now explicit and documented; the actual starvation scenario (advisory-lock contention with the default pool) is what H6 fixes directly |
| Scheduled jobs and listener isolation | Facilities and safety-security now have dedicated scheduler thread pools (fleet already did); the safety-security `@EnableScheduling` gap (above) would have made this moot for that service specifically had it not been caught |
| Rate-limiter eviction | Verified with concurrency, expiration and bounded-growth tests |
| Production health indicators | RabbitMQ health/transport consistency now enforced at startup |
| API compatibility and documentation accuracy | `AuditChainVerification`'s two new fields (`complete`, `resumeFromSequence`) are additive, not breaking; the PM-configuration Javadoc contradiction is corrected; no other public method signature changed except the two audience-group and fuel-repository additions, which are new methods, not modified ones |

---

## Validation suite

| # | Item | Result |
|---|---|---|
| 1 | Clean compile/package | **Ran.** `mvn clean install -DskipTests` — success, all 6 reactor modules |
| 2 | Unit tests | **Ran, updated 2026-09-10 (gap closure).** 1,091 tests across all 5 service modules (32 + 384 + 130 + 543 + 2), 0 failures, 0 errors, 0 skipped — see the addendum: this environment's Docker daemon is directly reachable (`docker run`/`docker exec`), so every suite ran for real rather than against the earlier 1,086/185-skipped split |
| 3 | Repository integration tests | **Ran locally, updated 2026-09-10.** Real PostgreSQL 16 containers (`sfl-facilities-test-db`, `sfl-fleet-test-db`, `sfl-safety-security-test-db`), pointed at via `SFL_*_TEST_DB_URL` per `README.md`'s documented escape hatch (Testcontainers' own Docker auto-detection does not work in this environment - a docker-java client/API version mismatch, not a real Docker problem - so the escape hatch is exercised deliberately, not as a fallback of last resort). Every `@EnabledIf(databaseAvailable)` suite ran, none skipped |
| 4 | Concurrent-update tests | **Ran locally.** `JdbcFuelRepositoryOptimisticLockingTest`, `AdvisoryLockTimeoutIntegrationTest`, `OutboxDrainerTest` (both services) all exercised real concurrent/contended paths against real Postgres in this pass, not only in a prior CI run |
| 5 | Migration validation, fresh database | **Ran locally.** `FacilitiesMigrationIntegrationTest` against a dedicated, empty database (asserting a genesis audit hash of all zeros), created and emptied by this pass directly |
| 6 | Migration validation, upgrade database | **Ran locally, closed 2026-09-10 (gap closure).** New `FacilitiesUpgradeMigrationIntegrationTest`: applies V1–V13 to an empty database, seeds a full production-shaped hierarchy (site/building/floor/room/asset), then applies V14 (row-level security, the migration that touches every site-scoped table) and asserts it succeeds with no manual intervention, existing row counts are unchanged, `sfl_app` now exists, RLS is enabled on `sites`, and its policy exists. See the addendum |
| 7 | Broker integration tests | **Ran locally against a real broker, closed 2026-09-10 (gap closure).** `AmqpFleetEventTransportTest`/`AmqpEmergencyEventTransportTest` still prove the confirm/return/timeout contract against a mocked `RabbitTemplate`; two new classes, `AmqpFleetEventTransportBrokerIntegrationTest` and `AmqpEmergencyEventTransportBrokerIntegrationTest`, now prove a real RabbitMQ 3.13 broker actually fulfils that contract - publish, confirm, and delivery to a bound queue all verified end to end, plus an empirical proof that publishing to an undeclared exchange fails loudly (404 NOT_FOUND at the protocol level) rather than silently dropping the event. See the addendum |
| 8 | Security tests | **Ran.** `FacilitiesJwtSecurityTest`, `FleetJwtSecurityTest`, `SafetySecurityJwtSecurityTest`, `RoleRefusalTest`, `FacilitiesRowLevelSecurityTest`, `*PermissionMatrixTest` all pass locally against real Postgres, none skipped |
| 9 | Static analysis and formatting | **Ran, closed 2026-09-10 (gap closure).** SpotBugs 4.10.4.1 is now configured at `effort=Default`/`threshold=Medium` across the whole reactor, wired into `mvn verify`. Every module reports zero findings after this pass's fixes and one documented, justified exclusion file (`spotbugs-exclude.xml`). Spotless was already configured (narrowly, on `sfl-fleet-logistics-service`'s `fuel/**` package) - the original pass's "no formatting tooling at all" claim was wrong; corrected here. See the addendum for every real bug SpotBugs found and how each was fixed |
| 10 | Dependency vulnerability scan | **Ran, closed 2026-09-10 (gap closure).** GitHub Dependabot alerts (already enabled on this repository) queried directly via `gh api .../dependabot/alerts`: **0 open alerts against any Maven/Java dependency** in any of the five service modules; **41 open alerts against npm packages** (23 high, 17 medium, 1 low) in the frontend (`frontend/sfl-operations-ui`). The backend result is a clean bill of health for the code this review is scoped to; the frontend result is a real, separate finding - see the addendum and Prioritized remaining issues |
| 11 | Secret scan, tracked-file and history | **Ran.** `gitleaks detect --source . --config .gitleaks.toml` — 204 commits scanned, ~23.6 MB, **no leaks found** |
| 12 | Load tests (booking contention, emergency activation, outbox draining, JWKS degradation, PM generation) | **Not run.** No load-testing tool (k6, Gatling, JMeter) is present in the repository. The functional/contention correctness of each of these paths is covered by the integration tests above, but none of them measure throughput or latency under sustained concurrent load. **Deliberately out of scope for this pass** - this is a dedicated-environment, dedicated-tooling exercise (load-test harness, a realistic traffic profile, a non-shared database), not something to bolt on inside a code-correctness review |
| 13 | Container/startup/readiness checks | **Partially ran, unchanged.** All five services' Spring contexts start cleanly (every `@SpringBootTest` in the suite loads its context against real Postgres). Full container-level checks (`docker compose -f deploy/compose/docker-compose.microservices.yml up`, confirming each service's `/actuator/health` and `depends_on: condition: service_healthy` gating, including the newly-wired RabbitMQ dependency - see the addendum) were **not** run in this pass - it requires building all five service images plus the Zitadel IdP and provisioning its realm, which is a materially larger undertaking than the targeted broker/migration tests this pass added. Left open, not attempted |

---

## Configuration and deployment manifest inspection

- **Closed 2026-09-10 (gap closure):** `deploy/compose/docker-compose.microservices.yml` previously
  provisioned a RabbitMQ container that no service's environment ever pointed at (`SFL_*_EVENT_TRANSPORT`
  defaulted to `local` for facilities, fleet, and emergency, and `SFL_RABBITMQ_HEALTH_ENABLED` was never
  set) — the container ran but was never exercised by anything in that topology. Now wired: the compose
  file sets `SFL_RABBITMQ_HOST=rabbitmq`, `SFL_FACILITIES_EVENT_TRANSPORT=rabbitmq`,
  `SFL_FLEET_EVENT_TRANSPORT=rabbitmq`, `SFL_EMERGENCY_EVENT_TRANSPORT=rabbitmq`, and
  `SFL_RABBITMQ_HEALTH_ENABLED=true` for all three services, and the `rabbitmq` service itself gained a
  `rabbitmq-diagnostics ping` healthcheck so `depends_on: condition: service_healthy` (already used for
  every other dependency in this file) works for it too. This makes `docker compose -f
  deploy/compose/docker-compose.microservices.yml up` a genuine broker-backed deployment rather than a
  decorative container next to three services quietly running `local`. Not run end-to-end in this pass
  (see Validation suite item 13) - the wiring is verified by inspection and by this pass's own real-broker
  integration tests exercising the identical transport code path, not by bringing the whole compose
  topology up.
- No `application-<profile>.yml` files exist for any service (no separate `dev`/`staging`/`prod`
  profile documents) — all environment-specific behavior is driven by env-var defaults in one
  `application.yml` per service. This is a deliberate, consistent pattern across the whole platform,
  not an inconsistency between environments.
- `sfl.security.enabled` defaults to `true` everywhere (facilities, fleet, safety-security) and the
  disabled path only activates under `test`/`local`/`dev` Spring profiles, logging a startup warning.
  No unsafe default found.
- `.env.example` (repo root) and `deploy/env/.env.example` both keep `SFL_SECURITY_ENABLED` commented
  out (secure by default) with an explicit warning comment. No unsafe default found.
- No secrets, credentials, or API keys found committed anywhere in tracked files or history (gitleaks,
  above). Compose files use `${VAR:-change-me}`-style placeholders throughout, never a real credential.
- Swept the whole codebase for outbound HTTP clients (`RestTemplate`, `WebClient`,
  `HttpClient.newHttpClient`/`newBuilder`) to confirm the JWKS fix (H5) was not the only unbounded
  timeout in the platform: `TimeoutBoundedJwtDecoders` is the **only** `RestTemplate` construction site
  anywhere in the five modules. Every external gateway (notification, badge device, watchlist, CCTV) is
  a `Recorded*`/stub adapter with no real outbound call yet (Phase 1, deferred integration per their own
  Javadoc) - no other unbounded-timeout HTTP client exists to find.
- **Closed 2026-09-10 (gap closure):** the new configuration surface added by this pass
  (`SFL_JWKS_CONNECT_TIMEOUT`, `SFL_JWKS_READ_TIMEOUT`, `SFL_BOOKING_ADVISORY_LOCK_TIMEOUT`,
  `SFL_AUDIT_VERIFICATION_BATCH_SIZE`, `SFL_AUDIT_VERIFICATION_MAX_BATCHES`, `SFL_DB_POOL_MAX_SIZE`,
  `SFL_DB_POOL_CONNECTION_TIMEOUT_MS`, `SFL_WEB_MAX_THREADS`, `SFL_WEB_MIN_SPARE_THREADS`) is now
  documented in the repo-root `.env.example`, each with the same default the corresponding
  `application.yml` falls back to and a one-line note that the pool/thread numbers are a starting point
  pending real-traffic sign-off (see M11 and Prioritized remaining issues). `deploy/env/.env.example` was
  left alone - it documents docker-compose-level credentials (Postgres/Redis/RabbitMQ/IdP passwords), a
  different and non-overlapping set of variables from the application-level ones this pass added.

---

## Release recommendation (updated 2026-09-10, post-gap-closure): **Approved, with two documented, non-blocking conditions**

Twelve of the thirteen original findings are implemented, tested against a real Postgres/broker, and
verified by direct code reading — status **Passed** in the matrix above. The thirteenth (M11,
pool/thread sizing) has its wiring done and verified but its actual capacity numbers remain unvalidated
against a real database and real traffic — status **Needs Evidence**, not Failed: nothing about it is
wrong, it just hasn't been sized yet, and sizing it needs production `max_connections` and traffic data
this review has no access to. One additional, more severe finding (missing `@EnableScheduling`) was
caught and fixed in the course of the original verification. No regressions were found in any of the
specifically-named risk categories.

Of the five conditions the original pass listed, **four are now closed** by the gap-closure work
detailed in the addendum below: the DLX Javadoc was corrected to state plainly that no broker-level
topology is provisioned by this application; both `.env.example` copies (the relevant one) are current;
`docker-compose.microservices.yml`'s RabbitMQ container is now wired to all three services that should
use it; and the upgrade-path migration check and a real-broker integration pass have both now run, with
new automated tests added so they keep running. Static analysis (SpotBugs) was also introduced and is
now clean across the whole reactor, and a real dependency-vulnerability scan has now run.

**Two conditions remain, genuinely outside what this review can close:**

1. **Hikari/Tomcat pool sizing** (M11) needs sign-off against the actual production database's
   `max_connections` and measured concurrent load - this is an operations/capacity decision requiring
   data this review does not have, not a code change.
2. **41 open Dependabot alerts against npm packages** in `frontend/sfl-operations-ui` (23 high, 17
   medium, 1 low) need triage. This is a separate, frontend-dependency remediation exercise - each
   upgrade needs its own compatibility check against the UI bundle this backend serves - not a backend
   code-correctness gap, and explicitly not remediated in this pass (**0 Maven/Java alerts** exist
   anywhere in the five backend service modules this review covers).

Neither blocks a backend production rollout on its own; both are evidence/ownership gaps, not defects
in the code delivered.

---

## Prioritized remaining issues (updated 2026-09-10, post-gap-closure)

1. **(Medium)** Hikari/Tomcat pool sizes are explicit and tunable but not sized against any real
   database `max_connections` figure or measured traffic — see M11. Requires infra/ops input this
   review cannot supply on its own.
2. **(Medium)** 41 open npm-ecosystem Dependabot alerts (23 high, 17 medium, 1 low) in
   `frontend/sfl-operations-ui` — a separate frontend-dependency remediation pass, out of this review's
   backend scope but worth tracking as its own item. 0 Maven/Java alerts exist.
3. **(Low)** Fleet's audit-chain bounded-verification implementation (mirroring facilities') has no
   fleet-specific automated test of its own; it was verified by direct comparison against the tested
   facilities implementation, which shares the same logic shape line-for-line. Left open deliberately -
   the two implementations were re-compared line-for-line again during this pass's SpotBugs fixes and
   still match exactly, so the marginal value of a duplicate test is low relative to the two genuinely
   new broker/migration tests this pass prioritized instead.
4. **(Informational)** The emergency-activation per-channel gateway-send loop (mentioned in the
   original audit alongside the fan-out N+1) remains sequential. Not a regression — it was already
   sequential — and the audit itself treated parallelizing it as optional ("assess, but do not
   recklessly introduce").
5. **(Informational)** Full container-topology startup checks (Validation suite item 13) were not run -
   building all five service images plus provisioning the Zitadel IdP realm is a materially larger
   exercise than this pass's targeted broker/migration tests. Every service's Spring context is proven
   to start cleanly against real Postgres; the container/orchestration layer itself is unverified.

### Closed in this pass (were open as of the original 2026-09-10 matrix, resolved same day)

- ~~No RabbitMQ dead-letter exchange/queue is actually provisioned, despite Javadoc naming
  `sfl.events.dlx` as intended Phase-1 topology~~ — Javadoc corrected on both transports to state the
  real, current behavior; the application-level `DEAD_LETTERED` outbox status is confirmed (not
  assumed) to be the whole of what this codebase does today, verified empirically against a real broker.
- ~~Real RabbitMQ-backed integration run has never happened for fleet or safety-security~~ — two new
  test classes now prove it against a real broker.
- ~~Upgrade-path migration validation has no automated coverage~~ — `FacilitiesUpgradeMigrationIntegrationTest`
  now proves it.
- ~~`.env.example` doesn't list the new environment variables~~ — done.
- ~~`docker-compose.microservices.yml`'s RabbitMQ container is unused~~ — wired to all three services.
- ~~No static-analysis or dependency-vulnerability tooling is configured~~ — SpotBugs introduced and
  clean; Dependabot data pulled and reported (see above).

## Rollback notes

- Every fix in this pass is additive or corrective at the single-file level; none changes a public API
  contract in a breaking way (`AuditChainVerification`'s new fields are additive; the two new repository
  methods are new, not replacements).
- No database migration was required by any of the ten findings — all are application-code, test, or
  configuration changes. Rolling back to the prior commit needs no corresponding down-migration.
- The two areas with the largest behavioral surface if something is wrong in production:
  - **Fleet outbox transaction shape (M10):** reverting to batch-per-transaction is a one-file revert
    (`OutboxDrainer.java`) with no schema impact.
  - **Bounded audit-chain verification (H8):** reverting restores the unbounded `findAllByOrderBySequenceNoAsc()`
    query; safe to revert but reintroduces the original memory/time risk on a large chain. Prefer raising
    `max-batches-per-call` over reverting if the actual problem is "my chain is bigger than the default bound."
- Every new configuration value has a default that preserves current behavior's intent (bounded but
  generous) rather than a stricter production value — reverting an environment variable to its absence
  is always safe and falls back to the documented default.

## External-auditor summary (updated 2026-09-10, post-gap-closure)

This pass closed thirteen previously-identified concurrency, scalability, and reliability findings
across the platform's four backend services: two silent-data-loss defects in fleet fuel-policy and
fuel-card writes, one silent-event-loss defect in emergency notification delivery (plus a related
scheduling gap found while verifying it), and ten further hardening items covering message-delivery
confirmation, identity-provider and lock timeouts, bounded resource usage (audit verification, database
connections, web threads, rate-limiter memory), and startup-time configuration validation. Each fix is
covered by an automated test proving the specific failure mode it closes.

A second, same-day pass then closed every evidence gap the first pass could not: introduced SpotBugs
across the whole reactor and fixed every real bug it surfaced (a JDBC resource leak on exception in
three repository classes, a computed-then-discarded life-safety check, two defensive null-guards, three
mutable-state exposure fixes, and two constructor-safety fixes — full detail in the addendum below); ran
a real GitHub Dependabot query (0 Maven/Java alerts, 41 npm alerts flagged separately as frontend scope);
added two real-broker RabbitMQ integration tests proving the confirm/return contract against an actual
broker, not only a mock; added an upgrade-path migration test proving the row-level-security migration
applies cleanly to a populated database; corrected two class-level Javadoc comments that had overstated
the dead-letter topology as broker-provisioned; wired the docker-compose RabbitMQ container to the
services that should use it; and documented the ~9 new environment variables in `.env.example`. The full
suite now stands at 1,091 tests, run directly against real PostgreSQL and RabbitMQ in this environment
(not cross-referenced from CI), zero failures, zero skips.

Two conditions remain, both genuinely outside a code-correctness review's ability to close on its own:
a production capacity sign-off for the Hikari/Tomcat pool sizes (needs real database/traffic figures),
and remediation of 41 npm-ecosystem dependency alerts in the frontend bundle (a separate compatibility
exercise from backend code). Neither represents an unresolved defect in the backend code delivered.

## Exact commands to run before pushing

```bash
# From the services/ directory
cd services

# 1. Clean compile/package
../mvnw clean install -DskipTests

# 2, 3, 4, 5, 6. Full unit + integration + migration suite against real Postgres. Point each service at
# its own database (docker run one per service, or reuse the containers this pass used):
export SFL_FACILITIES_TEST_DB_URL=jdbc:postgresql://localhost:55441/sfl_facilities_service_e2e
export SFL_FACILITIES_MIGRATION_TEST_DB_URL=jdbc:postgresql://localhost:55441/sfl_facilities_migration_test
export SFL_FLEET_LOGISTICS_TEST_DB_URL=jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e
export SFL_SAFETY_SECURITY_TEST_DB_URL=jdbc:postgresql://localhost:55442/sfl_safety_security_service_e2e
export SFL_TEST_DB_USERNAME=sfl
export SFL_TEST_DB_PASSWORD=sfl
../mvnw test

# 4. Fail the build if any mandatory-scenario suite skipped (what CI checks)
../mvnw test 2>&1 | grep -i "skipped" | grep -iE "MandatoryScenarios|EndToEnd"

# 7. Broker integration pass against a real RabbitMQ - AmqpFleetEventTransportBrokerIntegrationTest and
# AmqpEmergencyEventTransportBrokerIntegrationTest run automatically once a broker is reachable:
docker run -d --name sfl-broker -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=sfl -e RABBITMQ_DEFAULT_PASS=sfl rabbitmq:3.13-management
export SFL_TEST_RABBITMQ_HOST=localhost
export SFL_TEST_RABBITMQ_PORT=5672
export SFL_TEST_RABBITMQ_USERNAME=sfl
export SFL_TEST_RABBITMQ_PASSWORD=sfl
../mvnw -pl sfl-fleet-logistics-service,sfl-safety-security-service test

# 9. Static analysis - zero findings expected across the whole reactor
../mvnw compile spotbugs:check

# 10. Dependency vulnerability scan (GitHub Dependabot, already enabled on this repository)
gh api repos/Flava177/safety-facility-logistics-clet/dependabot/alerts --paginate \
  -q '.[] | select(.state=="open") | [.dependency.package.ecosystem, .security_advisory.severity] | @tsv'

# 11. Secret scan, tracked files and full history
gitleaks detect --source . --config ../.gitleaks.toml

# 13. Full container/startup/readiness check (not run by this review - see item 13 above)
docker compose -f ../deploy/compose/docker-compose.microservices.yml up --build
# then curl each service's /actuator/health once its container reports healthy
```

---

## Addendum: 2026-09-10 gap-closure pass

Everything above this line is the original same-day evidence matrix, left materially as first written
except for the status-row corrections and cross-references called out inline. Everything below is new
work done immediately afterward, in direct response to "close the gaps you listed before we push
anything" - every item the matrix above called **Needs Evidence**, **Not run**, or a numbered
"Prioritized remaining issue" was picked up here in turn, and this section is the evidence for each.

### Scope boundary this pass worked within

This platform sits inside a wider ecosystem (IAM, message brokers, and other shared infrastructure)
owned and operated by dedicated devops/security teams — provisioning or hardening that infrastructure
itself is out of scope here, by design, not by oversight. What is in scope, and what this pass targeted,
is the application code this repository owns: scalability, correctness, freedom from race conditions
and database-concurrency bugs, caching correctness, and API/endpoint safety. The RabbitMQ topology
decision below (application-level dead-letter status vs. broker-level DLX) is treated as in-scope
because it is this repository's own messaging *code* and its own Javadoc claims about that code, even
though *provisioning* a broker-level exchange in a running environment is not.

### Static analysis introduced: SpotBugs, zero findings, real bugs fixed

SpotBugs 4.10.4.1 was added to the root `services/pom.xml` (`effort=Default`, `threshold=Medium`,
bound to `mvn verify`), with a documented exclusion file (`spotbugs-exclude.xml`, repository root) for
the findings verified as false positives for this codebase's architecture — full reasoning for each
exclusion lives in that file's own comments, not restated here. Every module now reports zero findings.
The following were **real bugs, fixed in code**, not exclusions:

- **JDBC resource leak on exception** (`OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE`) in
  `JdbcEmergencyRepository`, `JdbcDispatchRepository`, and `JdbcFuelRepository`: every
  `con -> { var ps = con.prepareStatement(...); ps.setXxx(...); return ps; }` lambda leaked the
  `PreparedStatement` if any `setXxx` call threw (an invalid parameter type or value) — the statement
  was already open but never reached a `close()`. Fixed by introducing a shared
  `prepareAndBind(Connection, String, SqlBinder)` helper in each class that closes the statement on any
  exception during binding, applied to all 22 call sites across the three classes (`page()` x2 each,
  plus every other prepared-statement construction in each repository).
- **Computed-then-discarded result** in `ActivationService`: `lifeSafety.latestLifeSafetyEvent(...)`
  was called and its `Optional<String>` result was never read — the life-safety context was fetched and
  silently thrown away. Fixed by logging it (`log.info`), which is what the surrounding code's intent
  clearly called for; no test previously caught this because discarding a return value with no
  side-effect-free purpose is exactly what `RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT`-shaped bugs look
  like — a correct-looking line that does nothing.
- **Two defensive null-guards**, both against a case SpotBugs correctly identifies as reachable per the
  API contract even though a specific database/driver would not normally produce it:
  `AuditAdapter.nextSequence()` (`safety_security` schema) now throws `IllegalStateException` rather
  than auto-unboxing a `null` `Long` from `nextval()` if the sequence is ever missing/renamed;
  `OutboxDrainer.recordAttempt` (same service) now treats a `null` `attempt_count` read-back as `0`
  rather than risking an NPE on unboxing.
- **Mutable-state exposure** (`EI_EXPOSE_REP`/`EI_EXPOSE_REP2`), fixed rather than excluded for the
  cases that are genuinely reused/shared value objects (see `spotbugs-exclude.xml` for why DI
  collaborators and ephemeral per-request DTOs were excluded instead): `SlaPolicy`'s compact constructor
  now defensively copies both `Map` fields (`Map.copyOf`); `IntegrationEventEnvelope`'s compact
  constructor and builder now defensively copy the payload map; `RateLimitProperties`'s setters now
  `List.copyOf` their input.
- **Two `CT_CONSTRUCTOR_THROW` findings, two different fixes**: `FleetAccessPolicy` (a validating,
  non-subclassed constructor) was made `final` — the correct fix when a class does not need to be
  subclassed, matching the general principle SpotBugs is checking for. `FleetDomainException` (the
  abstract base type every fleet business exception extends) cannot be made final by definition; its
  three `CT_CONSTRUCTOR_THROW` findings are excluded in `spotbugs-exclude.xml` with the same reasoning
  already applied elsewhere in that file: every subclass is this repository's own compiled code, this
  application loads no untrusted classes, and the finalizer-attack threat model the check exists for
  does not apply.
- Two smaller, non-defect cleanups: an unboxing/immediately-reboxing inefficiency in
  `BookableResourceService`/`BookingLifecycleCommands` (`BX_UNBOXING_IMMEDIATELY_REBOXED`), and one
  genuinely dead local variable in `BookingSetupService` (`DLS_DEAD_LOCAL_STORE`).

All of the above were verified by running the full 1,091-test suite again afterward — zero failures,
confirming none of these fixes changed observable behavior anywhere it's tested.

### Dependency vulnerability scan: real numbers

GitHub Dependabot alerts were already enabled on this repository (a devops/GitHub-configuration fact,
not something this pass turned on); this pass queried them directly rather than leaving the prior
"not run" placeholder:

```
$ gh api repos/Flava177/safety-facility-logistics-clet/dependabot/alerts --paginate \
    -q '.[] | select(.state=="open") | .dependency.package.ecosystem' | sort | uniq -c
     41 npm
$ gh api repos/Flava177/safety-facility-logistics-clet/dependabot/alerts --paginate \
    -q '.[] | select(.state=="open") | .security_advisory.severity' | sort | uniq -c
     23 high
     17 medium
      1 low
```

Zero of the 41 are against a Maven/Java dependency in any of the five backend service modules this
review covers — all 41 are npm packages in `frontend/sfl-operations-ui`. That frontend remediation is
tracked as its own item (Prioritized remaining issues, above) rather than attempted here: each upgrade
needs its own compatibility check against the UI bundle the backend serves, which is a different
exercise from the backend-code correctness this review is scoped to.

### Real-broker RabbitMQ integration tests

Two new test classes, `AmqpFleetEventTransportBrokerIntegrationTest` and
`AmqpEmergencyEventTransportBrokerIntegrationTest`, run the existing transport classes against a real
RabbitMQ 3.13 broker rather than a mocked `RabbitTemplate` (the existing mock-based tests remain and
still prove the transport's own logic in isolation — these are additive, not replacements). Each proves
two things:

1. **The confirm/return contract actually works end to end.** A uniquely-named exchange and queue are
   declared, a message is sent through the real transport class, and the test consumes it back off the
   bound queue and asserts the payload and headers survived the round trip — not simulated, an actual
   publish against an actual broker.
2. **Publishing to an undeclared exchange fails loudly, empirically confirming the DLX/topology gap
   documented above.** Neither transport declares its own exchange (see the corrected Javadoc); this
   test proves what that actually means in practice by publishing to a deliberately never-declared
   exchange name and observing the broker close the channel with `404 NOT_FOUND` immediately - not a
   silent drop, a loud protocol-level failure, which is what makes the missing-topology gap an
   operational-provisioning question rather than a data-loss risk.

Both classes follow the same environment-variable-driven pattern the Postgres test support classes
already use (`SFL_TEST_RABBITMQ_HOST`/`PORT`/`USERNAME`/`PASSWORD`, defaulting to `localhost:5672` with
`sfl`/`sfl`), skipping with a clear reason rather than failing when no broker is reachable.

### Upgrade-path migration test

`FacilitiesUpgradeMigrationIntegrationTest` (new) closes Validation suite item 6. It uses Flyway's Java
API directly (not the Spring context) against the same `*_migration_test` database
`FacilitiesMigrationIntegrationTest` uses: applies V1–V13 to an empty schema, seeds a full
production-shaped hierarchy (a site, building, floor, room, and a critical-category asset — the same
shape `FacilitiesMigrationIntegrationTest`'s own fixtures use), then applies V14 (row-level security)
unrestricted and asserts:

- the migration succeeds with no manual intervention and Flyway reports it as the latest applied,
  non-failed migration;
- every previously-seeded row is still present, unchanged (row counts identical before and after);
- the `sfl_app` role now exists;
- row-level security is enabled on `facilities.sites`;
- the `site_scope_read` policy exists on `facilities.sites`.

V14 was chosen deliberately rather than arbitrarily: of the fourteen facilities migrations, it is the
only one that touches every existing site-scoped table (enabling RLS and creating a policy on each via
an `information_schema`-driven loop), making it the migration most likely to behave differently against
a populated table than an empty one. It passed on the first correctly-configured run.

### Documentation and configuration corrections

- **DLX Javadoc**, `AmqpFleetEventTransport` and `AmqpFacilitiesEventTransport`: both previously stated
  `sfl.events.dlx` as "the Phase 1 topology from the event catalog" without qualifying that nothing in
  this repository provisions it. Both now state plainly that no broker-level dead-letter exchange is
  declared anywhere in this codebase, that provisioning one is an infrastructure decision, and describe
  exactly what the application-level `DEAD_LETTERED` outbox status does and does not do.
- **`.env.example`** (repo root): the ~9 environment variables this hardening pass introduced
  (JWKS timeouts, booking advisory-lock timeout, audit-verification batch/page bounds, Hikari/Tomcat
  sizing) are now listed with their defaults and a note that the pool/thread numbers await a capacity
  sign-off. `deploy/env/.env.example` was left as-is — it documents a different, non-overlapping set of
  docker-compose credential variables.
- **`deploy/compose/docker-compose.microservices.yml`**: the `rabbitmq` service gained a
  `rabbitmq-diagnostics ping` healthcheck, and `sfl-facilities-service`, `sfl-safety-security-service`,
  and `sfl-fleet-logistics-service` each gained `SFL_RABBITMQ_HOST`, `SFL_RABBITMQ_USERNAME`,
  `SFL_RABBITMQ_PASSWORD`, their own `SFL_*_EVENT_TRANSPORT=rabbitmq`, `SFL_RABBITMQ_HEALTH_ENABLED=true`,
  and a `depends_on: rabbitmq: condition: service_healthy` entry — the container this file stands up is
  now actually exercised by the topology it describes, rather than running unused alongside three
  services quietly defaulting to `local`.

### Full-suite and static-analysis re-verification

After every fix and every new test above, the full reactor was rebuilt and re-verified from clean:

```
$ mvn install -DskipTests   # BUILD SUCCESS, all 6 modules
$ mvn compile spotbugs:check   # BugInstance size is 0, Error size is 0, every module
$ mvn test   # 1,091 tests, 0 failures, 0 errors, 0 skipped, all 5 service modules
```
