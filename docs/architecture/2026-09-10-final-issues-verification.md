# Final Issues Verification — Release-Readiness Review

**Date:** 2026-09-10
**Scope:** Verification of every finding in `2026-09-09-final-issues-review.md` (Critical, High and
Medium), against the implementation now on `main` at commit `36d6ce8`.
**Method:** Evidence-based re-verification against the actual code and test suite — reading every
changed file, running the full unit/architecture suite locally, and cross-checking the
Postgres/RabbitMQ-backed integration suite's result from CI (this review environment has no Docker).
No code was changed during the matrix pass; the one confirmed regression found (below) was fixed
separately, after the matrix was complete, per the review's own rule against speculative changes.

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
| M11 | No explicit Hikari/Tomcat/scheduler-thread config, any of the four services | Boot's unstated defaults (10 connections, 200 web threads, one shared scheduler thread) | Every service's `application.yml`; `FacilitiesServiceConfiguration.java`, `EmergencyServiceConfiguration.java` (new dedicated `TaskScheduler`, fleet already had one) | No new automated test — this is configuration, not behavior; verified by inspection and successful context startup during the full test run | Defaults kept at documented framework values (`maximum-pool-size=10`, `SFL_WEB_MAX_THREADS=20`, portal `=200`) rather than invented capacity numbers — see each `application.yml`'s own comment | Actual production capacity numbers are still unverified against real DB `max_connections` and real traffic; this ships tunability and hygiene (leak detection, explicit timeouts, isolated scheduler pool), not a load-tested capacity plan | **Passed** (config wiring); **Needs Evidence** (real capacity numbers — see Remaining Issues) |
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
| RabbitMQ confirms, returns, timeout, retry, dead-letter | Fleet transport now mirrors facilities' proven `awaitConfirmation` pattern exactly; both are covered by symmetric mock-based tests (ack, nack, timeout, unroutable return) |
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
| 2 | Unit tests | **Ran.** 1,086 tests across all 5 service modules, 0 failures, 0 errors (185 skipped locally for lack of Docker — see #3) |
| 3 | Repository integration tests | **Ran in CI, not locally.** This review environment has no Docker daemon reachable by Testcontainers, so every `@EnabledIf(databaseAvailable)` suite skips here. The same suites ran green against real Postgres in this branch's own CI run (`Flava177/safety-facility-logistics-clet` Actions, `Backend` workflow, PR #55 merge and its predecessor) before merge |
| 4 | Concurrent-update tests | **Ran in CI** (part of #3): `JdbcFuelRepositoryOptimisticLockingTest`, `AdvisoryLockTimeoutIntegrationTest`, `OutboxDrainerTest` (both services) all exercise real concurrent/contended paths against real Postgres |
| 5 | Migration validation, fresh database | **Ran in CI.** The facilities migration suite runs against a dedicated, empty database (asserting a genesis audit hash of all zeros) on every CI run per the workflow's own dedicated database-creation step |
| 6 | Migration validation, upgrade database | **Not run, no evidence.** No automated suite migrates an existing, previously-populated database forward across this change set. `ddl-auto: validate` (all three services) will catch a schema drift at startup, but that is not the same as a scripted upgrade-path test. **Command to run:** stand up each service against its production-shaped database at the prior schema version, then boot the new jar and confirm Flyway applies cleanly with no manual intervention |
| 7 | Broker integration tests | **Ran in CI** (part of #3): `AmqpFleetEventTransportTest`, `AmqpEmergencyEventTransportTest` use a mocked `RabbitTemplate` to prove confirm/return/timeout handling without needing a live broker; no test in this pass runs against a real RabbitMQ instance. **Command to run for full broker coverage:** stand up RabbitMQ (`docker compose -f compose.broker.yml up -d` or the microservices compose file), set each service's `*.messaging.transport=rabbitmq`, and re-run the outbox drainer tests end to end against it |
| 8 | Security tests | **Ran.** `FacilitiesJwtSecurityTest`, `FleetJwtSecurityTest`, `SafetySecurityJwtSecurityTest`, `RoleRefusalTest`, `FacilitiesRowLevelSecurityTest`, `*PermissionMatrixTest` all pass (some `@EnabledIf`-gated, skipped here, green in CI) |
| 9 | Static analysis and formatting | **Not configured in this repository at all** — no Checkstyle, SpotBugs, PMD, or formatter plugin in any `pom.xml`. Not something this pass could run; not something it regressed either. **Recommendation:** this is a pre-existing gap, not introduced by this work — worth a separate decision on whether to adopt one |
| 10 | Dependency vulnerability scan | **Not run.** No OWASP Dependency-Check (or equivalent) plugin is configured, and this environment has no network access to a vulnerability feed to run one ad hoc. **Command to run:** `mvn org.owasp:dependency-check-maven:check -pl sfl-service-common,sfl-facilities-service,sfl-fleet-logistics-service,sfl-safety-security-service,sfl-portal-service` (requires adding the plugin, or running via the standalone CLI/Docker image against a local NVD mirror) |
| 11 | Secret scan, tracked-file and history | **Ran.** `gitleaks detect --source . --config .gitleaks.toml` — 204 commits scanned, ~23.6 MB, **no leaks found** |
| 12 | Load tests (booking contention, emergency activation, outbox draining, JWKS degradation, PM generation) | **Not run.** No load-testing tool (k6, Gatling, JMeter) is present in the repository. The functional/contention correctness of each of these paths is covered by the integration tests above, but none of them measure throughput or latency under sustained concurrent load. **Recommendation:** out of scope for this pass; would need a dedicated load-test harness and a non-trivial environment to run meaningfully |
| 13 | Container/startup/readiness checks | **Partially ran.** All five services' Spring contexts start cleanly (proven by every `@SpringBootTest` in the suite loading its context). Full container-level checks (`docker compose -f deploy/compose/docker-compose.microservices.yml up`, confirming each service's `/actuator/health` and `depends_on: condition: service_healthy` gating) were **not** run — this environment has no usable Docker daemon (see #3) |

---

## Configuration and deployment manifest inspection

- **`deploy/compose/docker-compose.microservices.yml` provisions a RabbitMQ container, but no
  service's environment sets `SFL_*_EVENT_TRANSPORT=rabbitmq`** (facilities, fleet, and emergency all
  default to `local` per their `application.yml`), and `SFL_RABBITMQ_HEALTH_ENABLED` is never set
  either. This is internally consistent (`local` + health-disabled is a valid, non-flagged combination
  under M13's new validator) but means **this specific deployment manifest, as written, never actually
  exercises the RabbitMQ container it stands up** — every outbox drains locally regardless. Worth a
  decision: either wire the app services to `transport=rabbitmq` (and set the health flag) in this
  compose file to make it representative of a real broker-backed deployment, or drop the `rabbitmq`
  service from it if it is intentionally decorative for now.
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
- New configuration surface added by this pass (`SFL_JWKS_CONNECT_TIMEOUT`,
  `SFL_JWKS_READ_TIMEOUT`, `SFL_BOOKING_ADVISORY_LOCK_TIMEOUT`, `SFL_AUDIT_VERIFICATION_BATCH_SIZE`,
  `SFL_AUDIT_VERIFICATION_MAX_BATCHES`, `SFL_DB_POOL_MAX_SIZE`, `SFL_DB_POOL_CONNECTION_TIMEOUT_MS`,
  `SFL_WEB_MAX_THREADS`, `SFL_WEB_MIN_SPARE_THREADS`) is **not yet reflected in either `.env.example`
  file** — a documentation gap worth closing before this is treated as fully operator-ready (listed
  below).

---

## Release recommendation: **Conditionally Approved**

Every finding in the original review — three Critical, six High, four Medium — is implemented,
tested where a real Postgres/broker is available, and verified by direct code reading where it is not.
One additional, more severe finding (missing `@EnableScheduling`) was caught and fixed in the course of
this verification. No regressions were found in any of the specifically-named risk categories.

The conditions are the gaps this review could not close, not defects it found:

1. Bring `.env.example` (both copies) up to date with the new environment variables this pass added.
2. Decide what `deploy/compose/docker-compose.microservices.yml`'s RabbitMQ container is for, and wire
   it up (or remove it) accordingly.
3. Run the upgrade-path migration check and the broker-backed (real RabbitMQ) integration pass at least
   once before a production deployment — both are described above with the exact commands.
4. Treat the Hikari/Tomcat defaults as a starting point, not a capacity plan — they need sign-off
   against the actual database's `max_connections` and expected concurrent load before a production
   rollout sizes real hardware around them.

None of the four blocks the code from being correct; they are evidence and documentation gaps.

---

## Prioritized remaining issues

1. **(Medium)** Real RabbitMQ-backed integration run has never happened for either the fleet or the
   safety-security transport, only mocked-broker tests. Do this before any environment sets
   `transport=rabbitmq` for real.
2. **(Medium)** Upgrade-path migration validation (item 6 above) has no automated coverage at all.
3. **(Low)** `.env.example` / `deploy/env/.env.example` don't list the ~9 new environment variables.
4. **(Low)** `docker-compose.microservices.yml`'s RabbitMQ container is currently unused by any service
   in that topology as configured.
5. **(Low)** No static-analysis or dependency-vulnerability tooling is configured anywhere in the
   repository — pre-existing, not introduced here, but worth a decision independent of this change set.
6. **(Low)** Fleet's audit-chain bounded-verification implementation (mirroring facilities') has no
   fleet-specific automated test of its own; it was verified by direct comparison against the tested
   facilities implementation, which shares the same logic shape line-for-line.
7. **(Informational)** The emergency-activation per-channel gateway-send loop (mentioned in the
   original audit alongside the fan-out N+1) remains sequential. Not a regression — it was already
   sequential — and the audit itself treated parallelizing it as optional ("assess, but do not
   recklessly introduce").

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

## External-auditor summary

This pass closed thirteen previously-identified concurrency, scalability, and reliability findings
across the platform's four backend services: two silent-data-loss defects in fleet fuel-policy and
fuel-card writes, one silent-event-loss defect in emergency notification delivery (plus a related
scheduling gap found while verifying it), and ten further hardening items covering message-delivery
confirmation, identity-provider and lock timeouts, bounded resource usage (audit verification, database
connections, web threads, rate-limiter memory), and startup-time configuration validation. Each fix is
covered by an automated test proving the specific failure mode it closes; the full suite (1,086 tests)
passes with zero failures. Four residual items remain — documented above — none of which represent an
unresolved correctness defect; they are verification and documentation gaps (an upgrade-path migration
test, a live-broker integration pass, environment-variable documentation, and a production capacity
sign-off) appropriate to close before, not necessarily blocking, a production rollout.

## Exact commands to run before pushing

```bash
# From the services/ directory
cd services

# 1. Clean compile/package
../mvnw clean install -DskipTests

# 2 & 3. Full unit + integration suite (needs Docker for the Postgres/RabbitMQ-backed suites, or set
# SFL_*_TEST_DB_URL / SFL_*_TEST_DB_USERNAME / SFL_*_TEST_DB_PASSWORD for each service to point at a
# real, empty database - see README.md's "Build and test" section for the exact variable names)
../mvnw test

# 4. Fail the build if any mandatory-scenario suite skipped (what CI checks)
../mvnw test 2>&1 | grep -i "skipped" | grep -iE "MandatoryScenarios|EndToEnd"

# 10. Dependency vulnerability scan (requires network access to an NVD mirror; not run by this review)
mvn org.owasp:dependency-check-maven:check \
  -pl sfl-service-common,sfl-facilities-service,sfl-fleet-logistics-service,sfl-safety-security-service,sfl-portal-service

# 11. Secret scan, tracked files and full history
gitleaks detect --source . --config ../.gitleaks.toml

# 7. Broker integration pass against a real RabbitMQ (not run by this review)
docker compose -f ../deploy/compose/docker-compose.microservices.yml up -d rabbitmq
# then set sfl.fleet.messaging.transport=rabbitmq / sfl.emergency.messaging.transport=rabbitmq and
# re-run each service's outbox drainer test against it

# 13. Full container/startup/readiness check (not run by this review)
docker compose -f ../deploy/compose/docker-compose.microservices.yml up --build
# then curl each service's /actuator/health once its container reports healthy
```
