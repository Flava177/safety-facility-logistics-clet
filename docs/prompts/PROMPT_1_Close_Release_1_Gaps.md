# PROMPT 1 — Close every open gap in the seven built systems and the platform

> Run this first. See [`README.md`](README.md) for why, and for the verified baseline this prompt assumes.

You are working in the CLET Cluster 9 SFL repository. Read `CLAUDE.md`, then `solution.md` — the
implementation log and architecture standard — before touching anything. `solution.md` is appended to
by every pass, and its API-First Build Recipe governs each slice.

## Scope

Close every open gap in the **seven built systems** — S152, S153, S159, S166, S168_fuel, S171, S174 —
and the platform blockers underneath them. This makes **Release 1** (IFIMP + FTLMP + S174) certifiable.

**Out of scope, deliberately:** S160, S160a, S161, S162, S162a and S163. Those six SSEMP systems have
no domain model, no API, no migration and no test; `sfl-safety-security-service` is one Java class and
a foundation migration. That is unbuilt scope, not a gap. Do not start it here, and do not let it
dilute this pass.

## Environment

- Java 17 at `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`; set `JAVA_HOME` before Maven.
  Note the machine's `PATH` still carries Zulu 11, which cannot build the reactor.
- **Docker and Testcontainers are now available.** The e2e suites that have always skipped can execute.
- **`sfl-facilities-service/target/surefire-reports/` still holds three stale reports** for test
  classes deleted in the S153 rewrite (`WorkOrderServiceTest`, `FacilityFaultTest`, `WorkOrderTest`),
  carrying six phantom failures. A root `mvn clean` did not remove them. Run
  `../mvnw.cmd -pl sfl-facilities-service clean` before trusting any aggregated test count in this
  module — including your own.
- Per-service Postgres containers on 5441–5445, e2e copies on 55441–55445.
- Backend `../mvnw.cmd -pl <module> test` from `services/`; frontend `npm run test` / `npm run build`
  from `frontend/sfl-operations-ui/` (dev server on 5005).

---

# A. Platform work

## A0 — Per-record authorisation in fleet, fuel and dispatch  ✅ **DONE, 31 July 2026**

> **The premise in the first draft of this section was wrong and is corrected here.** It claimed there
> was "no per-record narrowing anywhere in `fleetlogistics`", based on a search of `*/domain/policy/`
> only. The narrowing actually lives in `application/service/` — `FuelAccessPolicy.isDriverOnly` and
> `FleetAccessPolicy.requireRecordScope`. The real defect was narrower and sharper: the rules existed,
> were documented, were unit-tested, and were applied to collections but not to records.

**What was actually broken, and is now fixed:**

- **`FuelApplicationService.logbook(id, actor)` had no ownership check.** The list was narrowed in SQL
  on `created_by`; the detail read was not. A driver holding a colleague's logbook id read journey,
  route, purpose and passenger notes in full.
- **`FleetAccessPolicy.requireRecordScope` was enforced nowhere.** Documented as "what keeps the
  limited driver/mobile user class to their own trips and inspections", unit-tested, and called in
  production exactly once — with `null` as the owner reference, which the policy returns on
  immediately. No read called it at all.
- **Ownership refusals were `IllegalStateException`** — a 500, with no denial written to the audit
  chain. Now `FleetAuthorizationException`: 403, SRS envelope, audited.

Ownership joins the two identity models through the driver's `staffReference`, the value an actor
signs in as. A supervising `FLEET_TRIP_MANAGE` passes through. Proved by refusal **by id** in
`FleetCriticalScenariosEndToEndTest` 7a and `FuelMandatoryScenariosEndToEndTest`.

**Dispatch is deliberately not narrowed.** `Dispatch.destinationCentre` and `assignedHandler` are
`VARCHAR(200)` free text with no relationship to a principal, so narrowing on them would hold only
when someone happened to type an actor id — worse than no rule, because it looks like enforcement. It
needs a principal-bound centre/handler reference: schema change plus an identity decision for the
Transportation & Logistics Unit. Recorded in `S166_Gap_And_Conflict_Report.md` C-16, not guessed.

**Still open:** a `FLEET_DRIVER` reads every fuel *transaction* at their site. Whether they should is
a policy question for the same owner; recorded in `S168_Fuel_Gap_And_Conflict_Report.md`.

**The three precedents to copy** — read all three before writing anything, because they already
resolved the design questions:

| Precedent | Where | Rule it encodes |
|---|---|---|
| Vendor technician | `WorkOrderApplicationService.assertVisible` / `.vendorFilter` | Narrowed by `assignedTo` matching the actor id, applied to **every read and every write** |
| Requester (faults) | `FacilityFaultService.requesterFilter` | Narrows only when `IFIMP_REQUESTER` is the actor's **only** facilities role |
| Requester (bookings) | `BookingApplicationService.assertVisible` / `.requesterFilter` | Same shape, and the model for what S159 already does correctly |

Two rules those precedents establish, and this work must preserve:

1. **The union of roles is not its narrowest member.** A manager who also holds the requester role is
   a manager. Narrowing on the union would make *adding* a role to somebody take capability away.
2. **A narrowing only one of reads or writes obeys is decorative.** Apply it to both. Filter in the
   SQL query, never in the browser and never in memory after loading.

**Build.** The boundary is the relationship — driver-of-trip, author-of-logbook, addressee-of-consignment
— because "the ones that are mine" is a property of the record and cannot be expressed in a role matrix.
Encode that reasoning in a comment at the enforcement point, as the three precedents do.

**Prove the negative.** A list that happens to be empty is not evidence. Tests must show a second
driver's logbook is refused **by id**, and a second centre's manifest is refused **by id**, with the
uniform error envelope and a 403.

**Record the widening question.** S153 chose per-person rather than per-firm narrowing for vendors and
wrote down why, noting that a firm-level view needs the vendor id on the actor. Take the equivalent
decision here for `CENTRE_MANAGER` — is the boundary the person or the centre? — and record it rather
than leaving it implicit.

## A1 — Turn authentication on

**What already exists.** More than the gap reports imply — check before building:

- `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${SFL_IAM_ISSUER:http://localhost:8080/realms/sfl}`
  is configured in **every** service's `application.yml`.
- Both filter chains are written: `developmentSecurity` (permitAll) and `keycloakSecurity`
  (stateless, JWT resource server, `realm_access.roles` → `ROLE_*` authority converter).
- Full JWT actor resolution exists in `FacilitiesActorResolver`, `FleetActorResolver` and
  `EmergencyActorResolver` — `fromJwt` reads `sub`, `name`, realm roles and the site-scope claim, with
  the `X-SFL-*` headers as fallback only when no authenticated JWT is present.

**The actual gaps, all four of them:**

1. **The default is open.** `sfl.security.enabled` defaults to `false` in every service, and the dev
   chain is annotated `@ConditionalOnProperty(..., matchIfMissing = true)` — so a **missing** property
   also yields permitAll. `deploy/compose/docker-compose.microservices.yml` never sets it. Every API
   is currently unauthenticated. Invert this: production-safe by default, dev opened only by explicit
   opt-in under a named profile.
2. **There is no identity provider in the stack.** `deploy/keycloak/` is an **empty directory** and the
   compose file has no Keycloak service — only Postgres containers and the five services. Add it, with
   a realm carrying the 26 `SflRole` values as realm roles and a mapper emitting the site-scope claim
   the resolvers already read.
3. **No test anywhere exercises the JWT chain.** Searching every `src/test/java` for `keycloakSecurity`,
   `sfl.security.enabled=true` or `JwtAuthenticationToken` returns nothing. The production security
   path has never been executed. Add contract tests: a valid token resolves to the right
   `SflPrincipal` with roles and site scopes; an absent token is 401; a token whose realm roles do not
   carry the permission is 403 and **is audited as a denial**, per the existing
   `AUTHORIZATION_DENIED` behaviour.
4. **`sfl-asset-visibility-service` has a security configuration but no JWT actor resolver** — the
   other three have one. Confirm how it resolves an actor under `enabled=true` and give it the same
   path if it lacks one.

Keep the header resolver available for local development, because the actor switcher in the dashboard
depends on it — but behind an explicit profile, never as the fallback for absent configuration.

## A2 — Run the mandatory-scenario suites  ✅ **101 of 102 DONE, 31 July 2026**

> **Docker being up was not enough.** Testcontainers asks whether the *Java* Docker client can reach
> the daemon, and on Windows it cannot — the named-pipe transport fails while `docker ps` works. The
> suites kept skipping. The route through is the external-database escape hatch that already existed
> and nothing was using:
>
> ```
> SFL_FLEET_LOGISTICS_TEST_DB_URL=jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e
> SFL_EMERGENCY_NOTIFICATION_TEST_DB_URL=jdbc:postgresql://localhost:55445/sfl_emergency_notification_service_e2e
> SFL_FACILITIES_TEST_DB_URL=jdbc:postgresql://localhost:55441/sfl_facilities_migration_test
> SFL_TEST_DB_USERNAME=sfl
> SFL_TEST_DB_PASSWORD=sfl
> ```
>
> `FacilitiesMigrationIntegrationTest` had no hatch and gained one (`FacilitiesPostgresSupport`). Its
> database **must be empty** — it asserts absolute facts about a virgin schema, which Testcontainers
> had been supplying implicitly. Recreate it before each run; the command is in the class javadoc.
>
> **Backend now runs 744 tests, 0 failures, 1 skipped** (was 641 run, 102 skipped). The suites passed
> on first execution — they did not surface the pile of defects this section predicted. The single
> issue found was an order-dependent assertion in the V7 checklist-seed test, since corrected.
>
> **Outstanding:** `FleetPostgresEndToEndTest` (1 test) is still `@Testcontainers`-gated. Give it the
> same support class. And put these variables in CI — evidence that depends on a developer
> remembering an environment variable is not evidence.

The suites, for reference:

| Suite | Skipped | System |
|---|---|---|
| `EmergencyMandatoryScenariosEndToEndTest` | 22 | S174 |
| `DispatchMandatoryScenariosEndToEndTest` | 19 | S171 |
| `FuelMandatoryScenariosEndToEndTest` | 17 | S168_fuel |
| `FuelCriticalScenariosEndToEndTest` | 1 | S168_fuel |
| `FleetCriticalScenariosEndToEndTest` | 16 | S166 |
| `FleetPostgresEndToEndTest` | 1 | S166 |
| `FuelGapClosureEndToEndTest` | 14 | S168_fuel |
| `FacilitiesMigrationIntegrationTest` | 12 | S152/S153/S159 |

**The mandatory-scenario evidence for S166, S168_fuel, S171 and S174 has never executed in this
environment.** Docker is now up. Run them, fix what they find, and record the outcome per system.

Expect defects. This repository's own history is that a real database found **eight** defects in S152
while 135 unit tests were green — `CHAR(64)` columns Hibernate rejects, constructors that had never
let the service boot, an audit chain that replayed as tampered twice over — and **two** in S159 while
290 were green: HTTP 500 on every booking search from a null `Instant` bind, and a deadlock that
turned fifteen of sixteen concurrent bookings into 500s. Treat a suite that passes first time as a
result to verify, not a result to celebrate.

## A3 — Publish the events

`sfl-facilities-service` has **no messaging adapter at all** — its `infrastructure/messaging/` package
is empty — so all of IFIMP writes outbox rows that nothing drains. Fleet and emergency default to
`transport: local`.

Three gap reports record the same consequence in different words: no S153 escalation, no S159
rejection, no-show or readiness hold, and no booking decision ever reaches a person; no cross-module
saga runs end to end. S153 §3.2 and S159 §3.2 are one gap with one fix.

Build **one** drainer for IFIMP, following the existing `OutboxDrainer` in
`sfl-emergency-notification-service` and `fleet/infrastructure/messaging/`: `@Scheduled`,
`FOR UPDATE SKIP LOCKED`, exponential backoff, poison after N, at-least-once. The delivery-state
columns added in facilities `V5` are the schema half already in place. Default the transport to the
broker outside dev, and **prove a message crosses the exchange** — a drainer that marks rows published
without a broker on the other end is the failure this is meant to end.

## A4 — Rename the non-conforming event types

`sfl.facilities.*` and `sfl.assetvisibility.*` violate the catalogue's
`sfl.{platform}.{event-name}.v{version}` rule, carrying the version in a separate column instead of
the suffix (S166 gap C-03). Fleet already conforms and asserts it with a regex in `FleetEventTypeTest`.

Rename to `sfl.ifimp.*.v1` and `sfl.avamp.*.v1`, update `docs/integration/event-catalog.md`, and add
the same regex assertion to the two services being corrected. **Do this now, before any external
consumer binds a routing key** — after that it is a breaking change, and A3 is about to make these
events externally visible for the first time.

## A5 — Write the runbooks

`docs/runbooks/` exists and is **empty**. Architecture gate G10 and workplan W7 cannot pass without
DR, incident, dead-letter and backup/restore procedures. Write them from what the code actually does —
the outbox poison path, the dead-letter exchange `sfl.events.dlx`, the per-service Flyway baseline and
the audit chain's replay verification each have a real operational procedure behind them.

## A6 — Decide row-level security

Site scope is enforced in the application layer and filtered in SQL — correct and tested, but not the
defence-in-depth that `solution.md` and workplan §7 both require. S166 C-09 records why it stalled:
how the request principal reaches the database session (per-request `SET LOCAL app.site_scopes`, or a
per-tenant role) is an operational decision under connection pooling, not a code-local one.

Take the decision, record it as an ADR, then either implement it in a follow-up migration or formally
defer it with a named owner. Do not leave it as an open question in a gap report for a third pass.

---

# B. Per-system gaps

## S152 CAFM / IWMS

- No inbound webhook endpoint, and therefore no HMAC/mTLS signature verification (C-04). S153 shares
  this gap. Build it only if A1 gives you a real sender to validate against; otherwise record why not.
- Floors have no screen of their own (UI gap §2.4).
- Readiness recomputation is synchronous. Correct at Phase 1 volumes — **record it, do not build** the
  site-wide recompute.

## S153 CMMS

- **Consume the escalation events.** §3.2 is explicit: the requirement is not met until somebody is
  told. A3 unblocks it; this is the half that closes it.
- **Sweep evidence disposal** (§3.4). `disposalEligibleFrom` is computed and the
  `ix_maintenance_evidence_retention` index exists for exactly this query. Nothing runs it. It deletes
  things, so build it deliberately and on its own.
- **Enforce the response SLA** (§3.5). `maintenance.sla.response.*` is read, stored and exposed, but
  only the resolution deadline drives escalation. Separating "nobody has picked this up" from "nobody
  has finished this" needs a second escalation track with its own recipients — not a second `if` in
  the sweep.
- **File upload** — the UI has none anywhere (UI gap §2.1). Evidence is by reference with a SHA-256,
  so this is an upload path plus a hash, not a blob store.
- **Go-live item for the cutover checklist, not the code:** faults migrated open have no SLA and will
  never escalate until re-triaged. `slaDueAt` is deliberately null on migration because back-dating
  would produce a wall of instant breaches on day one.

## S159 Room and Resource Booking — the largest single gap

**It has no screens at all.** Four controllers, 25 API paths, driven only by the API. It is also
absent from `SystemCode` in `frontend/sfl-operations-ui/src/shared/layout/programmeModel.ts`, so no
role sees a booking section even though the permissions exist.

Build the module in `src/modules/facilities` — the same service, client and envelope as S152 and S153,
not a parallel module. Cover: the diary and availability search, request, approve and reject, override
with a recorded reason, readiness holds, no-show, and the setup-task list. Controllers are
`BookingController`, `BookingAvailabilityController`, `BookableResourceController` and
`BookingSetupTaskController`; permissions are `FACILITIES_BOOKING_READ`, `_REQUEST`, `_APPROVE`,
`_CANCEL` and `_OVERRIDE`.

Two things the backend already does that the UI must not undo:

- **A requester is narrowed per record** by `BookingApplicationService.requesterFilter`, on reads and
  writes. `IFIMP_REQUESTER` deliberately does **not** hold `_CANCEL`; cancelling one's own booking is
  allowed by the per-record rule instead. Do not read the missing permission as a missing capability.
- **Empty states must describe what is visible to you**, not what exists — a requester sees only their
  own bookings, so "the hall is free all week" is a claim the screen cannot make.

Then, in order of consequence:

1. **Confirm `btree_gist` is installable in the production database.** The no-double-booking guarantee
   is a PostgreSQL `GIST` exclusion constraint; a managed Postgres with an extension allow-list would
   refuse it and the module's central rule would silently become advisory. Check **before** the first
   deploy, not at it.
2. **Recurrence**, before the first full academic year is timetabled — a twelve-week lecture is
   currently twelve `POST /bookings` calls. Do it properly, with its own aggregate and an exception
   model, or not at all: expanding to twelve independent bookings connects nothing when the room is
   lost in week six.
3. **A per-site decision on `booking.no-show.grace`.** Twenty minutes is a default, not a policy.

**Record, do not silently fix:** capacity stays advisory (§3.4), and pooled-resource arithmetic is not
race-proof (§3.5) — an exclusion constraint can say "these rows must not overlap" but not "their
quantities must sum to no more than forty". Closing that properly means a per-resource counter row
updated with `SELECT ... FOR UPDATE`, and it is a real option only if it ever bites.

## S166 Fleet and Vehicle Management

- Resolve the two owner decisions rather than carrying them: confirm or amend the `SRS-SFL-S166-06`
  traceability (C-01), and correct the workplan §4.3 endpoint→requirement table, which contradicts SRS
  semantics (C-02).
- Confirm W6 as the home for emergency logistics (C-12), or amend the SRS. No
  `/api/v1/fleet/emergency-logistics` endpoint is to be added here.
- **Verify the audit hash chain against a real database.** S152 §8a warned fleet likely carried its
  D-04 and D-05 defects. On inspection the jsonb half is **already handled** —
  `AuditRecordEntity.toDomain(ObjectMapper)` re-canonicalises the stored JSON on read through
  `CanonicalJson`, so PostgreSQL's key reordering is neutralised. The timestamp half is **unresolved**:
  `AuditHashChain.canonical` hashes `occurredAt.toString()`, and there is no `truncatedTo(MICROS)`
  anywhere in the service, while PostgreSQL stores microseconds. Replay a real chain with
  `verifyChain()`. If it reports tampered, truncate before hashing exactly as S152 did.

## S168_fuel Fuel Management and Driver Logbooks — the largest residual backlog

- **`SRS-SFL-S168fuel-04` card management is not implemented — there is no card registry at all.**
  Reconciliation cannot perform fuel-card or allocation checks without one. This is a missing
  requirement, not a refinement.
- Reconciliation lacks daily, weekly and monthly rolling limits.
- `COST_VARIANCE` uses a documented default tolerance **constant**; it must become a versioned,
  effective-dated policy column like every other threshold in this platform. No threshold is
  hard-coded as institutional policy — that is the F-07 rule.
- Governed evidence export, legal hold and audit-tamper flows are not exposed through a fuel port,
  though the fleet foundation implements all three.
- List endpoints use a bounded `limit` rather than the documented page/cursor contract with stable
  sort keys and pagination metadata.
- Frontend register gaps 7 and 8 are **documentation** defects — `RESUBMITTED` is undocumented and
  three transaction statuses are unreachable. Write them up; the code is correct.

## S171 Mailroom / Courier and Dispatch

Cleanest of the seven. Confirm the D-01 dual numbering (SRS `01…05` vs build-prompt `01…06`) stays
reconciled in the RTM so neither scheme is orphaned. GPS/telematics and RFID remain Phase-2 seams with
recorded adapters — **do not build them**.

## S174 Emergency Mass Notification

- **`cancel`, `reopen` and `withDegradedFallback` have no endpoint.** Held open because the operational
  meaning is undecided, not because the code is hard: a cancelled activation and a rejected one are
  different records to an auditor, and `reopen` on a closed activation raises a closure-evidence
  question nobody has answered. **Get the decision from the Emergency Coordinator, then build them** —
  an activation that cannot be cancelled sits in the register forever as open, because
  `activationOpen()` counts anything not closed. `escalate` is reachable through the scheduled SLA
  sweep and needs no manual door.
- The CSV export truncates silently at 500 rows. Either paginate it or say so in the file.
- **S174 still has no vendor gateway**, so it cannot actually notify anyone. That is procurement, not
  code — it belongs in the gap report and the go-live pack, and it must not be papered over with a
  simulator that reports success.

---

# C. Verification

**Run it against a real database before calling it done.** Testcontainers now works, so this is no
longer a manual substitute for the real thing.

For every slice:

1. `../mvnw.cmd -pl <module> test` green, with the e2e suites **executing, not skipping** — check the
   surefire skip count, do not assume.
2. The service boots against real PostgreSQL with `ddl-auto: validate` passing.
3. Migrations apply on an empty schema **and** on one already carrying the previous version. `VARCHAR(n)`
   with a length `CHECK`, never `CHAR(n)`. Alter and backfill rather than drop and recreate.
4. The workflow is driven end to end **as each affected actor**, including the ones who should be
   refused. For A0, prove the refusal by id.
5. Frontend: `npm run test` and `npm run build`, then drive the screens in a browser against the
   running services. A green build and a green typecheck have twice hidden a module-wide defect here.

# D. Documentation

- Every gap report updated with what closed each item and what remains, in the house style — honest
  about what is not built and what running it found. `docs/facilities/S153_CMMS_Design.md` is the model.
- `solution.md` gets one pass entry per slice, in the existing voice, referencing its `SRS-SFL-*` IDs.
- `docs/integration/event-catalog.md` reflects A4.
- New ADRs for the A6 decision and the S174 activation-lifecycle decision.
- **No AI-attribution trailers in any commit, PR body or changelog entry.**
