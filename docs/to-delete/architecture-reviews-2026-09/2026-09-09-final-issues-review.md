# Final Issues Review — Concurrency, Scalability, Integration Reliability, Performance Under Load

**Date:** 2026-09-09
**Scope:** All 5 backend modules — `sfl-facilities-service`, `sfl-fleet-logistics-service`,
`sfl-safety-security-service`, `sfl-portal-service`, `sfl-service-common`.
**Method:** Four independent, parallel, read-only investigations, each scoped to one lens:
(1) concurrency/correctness bugs, (2) scalability, (3) cross-service integration and messaging
reliability, (4) performance under sustained load. No code was changed as part of this review.
**Companion document:** builds on `docs/architecture/2026-09-09-external-readiness-review.md`
(architecture/API/docs/ops readiness) — findings already covered there are not repeated here unless
this pass adds materially more depth or evidence.

## Summary

| Severity | Count |
|---|---|
| Critical | 3 |
| High | 6 |
| Medium | 4 |
| Low / informational | 2 |

Three findings involve **real, currently-shipping silent data or write loss** and should be treated as
the priority: two in fleet-logistics' fuel-policy/fuel-card persistence, one in safety-security's
emergency-notification outbox.

---

## Critical

### 1. `sfl-fleet-logistics-service` — `JdbcFuelRepository.savePolicy` has zero conflict detection

**File:** `sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fuel/infrastructure/persistence/JdbcFuelRepository.java:246-297`

Uses `INSERT ... ON CONFLICT (id) DO UPDATE SET ..., version = fleet_logistics.fuel_policies.version + 1`
with **no `version = ?` predicate** in the conflict clause — unlike every sibling save method in the same
file. Real update paths exist for this (`FuelApplicationService.java:429` revise, `:494` withdraw).

**Failure scenario:** two concurrent edits to the same `FuelPolicy` — pure last-write-wins. No exception,
no 409, no signal to either caller that their edit was overwritten.

**Why it matters:** this is the exact class of lost-update bug this session spent its entire main effort
fixing in facilities-service (see the companion PR on `task/concurrency-and-local-run-config`), just
reached via a raw JDBC UPSERT instead of a broken JPA `@Version` mapping. The fix pattern is proven and
already shipped elsewhere in this codebase.

**Fix:** Postgres UPSERT syntax cannot express `AND version = ?` inside `ON CONFLICT DO UPDATE`. Restructure
to the same "conditional UPDATE, fall back to INSERT if not found, throw if found-but-0-rows" pattern
already used by every other method in this file (e.g. `saveTransaction`, line 485).

### 2. Same file — `JdbcFuelRepository.saveCard` detects the conflict, then discards it

**File:** same file, `:1385-1431`

Has the `WHERE id = ? AND version = ?` guard, but is missing the
`else if (updated == 0) throw new OptimisticLockingFailureException(...)` branch that `savePostedPrice`
(`:408`), `saveTransaction` (`:547`), `saveLogbook` (`:753`), and `saveAnomaly` (`:851`) all have.

**Failure scenario:** two admins concurrently edit the same fuel card's status/limits. The second
writer's `updated == 0` (row exists, version mismatch) falls through both branches and hits `return c;`
— returning the caller's own, never-persisted, now-stale domain object as if the save succeeded. The
caller has no way to know their write was dropped.

**Fix:** add the missing `else if (updated == 0) throw new OptimisticLockingFailureException("FuelCard version conflict");`, mirroring the sibling methods in the same file.

### 3. `sfl-safety-security-service` — the emergency outbox drainer never actually delivers anything

**File:** `sfl-safety-security-service/src/main/java/gh/edu/clet/sfl/safetysecurity/emergency/infrastructure/messaging/OutboxDrainer.java:62-65`

```java
/** Phase-1 recorded local delivery. A real broker/consumer replaces this without a domain change. */
private void deliver(UUID id) {
    log.debug("Recorded local delivery of outbox message {}", id);
}
```

`deliver()` never throws and never sends anywhere — a no-op that logs at DEBUG and returns. `drain()`
(`:41-59`) then unconditionally marks every claimed row `PUBLISHED`. No RabbitMQ (or any other) transport
is wired into this module at all — there is no `RabbitTemplate` usage anywhere in it. The
retry/backoff/dead-letter scaffolding (`attempt_count`, `max-attempts`) is present but structurally
unreachable, because `deliver()` can never fail.

**Failure scenario:** every emergency-module event written to the outbox — incident escalations, drill
records, provider callbacks — is silently discarded. No consumer, in this service or any other, ever
sees it. The row shows `PUBLISHED`, which reads as "delivered successfully" to anyone checking the
table or an admin endpoint over it. This is the emergency-notification service; this is the one place
where "silent" is the most dangerous.

**Fix:** either wire a real transport (mirror facilities'/fleet's `AmqpEventTransport` pattern) before
this ships to any environment that depends on real notification delivery, or — if genuinely still
Phase-1 and intentional — make that unmistakable: rename the class and log at WARN/ERROR each drain
cycle with a count, so an operator watching logs sees "0 of N messages actually delivered" rather than
silence.

---

## High

### 4. Fleet-logistics' AMQP transport doesn't wait for broker confirmation

**File:** `sfl-fleet-logistics-service/src/main/java/gh/edu/clet/sfl/fleetlogistics/fleet/infrastructure/messaging/AmqpFleetEventTransport.java:48`

```java
rabbitTemplate.send(exchange, routingKeyOf(outboxMessage.eventType()), message);
```

Fire-and-forget: no `CorrelationData`, no wait on a confirm future, no check of publisher returns.
`application.yml` doesn't set `publisher-confirm-type` or `publisher-returns` either
(`sfl-fleet-logistics-service/src/main/resources/application.yml:49-53`).

Compare to `sfl-facilities-service`'s `AmqpFacilitiesEventTransport`, which builds the same envelope
(its own Javadoc says so) but adds `awaitConfirmation()`, waiting on the broker's ack/nack and treating
an unroutable return or timeout as a failure that goes back through the drainer's retry path. That
hardening evidently was never back-ported to fleet.

**Failure scenario:** `rabbitTemplate.send()` returns successfully once the message hits the local
channel buffer — before the broker has necessarily durably received it. A dropped connection mid-publish
or an unroutable routing key look identical to success. `OutboxDrainer.drainOnce()` marks the message
`PUBLISHED` right after `transport.send()` returns without exception — permanently lost, no retry, no
dead-letter, no log.

**Fix:** port `AmqpFacilitiesEventTransport`'s confirm-await logic into `AmqpFleetEventTransport`, and
add `publisher-confirm-type: correlated` / `publisher-returns: true` to fleet-logistics' `application.yml`.

### 5. No timeout on JWT/JWKS fetch from the identity provider, in all three services

**Files:** `application.yml` in facilities (`:63`), fleet-logistics (`:58`), safety-security (`:36`) —
all three set only `issuer-uri`, nothing else. No service defines a custom `JwtDecoder` bean or custom
`RestOperations`/timeout.

With only `issuer-uri` configured, Spring Boot auto-configures a `NimbusJwtDecoder` backed by a default
`RestTemplate` with no explicit connect/read timeout. Two consequences:

- **Lazy, not fail-fast** — the JWKS isn't fetched at startup, only on first token validation. A service
  can boot, pass every health check, and look fully healthy while Zitadel is completely unreachable; the
  failure only surfaces on the first real authenticated request.
- **Unbounded hang under a slow/degraded IdP** — if Zitadel accepts the TCP connection but stalls, the
  fetch has no read timeout and can block for a long time. Under load, with every incoming request
  needing JWT validation, this is a direct path to full request-thread-pool exhaustion off a single slow
  dependency — a classic cascading-outage shape, identical in all three services.

**Fix:** define an explicit `JwtDecoder`/`RestOperations` with a bounded connect/read timeout (a few
seconds) in each service's security configuration, and consider eagerly warming the JWKS cache at
startup so an unreachable IdP fails the readiness probe rather than surfacing on the first user request.

### 6. Blocking booking advisory lock with no timeout can exhaust the whole service's connection pool

**Files:** `JpaBookingJpaRepository.java:200-201` (`pg_advisory_xact_lock`, transaction-scoped — auto-releases
on commit/rollback, not session-leaked, which is good); no `lock_timeout`/`statement_timeout` configured
anywhere in the codebase (verified — no match in any `.java`/`.yml`/`.properties`).

A thread waiting on this lock holds its checked-out HikariCP connection for the entire wait, unbounded.
Combined with finding #11 (no pool sizing anywhere → Spring Boot default max 10 connections per
service), a burst of concurrent booking requests for the same popular room/resource can each grab one of
the pool's 10 connections and queue indefinitely behind the lock. Past 10 concurrent contenders, the
**entire connection pool is exhausted**, starving completely unrelated read requests on the same
service too.

**Fix:** set `lock_timeout` (e.g. `SET LOCAL lock_timeout` before the advisory-lock call, or connection-init
SQL) so contention fails fast into the existing conflict-translation path instead of blocking
indefinitely.

### 7. N+1 / redundant runtime-configuration reads in `PreventiveMaintenanceService.generateDueWorkOrders`

**File:** `sfl-facilities-service/src/main/java/gh/edu/clet/sfl/facilities/maintenance/application/PreventiveMaintenanceService.java:208-236`.
Default batch size is 200 (`MaintenanceConfiguration.generationBatchSize`, `:115`).

Inside the per-schedule loop: `facilities.findAsset(...)` (1 query, not batched),
`configuration.slaPolicyFor(schedule.siteCode())` (~8 config keys, each a real DB query — see below),
`operatingModeOf(schedule.siteCode())` called **twice** per schedule (`:231` and `:233`, each a
`findSiteByCode` query), and `configuration.evidenceRequiredFor(...)` (2 more config reads). That's
~20+ individual DB round trips per schedule, × up to 200 schedules per run, none deduplicated by site,
inside one `@Transactional` method.

Its sibling `MaintenanceEscalationService.sweep()` (same file family) explicitly caches `SlaPolicy` per
site for the run (`Map<String, SlaPolicy> policies = new HashMap<>()`) for exactly this reason — this
method doesn't.

**Related documentation bug, not just performance:** `MaintenanceConfiguration.slaPolicyFor`'s Javadoc
(around line 60) claims building it fresh "is one read per key from a table the port already caches per
request." `JpaRuntimeConfigurationAdapter`'s own Javadoc (around line 19) says plainly "**Nothing is
cached**." These two comments directly contradict each other — the caching the first comment relies on
to justify the design doesn't exist, meaning the true cost is understated in the code's own reasoning.

**Fix:** cache `SlaPolicy`/`OperatingMode` per site code for the duration of the run, and call
`operatingModeOf` once per schedule instead of twice. Correct or remove the contradictory Javadoc claim.

### 8. Unbounded full-table load in audit-chain verification, reachable over HTTP

**Files:** `sfl-facilities-service/.../JpaAuditAdapter.java:133-137`,
`sfl-fleet-logistics-service/.../JpaAuditAdapter.java:89-93`; exposed via
`FacilitiesGovernanceController.verifyChain()` (an authenticated HTTP endpoint) and
`FleetEvidenceApplicationService.java:279`.

`verifyChain()` calls `findAllByOrderBySequenceNoAsc()` — loads **every row ever written** to the audit
table into a `List`, unbounded, on every call. The audit table is append-only and grows with every
create/update/delete across every module for the service's entire lifetime, and this is reachable on
demand via an authenticated API call, not just an offline job.

**Why it matters at scale:** as the table grows past hundreds of thousands/millions of rows (realistic
within 1-2 years of estate-wide operation), this call's heap footprint and query time grow unboundedly —
risking OOM or multi-minute read-transaction holds triggered by an ordinary API call.

**Fix:** verify the chain incrementally/in batches (stream with a cursor rather than materializing a
`List`, or verify only since the last checkpoint and persist a rolling checkpoint hash), or move it to
an offline/batched job with a hard row-count cap per invocation.

### 9. N+1 query on the emergency-activation fan-out (life-safety broadcast path)

**File:** `sfl-safety-security-service/src/main/java/gh/edu/clet/sfl/safetysecurity/emergency/application/service/ActivationService.java:373-379` (`targetCount`), called from `fanOut` on every emergency activation.

```java
for (UUID audienceId : activation.audienceGroupIds()) {
    total += repository.findAudienceGroup(audienceId)...
}
```

One `SELECT` per audience group, sequentially, inside the method that computes the notification
fan-out target count for an **emergency activation**. `EmergencyRepository` has no batch variant
(`findAudienceGroups(Collection<UUID>)`) anywhere — only single-id lookup exists. Latency matters most
on exactly this path; N sequential round trips add avoidable latency as the number of targeted audience
groups grows.

Related, same method (`fanOut`, lines 353-360): the per-channel-type loop does an external `gateway.send(...)`
call then 2 DB round trips (`findChannel` + `saveChannel`) sequentially per channel. With several
channel types configured, one emergency activation serializes N external sends + 2N DB calls
end-to-end before returning — a latency risk on the same critical path worth considering for
parallelization.

**Fix:** add `findAudienceGroups(Collection<UUID> ids)` returning a single `IN`-clause query; sum from
that instead of looping.

---

## Medium

### 10. Fleet-logistics' outbox drainer batches ~50 messages per transaction

**File:** `sfl-fleet-logistics-service/.../OutboxDrainer.java:49-80` — claims up to `batchSize`
(default 50) rows via `PESSIMISTIC_WRITE` + `SKIP LOCKED`, loops `transport.send()` synchronously for
all of them, then a single `saveAll(due)` at the end, all inside one transaction.

`FacilitiesOutboxDrainer` deliberately does the opposite, and says why in its own Javadoc: *"A batch in
a single transaction would let one poison payload roll back every delivery that preceded it in the same
tick."* Worse here: if the process crashes or the DB connection drops mid-loop (after some
`transport.send()` calls already succeeded — events already left the building), the whole transaction
rolls back, every claimed row reverts to `PENDING`, and the next drain resends everything in that batch,
including messages already genuinely delivered.

This is partially mitigated by inbox-side idempotency-key dedup (confirmed present in
`FleetIntegrationApplicationService.java:78`, `findBySourceAndIdempotencyKey`), but that only protects
consumers that implement the same pattern. Holding up to 50 row locks for the duration of N synchronous
network sends is also a connection-pool/throughput concern under load, independent of the crash scenario.

**Fix:** bring fleet-logistics' drainer in line with facilities' one-message-per-transaction design, or
shrink the transaction to just the claim+status-write per message while keeping the outer loop —
document the divergence if the batch shape is intentional for throughput reasons.

### 11. No HikariCP or thread-pool tuning configured, in any of the 4 services

Checked `application.yml` in facilities, fleet-logistics, safety-security, and portal — zero
`spring.datasource.hikari.*` settings in any of them, and no `@Async`/`TaskExecutor` sizing anywhere.
Every service runs on Spring Boot defaults: Tomcat up to 200 request threads, Hikari max 10 connections,
leak-detection disabled. Nearly every endpoint touches the DB, so once concurrent DB-bound requests
exceed 10 — plausible under any real load, given scheduled sweeps, RabbitMQ listeners, and HTTP traffic
all share the same pool — later requests block waiting for a connection and, past the default 30s
timeout, fail outright, well before Tomcat's own thread capacity is a constraint.

**Fix:** explicitly size `spring.datasource.hikari.maximum-pool-size` per service based on expected
concurrency and DB capacity, and consider capping Tomcat's thread count closer to the pool size so the
service fails fast/queues predictably instead of silently timing out.

### 12. Unbounded memory growth in the shared rate limiter

**File:** `sfl-service-common/.../ratelimit/FixedWindowRateLimiter.java`. The internal
`ConcurrentHashMap<String, Window>` (`:22`) is written to on every `tryAcquire` call, but entries for
expired/inactive keys are never removed — `compute` only replaces an entry, never evicts one. Keyed on
`remoteAddr + ":" + servletPath`, so under sustained traffic from many distinct client IPs (public-facing
deployment, NAT'd clients, or just normal traffic growth) this map grows without bound for the life of
the process — a genuine, unflagged memory leak, separate from the class's own documented (and
reasonable) caveat about per-instance-only limits under horizontal scaling.

**Fix:** evict/sweep windows once past `windowMillis` old, or switch to a TTL-evicting cache (e.g. Caffeine).

### 13. RabbitMQ health indicator defaults to disabled

**Files:** `sfl-facilities-service/src/main/resources/application.yml:82-83`,
`sfl-fleet-logistics-service/src/main/resources/application.yml:66-67` — both default
`management.health.rabbit.enabled` to `false`, overridable via `SFL_RABBITMQ_HEALTH_ENABLED`. Reasonable
for local dev (no broker needed there), but means the default readiness probe in *any* environment that
forgets to set the override — including a production one running `transport=rabbitmq` — won't reflect
broker connectivity. An orchestrator would keep routing traffic to an instance whose outbox has stopped
draining, with the backlog growing silently until someone notices via the outbox table directly.

**Fix:** add a startup-time check ("transport is rabbitmq but rabbit health is disabled") so this can't
be a silent misconfiguration in a real deployment.

---

## Low / informational

### 14. No application-level caching layer anywhere

Zero `@Cacheable`/`@EnableCaching`/`CacheManager` usage across the entire workspace, despite the rate
limiter's own comment noting "the platform's Redis instance is already provisioned" for exactly this
purpose. Slowly-changing master data (sites, buildings, rooms, runtime config) is read fresh from
Postgres on every access, including inside hot sweep loops — this is the systemic root cause amplifying
finding #7 and similar N+1s. Not urgent today, but worth a real caching story before load grows
materially.

### 15. Fleet-logistics' consumer-side event-ordering guarantee not verified

Facilities' RabbitMQ listener concurrency is explicitly pinned to 1 for per-vehicle ordering
(`application.yml:41-44`), which is sound. No equivalent explicit ordering guarantee was found documented
for fleet-logistics' consumer side; ran out of scope/time to verify whether fleet's domain has an
ordering-sensitive event type that would need one. Worth a follow-up look.

---

## Ruled out — confirmed clean

- **Fleet-logistics' `VehicleEntity`** and **all of safety-security's incident/visitor/emergency/dispatch
  modules** do *not* reproduce the facilities-service lost-update bug. `VehicleEntity` has `@Version`
  correctly placed directly on the entity (not an `@Embeddable`), `JpaVehicleRepositoryAdapter.save()`
  uses the correct fetch-managed-row-and-mutate pattern, and `JdbcEmergencyRepository`/`JdbcDispatchRepository`
  both implement correct, consistent version-guarded UPDATE + throw-on-conflict across every method
  checked.
- **No livelock/retry-storm risk** from this session's new `@Version` optimistic-locking work in
  facilities-service: no `@Retryable`/`RetryTemplate`/`@Recover` exists anywhere in the codebase, so a
  409 conflict fails once and stops — nothing auto-retries, on either the HTTP path or the background-sweep
  path.
- No TODO/FIXME/stub markers found in any of the 4 non-facilities modules' main source.
- No silently-swallowing catch blocks found (26 broad `catch` blocks sampled — all log/rethrow/translate
  correctly).
- Pagination max-page-size caps (200-500 across facilities/fleet-logistics) and spot-checked booking
  table indexes (`ix_bookings_site_window`, `ix_bookings_live`, etc.) look adequate for their query
  patterns.
- Exception plumbing for optimistic-lock conflicts (`FleetApiExceptionHandler`) is correctly wired across
  every fleet-logistics module including fuel/dispatch — findings #1 and #2 bypass this handler by never
  throwing in the first place, they don't reveal a gap in it.

---

## Recommended priority order

1. **Fix the three Critical findings first** (#1, #2, #3) — all three are real, currently-shipping
   silent data/write loss, in fuel-policy/fuel-card persistence and in emergency-notification delivery.
2. **Then the High findings touching live-fire paths** — the advisory-lock timeout (#6) and the
   emergency-activation N+1 (#9) sit on booking-under-contention and life-safety-broadcast paths
   respectively.
3. **The remaining High and Medium findings** are good candidates for a dedicated hardening pass before
   go-live, but none currently represent silent data loss the way the Criticals do.

Not scoped in this pass: the other `@Scheduled` sweep schedulers in fleet-logistics and safety-security
(`FuelSweepScheduler`, `ComplianceServiceSweepScheduler`, `SlaEvaluationScheduler`,
`EmergencySweepScheduler`) share the same single-large-transaction batch shape as the ones already
reviewed here and in the companion readiness review; a per-row cost deep-dive on each was out of budget
for this pass and is a reasonable next target.
