# SFL Implementation Solution Log & Architecture Standard

> **Authoritative references**
> - **Specification (the contract):** `docs/System Mappings and SRS/CLET_Cluster9_SFL_Phase1_SRS_v1.0.docx` — the 13 Fast-Track systems, all functional/non-functional requirements. Every module, endpoint, event and test traces to an `SRS-SFL-*` ID.
> - **Build plan:** `docs/SFL_Phase1_Implementation_Workplan.md` — hexagonal design, per-service backlog, delivery waves, testing/commissioning.
> - **Reference implementation pattern:** the S074 comms-service (API-first, contract → 202 → fast/deferred processing → transactional audit outbox → runtime-resolved adapter registry → OIDC/JWT + permission checks → emergency fast-lane). We mirror its *shape*, re-expressed in Java/Spring with hexagonal layering.
> - **ADRs:** `docs/adr/0001` (foundation), `0002` (build/buy/hybrid), `0003` (Java/Spring migration).

We implement to the SRS. Where the SRS and any earlier note disagree, **the SRS wins** and this log is corrected.

---

## Active Architecture

The active SFL implementation is the **four-service Spring Boot microservices** workspace under `services/`:

- `sfl-facilities-service` — IFIMP: S152 CAFM/IWMS, S153 CMMS, S159 Room & Resource Booking, hall-readiness. Schema `facilities`.
- `sfl-safety-security-service` — SSEMP: S160 Visitor, S160a Access Control, S161 CCTV, S162 Intrusion, S162a Life-Safety, S163 HSE, S174 Emergency Notification. Schema `safety_security`. Split into sub-contexts (`visitor`, `accesscontrol`, `cctv`, `intrusion`, `lifesafety`, `hse`, `emergencycomms`) so life-safety is not coupled to visitor badges.
- `sfl-fleet-logistics-service` — FTLMP: S166 Fleet, S168_fuel Fuel & Logbooks, S171 Mailroom/Courier & Dispatch. Schema `fleet_logistics`.
- `sfl-asset-visibility-service` — AVAMP-Lite: asset/device/location references for all services. Schema `asset_visibility`.
- `sfl-service-common` — shared kernel (principal/RBAC, error & event envelopes, outbox/inbox contracts, integration-security primitives). Library, no schema.

Each service **owns its database schema, migrations, API boundary, domain model, outbox and idempotent inbox**. Cross-service workflows use **APIs, events and sagas** through the enterprise API gateway, IAM, event broker, audit/evidence, notification, reporting and document/object-storage services. **Services must not share databases or depend on another service's internal persistence model.** The root Spring Boot app under `src/main` is migration/reference material only.

---

## Architecture Standard (Clean / Hexagonal — Ports & Adapters)

Every service, every feature, uses this layering and dependency rule:

```
gh.edu.clet.sfl.<service>.<feature>.
  api/                inbound adapter: controllers, request/response DTOs, Bean Validation,
                      principal mapping, HTTP status + error envelope, OpenAPI
  application/
    command/          write use cases (transaction boundary lives here)
    query/            read use cases / read models
    port/             outbound ports (interfaces the app owns): repositories, EventPublisher,
                      AuditPort, VendorXAdapter, IntegrationInbox, ClockPort, IdGenerator, RuntimeConfigPort
    workflow/         sagas / process managers
  domain/
    model/            aggregates, entities, value objects (NO Spring/JPA/Jackson/HTTP/vendor imports)
    event/            domain events
    policy/           invariants, status transitions, authorization predicates
  infrastructure/
    persistence/      JPA entities + Spring Data repos implementing repository ports
    messaging/        RabbitMQ publisher, outbox drainer, inbound consumers
    integration/      vendor adapters (CCTV/access/alarm/fire/fuel/notification/courier/telematics)
    security/         OIDC/JWT → SflPrincipal mapping, method authorization
    config/           Spring config + runtime-config provider (config-without-code)
```

**Dependency rule (ArchUnit-enforced):** `api → application → domain`; `infrastructure → application/domain`. Nothing points into `infrastructure`. The **domain layer imports no framework**. Modules reference only other modules' contracts + published events — never another module's `domain`/`application`/`infrastructure`. **A DB-lint fails the build on any cross-schema foreign key.**

**Ports own every boundary; one adapter per product.** Identity (OIDC/JWT), messaging (RabbitMQ), cache (Redis), persistence (Postgres) and every vendor device system live behind a port with exactly one adapter, so any product is swappable by configuration — not a rewrite.

---

## API-First Build Recipe (do this for every requirement slice)

Same discipline as comms and as the AVAMP asset API already built. One or a few `SRS-SFL-<system>-NN` per slice, in this fixed order:

1. **Contract** — OpenAPI operation(s) + `api` DTOs with Bean Validation, derived from the requirement's *Requirements* and *Status Matrix*.
2. **Controller (stub)** — correct status (`202 Accepted` + id for async submits; `200/201` sync).
3. **Contract test** — WebMvc slice: happy path + the requirement's *Error States* envelope + 403 authorization. (Executable *Acceptance Criteria*.)
4. **Domain** — aggregate(s), value objects, events, `policy`; unit test each business acceptance criterion.
5. **Application use case** — command/query with the transaction boundary; define new outbound `port`s.
6. **Persistence + Flyway** — JPA entity + repo implementing the port; `V#__<slice>.sql` in the service schema.
7. **Vendor adapter (Buy/Hybrid only)** — implement the vendor port with a **simulator first**; real vendor after contract tests pass.
8. **Async worker/consumer** — outbox drain publishes the slice's events; inbox consumers react idempotently.
9. **Read model / dashboard** — projection + query endpoint feeding dashboards + Analytics (S225).
10. **Authorization + site-scope + audit** — `AuthorizationPolicy` + RLS + audit record on every state change.
11. **Integration + architecture tests** — Testcontainers (Postgres/RabbitMQ/Redis), saga tests, ArchUnit gates.

**Contract conventions:** `/api/v1/<domain>/<resource>`; `202 Accepted` + `{requestId}` + status endpoint (+ signed callback for source systems); `Idempotency-Key` header + dedup on state-creating POSTs; uniform error envelope matching SRS *Error States*; cursor pagination; `X-Correlation-ID` end-to-end.

---

## Eventing, Outbox / Inbox & Data conventions

- **Broker:** RabbitMQ. Exchange `sfl.events` (topic); routing key `{platform}.{event-name}.v{version}`; dead-letter `sfl.events.dlx`; consumer queues `sfl.{consumer}.{purpose}`. A **fast-lane** exchange/queue carries life-safety/emergency events (S162a/S174) and is drained ahead of standard traffic.
- **Envelope (every message):** `eventId, eventType, eventVersion, occurredAt, publishedAt, correlationId, causationId, siteCode, sourceModule, traceparent, payload`.
- **Outbox → publish:** business change **+** outbox row in one local transaction; an `OutboxDrainer` (`@Scheduled`, `FOR UPDATE SKIP LOCKED`, exponential backoff, poison after N) publishes. At-least-once.
- **Inbox → consume:** write `eventId` to `inbox_messages` before processing; consumers are idempotent (Redis may front the dedup, Postgres is the record).
- **Data:** schema per service; Flyway per service; **Postgres Row-Level Security** + repository site-scope filter driven by the principal's `SiteScopes`; **no cross-schema FK**.
- **Evidence by reference:** store references + hashes, not raw video/large files, unless an approved policy exception (S161, §4.2).

---

## Security & Configuration

- **AuthN:** OAuth2 **resource server** validating OIDC/JWT (standards-based, provider-pluggable — swap = config). Runtime validation is pure OIDC/JWKS so it works offline at the edge via cached JWKS. No local credential store.
- **AuthZ:** `AuthorizationPolicy` (in `sfl-service-common`) on every command/query — role **and** site-scope; denials audited. Dev uses the `X-SFL-*` header actor; prod swaps in the JWT/claims-backed `ActorContext` producer (same interface).
- **Inbound integration security:** every vendor webhook — per-vendor HMAC or mTLS, source allowlist, schema validation, store-raw → normalise → publish; reject-and-log otherwise (SRS 0F, CT-19).
- **Audit:** immutable, **hash-chained** (`hash = H(prev_hash || canonical(record))`), insert-only writer role; tamper detectable by replay (PLAT-03, CT-18).
- **Config-without-code:** SLA thresholds, zones, severities, fuel limits, readiness checklists, retention and vendor/provider config are runtime-configurable, versioned and audited (PLAT-05). Secrets are vault references, rotated without redeploy.
- **Edge survivability:** local outbox + offline token validation + permission snapshot keep a centre operating during WAN loss; reconcile on restore (PLAT-04, CT-17).

---

## SRS-Driven Build Sequence (see the Workplan for detail)

- **R0 Foundation** — `sfl-service-common` kernel + per-service foundation (outbox drainer, RabbitMQ topology, resource server, audit hash-chain, ArchUnit/DB-lint, edge skeleton). Prove the spine end-to-end. *(PLAT-01..05)*
- **W1 Facilities core + AVAMP** — S152, S153 spine, readiness v1; extend AVAMP references.
- **W2 Physical security + IntegrationHub** — S160, S160a, S161, S162 + inbound-security (CT-19).
- **W3 Life-safety + Emergency comms + Fast lane** — S162a, S174 (CT-20; SFL never in actuation path).
- **W4 HSE** — S163.
- **W5 Fleet & Logistics** — S166, S168_fuel, S171.
- **W6 Sagas + Edge + Dashboards** — hall-readiness, emergency-incident, secure-dispatch sagas; PLAT-04/06/07 (CT-17).
- **W7 Commissioning & Go-Live** — NFRs §6, CT-17..21, DR drill, sign-off.

**Reference slice (the template every team copies):** `POST /api/v1/facilities/work-requests` → `work-order` → outbox `sfl.ifimp.work-order-created.v1` → audit hash-chain → readiness re-score, built strictly through the API-First recipe above.

---

## Implementation Log (history)

### Pass — Facilities IFIMP vertical slice (migrated to `sfl-facilities-service`)
`sfl-facilities-service` owns S152 facilities master data (sites, buildings, floors, rooms, zones, room readiness, device/location references), the S153 fault-reporting/maintenance-intake foundation, and S159 readiness hooks through room/resource readiness data. It owns the `facilities` schema and reads/writes no other service schema.
Package layout: `masterdata.{domain,application,api,infrastructure.persistence}`, `maintenance.{domain,application,api,infrastructure.persistence}`, `shared.{application,infrastructure.persistence}`.
Persistence/migrations: `V1__service_foundation.sql` (schema, metadata, outbox, inbox/idempotency), `V2__facilities_master_data.sql`, `V3__facility_faults.sql`. Eventing records service-local integration events in `facilities.outbox_messages` via the `ServiceOutbox` port (no writes to old monolith schemas). The service landing page at `/` was retired by ADR 0006 once S152 had dashboard screens; it now redirects to `/ui/facilities`.

### Pass — AVAMP-Lite Asset Visibility slice (`sfl-asset-visibility-service`)
Owns asset/device/location reference records used by other services (not financial accounting, full RFID stocktake, vendor scanner management or depreciation). Capabilities: register asset/device references (category, site, location, custodian, external ref); move to a new site-scoped location; assign/clear custody; link evidence metadata references (no files); query by site and by location; publish service-local outbox rows for registration/location/custody/evidence changes.
API: `POST /api/v1/assets`, `GET /api/v1/assets?siteCode=...`, `GET /api/v1/assets/{assetId}`, `GET /api/v1/assets/by-location`, `PATCH /api/v1/assets/{assetId}/location|custody|evidence`.
Persistence: `V2__asset_references.sql` (`asset_visibility.asset_references`); foundation migration owns `asset_visibility.outbox_messages` and `inbox_messages`.

### Pass — Asset API contract tests + Facilities work orders
Added Spring Boot WebMVC contract tests for `sfl-asset-visibility-service` (register/list/lookup/move + validation error envelope). Added the S153 work-order foundation in `sfl-facilities-service`: `WorkOrder` model, `WorkOrderStatus` lifecycle (`OPEN`, `ASSIGNED`, `CLOSED`), create-from-fault, assign, close with notes, and service-local outbox events `sfl.facilities.work-order-created|assigned|closed`.
API: `POST /api/v1/facilities/work-orders/from-fault`, `PATCH /api/v1/facilities/work-orders/{id}/assignment|closure`, `GET /api/v1/facilities/work-orders[/{id}]`. Persistence: `V4__work_orders.sql` (`facilities.work_orders`, referencing `facilities.facility_faults` inside the same schema only).

### Pass — Role, Actor and Site-Scope foundation (`sfl-service-common`)
Shared security concepts reusable by all four services without coupling to a specific IAM product: `SflRole` (`SFL_ADMIN`, `FACILITIES_DIRECTOR`, `FACILITIES_MANAGER`, `IFIMP_MAINTENANCE_SUPERVISOR`, `IFIMP_TECHNICIAN`, `IFIMP_REQUESTER`, `VENDOR_TECHNICIAN`, `COMMAND_ROLE`, `AUDITOR`, `DTI_ADMIN`, `INTEGRATION_ENGINEER`); `SflPermission`; `SiteScopedPrincipal`; `ActorContext`; `AuthorizationPolicy` (`hasRole`/`hasAnyRole`/`canAccessSite`/`require*`). Workflow authorization stays separate from IAM: the identity provider supplies identity/groups/claims; each service still enforces its own workflow role and site-scope decisions.
Dev header actor resolver reads `X-SFL-User`, `X-SFL-Display-Name`, `X-SFL-Roles`, `X-SFL-Sites`, `X-Correlation-ID`; the future JWT path replaces the header resolver with a JWT/claims-backed adapter producing the same `ActorContext`. S153 work-order commands enforce role + site access (create/assign: `SFL_ADMIN`/`FACILITIES_MANAGER`/`IFIMP_MAINTENANCE_SUPERVISOR`; close: supervisor/technician/vendor; read: facilities + `AUDITOR`/`COMMAND_ROLE`); authorization failures return 403.

### Pass — Bootstrap facilities dashboard
Dev/demo UI served by `sfl-facilities-service` at `/`: Command Center (service health, open work, registry counts, recent records), Facilities Registry (S152 sites/buildings/floors/rooms/readiness), Maintenance (S153 faults → work orders → assign/close), Asset Visibility (AVAMP-Lite register/list), Actor/Services config (dev actor + API base URLs). Thin frontend shell over service APIs (`static/index.html`, `assets/css`, `assets/js`) intended for later replacement by a React app without rewriting backend workflow logic. Local CORS for dev origins (8091/8094/5173/3000).

### Pass — S152 CAFM/IWMS platform (`sfl-facilities-service`)
`SRS-SFL-S152-01..05`, NFR 23.3, NFR 23.8. Built S152 as the **host platform for IFIMP**, because the C9 mapping makes S153 a "sub-system of CAFM (S152)" and puts S158 and S159 under it too — the space and asset registry has to exist before anything can reference it.

**Platform, in `facilities.shared`, inherited by S153 and S159 rather than rebuilt per module:** hash-chained append-only audit (`AuditHashChain`, single-row chain head under a pessimistic lock, DB trigger refusing UPDATE/DELETE, denials audited as `AUTHORIZATION_DENIED`); idempotency store keyed on operation + key + request fingerprint; effective-dated runtime configuration read at evaluation time; `FacilitiesActorResolver` (JWT or `X-SFL-*` headers); correlation-ID filter; uniform error envelope with SRS-worded codes; `FacilitiesPermissionMatrix`; `RecordMetadata` and `RecordLifecycleStatus`.

**Estate:** site → building → floor → space extended with system-managed fields, four-state lifecycle (`ARCHIVED` terminal), `SpaceType` with bookable/examination-capable defaults, area and cost centre; site `OperatingMode` (Routine ⇄ Examination, role-gated and audited per NFR 23.3); zones with nesting and heterogeneous membership; device references with vendor identity and observation time; and the **`FacilityAsset` register** — the fixed plant S153 raises work orders against, distinct from AVAMP-Lite's cross-programme asset identity and linked to it by value.

**Readiness engine:** runtime-configurable versioned checklists applicable by space type and operating mode; append-only assessments; blockers from checklist items, assets and manual raises; the examination readiness lock. The invariant everything serves — *a space cannot be marked READY while a critical blocker is open* — is enforced on both the derived and the manual path through one `ReadinessPolicy`. An asset going out of service raises a blocker at a severity derived from its criticality and blocks the space it serves; its recovery clears it.

**Dashboard (S152-05):** readiness by status, blockers by severity, unavailable spaces, examination-readiness risk, stale readiness with a configured freshness threshold, and maintenance-linked risk through a port the maintenance module implements. Computed live from source records so counts reconcile; snapshot tables exist for go-live reporting.

API: 49 paths under `/api/v1/facilities`. Persistence: `V5__facilities_platform_foundation.sql`, `V6__facilities_estate_model.sql`, `V7__facilities_readiness.sql`, `V8__facilities_dashboard_snapshots.sql`. Tests: 147, covering the ten mandatory S152 scenarios, the readiness rules, the audit chain's tamper detection, the permission matrix, twelve WebMvc contract tests and nine ArchUnit boundary rules.

**Verified by running it, not only by testing it.** 135 tests were green while the service could not start. Dropping the schema and running against a real PostgreSQL found eight defects invisible to in-memory doubles — `CHAR(64)` columns Hibernate rejects, two unannotated constructors that had never let this service boot, blockers written before the row they reference, and — twice over — an audit chain that replayed as tampered because `jsonb` reorders keys and because nanosecond timestamps are stored as microseconds. All fixed; all recorded in `docs/facilities/S152_Gap_And_Conflict_Report.md` §8a, which also flags that `sfl-fleet-logistics-service` likely has the last two. **The lesson for S153 and S159: run the service against a database as part of the build, not after it.**

### Pass — S152 CAFM/IWMS dashboard module (`frontend/sfl-operations-ui`)
Built the S152 UI in the shared operations dashboard: **fifteen screens** in `src/modules/facilities` across three navigation sections — Facility operations (dashboard, readiness assessments), Estate registers (sites, spaces, facility assets, zones, device references), Facility assurance (readiness checklists, audit & integrity, configuration). Every item carries its real service permission, so the sidebar narrows with the actor rather than offering screens the service will refuse.

**Service changes the UI forced.** The facilities service's five S152 controllers returned bare payloads while 27 fleet and 8 emergency controllers returned `ApiResponse<T>`; the five outliers were changed rather than teaching the shared client a per-service policy, and `/actor/permissions` was flattened to `string[]` for the same reason. The two pre-S152 maintenance controllers were finished in the same pass. CORS allowed neither `8093` (bundled dashboard) nor `5005` (`npm run dev`) and did not expose `X-Correlation-ID` — every screen would have failed in a browser while every equivalent `curl` succeeded.

**ADR 0006 decision 3 discharged.** That decision kept `sfl-facilities-service`'s static page *only* until IFIMP had dashboard screens. It now redirects to `/ui/facilities` like the other four, its 861 lines of stylesheet and script are deleted, and its `index.html` is a notice page. Fault reporting and work orders were the one real capability lost — they are S153 and have no screens — which the notice page and the gap report both say plainly rather than gloss.

**Verified by driving it, not only by testing it.** A green build, a green typecheck and 44 green tests hid a module-wide defect: `shared/layout/actorPermissions.ts` asked only fleet and emergency, and its fail-open is per-*set*, so every `FACILITIES_*` permission evaluated **denied** — all S152 controls silently absent, no error anywhere. Found by clicking a row. In the browser against real PostgreSQL: a passing re-assessment closed the critical blocker it superseded; a critical generator set `OUT_OF_SERVICE` blocked its hall while the checklist score stayed at 100%; READY was refused in both layers with a readable message; returning the asset to service returned the space to READY; a `FLEET_MANAGER` saw no facilities sections and an `IFIMP_TECHNICIAN` no operating-mode control and a disabled lock. Recorded in `docs/facilities/S152_UI_Gap_Report.md` and `S152_UI_Screen_Inventory.md`. **The lesson for S153 and S159: adding a module means adding its permissions source — and driving the screens, not only rendering them.**

### Pass — S153 CMMS (`sfl-facilities-service`)
`SRS-SFL-S153-01..05`, NFR 23.1, 23.3, 23.5, 23.8. **A rewrite, not a greenfield build.** The maintenance spine predated S152 and inherited none of its platform, and three things were wrong with it: `FacilityFaultController.findAll()` had **no permission check and no site filter**, so any caller with any role or none received every fault at every site; the controllers read `X-SFL-User` directly through a second actor model that no JWT path touches; and no fault or work-order state change was hash-chained, so the integrity check verified a chain with holes in it and reported `intact: true`.

**Built on the platform rather than beside it:** six aggregates (`FacilityFault`, `WorkOrder`, `PreventiveMaintenanceSchedule`, `MaintenanceVendor`, `MaintenanceEvidence`, `WorkOrderPart`) with `RecordMetadata`, lifecycle, optimistic locking and real state machines; SLA timers computed from effective-dated runtime configuration, site operating mode and a vendor's contracted response time, whichever is tighter; an escalation ladder evaluated against the configuration **active at the moment of evaluation**, as S153-02 requires; preventive schedules that generate work idempotently by cycle and, on closure, write `lastServicedOn` back to the asset — closing the loop S152 left open; closure evidence by reference with a SHA-256, a mandatory retention class, legal hold, and export as a separate authorised act with a recorded reason and recipient.

**The join that puts S153 under S152:** a fault at or above a configurable priority raises a readiness blocker on the space it is in, and resolving it clears the blocker and re-derives the space. The port is declared **by readiness** and implemented by readiness, the opposite of S152's `SpaceReadinessPort`, because readiness is the deeper module — a hall's usability is a fact about the estate, and maintenance is one of several things that can change it. An ArchUnit rule holds that direction, and the `maintenance` exclusion S152 recorded as debt is deleted along with the JPA types the application layer used to name.

**Authorisation, deliberately narrowed.** `VENDOR_TECHNICIAN` was the same permission set as an in-house technician, which let a contractor read the whole estate register and every fault at the site; it is now split, and the real boundary is **assignment**, enforced per record on reads and writes alike because "the ones assigned to me" is not something a matrix can say. A requester reads only the faults they reported. A technician marks work complete and a supervisor accepts it — no technician holds `FACILITIES_WORK_ORDER_CLOSE`.

**Verified by running it.** Four design defects were found by writing the acceptance tests: closure was unreachable from `IN_PROGRESS` (which would have made every migrated row unclosable); a technician could reopen their own completed work; `resolve` and `dismiss` cleared the blocker flag *before* the reconciliation that reads it, leaving a rejected fault's blocker open forever on a hall nobody could book; and the sweep escalated a fault and its work order, notifying two people about one problem. Then a database was built at V8, seeded with rows in the old shape, and V9 run over it: provenance backfilled from the reporter, `HALL-A` linked to its room and `CAR-PARK-B` correctly left unlinked, historic orders left closable, and the whole workflow driven end to end — blocker raised and cleared, evidence gate refused and satisfied, generation and escalation both idempotent, a live configuration change applied to the next triage, and the audit chain verifying intact with 21 records including every S153 action. Tests: **190**, 12 skipped. Docs: `docs/facilities/S153_CMMS_Design.md`, `S153_Gap_And_Conflict_Report.md`, `S153_API_Reference.md`.

**One consequence for go-live:** faults migrated open have no SLA and will not escalate until re-triaged. Back-dating deadlines would have produced a wall of instant breaches on day one; the cost is a triage pass at cutover, and it is on the record rather than in the code.

### Pass — S153 CMMS dashboard module (`frontend/sfl-operations-ui`)
Nine screens and ten dialogs extending `src/modules/facilities` — the same service, client and envelope as S152 rather than a parallel module. Fault register and detail, work-order queue and detail, preventive schedules and detail, vendors, evidence detail, and open faults on the S152 space page. One new navigation section, **Maintenance**, each item gated on its real service permission.

**The capability ADR 0006 gave up is back.** Retiring the static facilities page cost CLET its fault register and work-order controls, recorded in `S152_UI_Gap_Report.md` §2.1 as the one real loss. That section is now closed, and what replaced it carries the SLA, escalation, preventive maintenance and closure evidence the old page never had.

**S153 became its own `SystemCode`**, and the reasoning is recorded at the declaration because the obvious reading of two codes that always move together is that one is redundant: entitlement is identical to S152 for every role today, because the permission matrix puts fault and work-order reads in its shared read-only set. It is separate because the C9 mapping treats S153 as a Fast-Track system, the coverage claims count systems, `VITE_SFL_SYSTEMS=S153` makes maintenance viewable in isolation, and a refusal page should name the right thing.

**Nothing the service derives is recomputed.** `overdue`, `minutesOverdue`, `assignable`, `dueForGeneration`, `disposalEligibleFrom` and `supportsClosure` all come down the wire — a browser working out for itself what is late would disagree with the escalation sweep the moment a workstation clock drifted, and the sweep is the one that notifies people. The single piece of client-side arrangement is the queue's overdue-first sort.

**Two defects found by driving it, both invisible to a green build and 73 green tests.** A technician was shown a Close button disabled with "You do not have permission" — permanent, on every job, forever — because the page disabled rather than hid on a permission denial. The fix draws a line the module now follows: *a permission denial hides the control, a state or data shortfall disables it with the reason*. And two empty states claimed "every work order at this site is closed", which is a confident falsehood for a contractor who sees only their own; both now describe what is visible to you rather than what exists.

**Verified end to end against real PostgreSQL, as four actors.** A supervisor reported, triaged, raised, assigned, attached evidence and closed — and watched HALL-A go BLOCKED → READY with the fault resolved and its blocker cleared. Closure was refused first with the service's own sentence and allowed after the evidence. A technician could complete but not close; a vendor saw exactly the one order assigned to them while another vendor saw none; a requester saw only their own fault. Tests: **73**, up from 44. Docs: `docs/facilities/S153_UI_Screen_Inventory.md`, `S153_UI_Gap_Report.md`.

**IFIMP is now complete for S152 and S153.** Six of the thirteen Phase 1 systems have screens; S159 room and resource booking is what remains in this programme.

### Pass — S159 Room and Resource Booking (`sfl-facilities-service`)
`SRS-SFL-S159-01..03`, NFR 23.3, 23.8. The third IFIMP system, built on the S152 platform alongside S153 — same estate register, audit chain, idempotency store, runtime configuration and permission matrix. Six tables, five application services, four controllers, and one database constraint that is the reason the module works.

**The rule that cannot be enforced in Java.** A space cannot be double-booked, and a read-then-write check cannot guarantee it: two requests can both read an empty diary before either writes. The guarantee is a PostgreSQL `GIST` exclusion constraint — `EXCLUDE USING gist (room_id WITH =, tstzrange(occupied_from, occupied_to, '[)') WITH &&) WHERE (status IN ('REQUESTED','CONFIRMED','IN_USE'))`. The application check is kept because it produces the message a requester can act on, naming the booking that has the hall; the constraint is what makes the rule true. `S159MandatoryScenariosTest` reads `V10` off the classpath and asserts the constraint's status list matches `BookingStatus.holdsTheSpace()`, because those are two expressions of one rule in two languages with no compiler between them.

**Half-open intervals, `[start, end)`, decided once and applied in three places.** A booking ending at ten and one starting at ten do not clash. Wrong one way, every back-to-back lecture reports a phantom conflict until people stop trusting the check; wrong the other, the hall is double-booked on the hour — which is exactly when lectures change over. Conflict is tested on the *occupied* window, widened by setup and teardown buffers, so the next booking cannot start while the chairs are still being moved.

**Three decisions that shaped the module.** A **request holds the space** rather than waiting for approval, so the second person to ask is refused now instead of three people planning around one hall and the approver arbitrating a clash. There is **no `APPROVED` state** — approval is an event recorded as a `BookingApproval`, and the absence of one is what records that a booking needed none. A **readiness hold is a flag beside the status, not a state**: a confirmed booking on a hall blocked on Tuesday is still a confirmed booking somebody has in their diary, and moving it to `AT_RISK` would decide on the estate's behalf that Tuesday's leak will still be there on Friday.

**Setup tasks are deliberately not S153 work orders.** The obvious move buys the queue, the SLA and the closure evidence for free; it also puts a twenty-minute chair rearrangement in the same queue as a failed standby generator, and the generator ends up on page four.

**Verified by running it, and it found two defects 290 green tests could not.** Every booking search returned HTTP 500 — `could not determine data type of parameter $11` — because the codebase's `(:p is null or column = :p)` idiom does not work for a null `Instant` on PostgreSQL: `IS NULL` gives the planner no type and pgjdbc sends `UNSPECIFIED`. And sixteen simultaneous requests for one hall produced one booking and **fifteen HTTP 500s**: the constraint held perfectly, but the losers hit a deadlock (`SQLSTATE 40P01`) rather than a constraint violation, because two transactions each insert then each wait on the other's uncommitted row. A per-space transaction-scoped advisory lock taken before the conflict check turns that into one booking and fifteen readable `BOOKING_CONFLICT` refusals; the deadlock translation stays as a backstop.

Also proven against a real database: `V1..V10` on an empty schema, `btree_gist` installable by the application user, Hibernate `validate` passing, both exclusion constraints refusing overlaps and accepting back-to-back pairs at the SQL level, the examination buffer blocking the slot straight after a paper, a requester seeing only their own bookings, an override refused without the permission and recorded with it, the reconciliation sweep placing four holds without changing a status, the no-show sweep releasing a hall and writing the room-time lost, and the audit chain verifying intact.

Tests: **290**, 61 of them S159, 12 skipped. API: 25 paths. Persistence: `V10__room_and_resource_booking.sql`. Docs: `docs/facilities/S159_Booking_Design.md`, `S159_API_Reference.md`, `S159_Gap_And_Conflict_Report.md`.

**IFIMP's backend is complete: S152, S153 and S159.** S159 has no screens yet — that is the next pass, and the two lessons above it still apply: add the module to the permissions source, and drive the screens rather than only rendering them.

### Pass — Record scope in FTLMP, and the mandatory-scenario suites that had never run

`SRS-SFL-S166-01`, `SRS-SFL-S168fuel-01..03`. Two findings, and the second is why the first survived so long.

**101 of 102 skipped tests now execute.** The suites that prove the SRS mandatory scenarios for S166, S168_fuel, S171 and S174 were gated on Testcontainers, and Testcontainers asks whether the **Java** Docker client can reach the daemon. On Windows it cannot — the named-pipe transport fails while `docker ps` works perfectly from a shell — so every one of those tests skipped on every run, in the only environment this platform is developed in. The suites were not failing; they were not running, and a skip reads as a pass in a summary line.

The fleet and emergency suites already had an external-database escape hatch (`SFL_*_TEST_DB_URL`) that nothing was using. Pointing it at the e2e containers already running on 55441–55445 turned 89 skips into passes with no code change at all. `FacilitiesMigrationIntegrationTest` had no such hatch, so it gained one in `FacilitiesPostgresSupport`, modelled on the fleet original — with the constraint that the external database must be **empty**, because that suite asserts absolute facts about a virgin schema and Testcontainers had been supplying the emptiness implicitly. Backend now runs **744 tests, 0 failures, 1 skipped**, where it ran 641 with 102 skipped.

Running them found one order-dependent test, not a product defect: the V7 checklist-seed assertion multiplied the site count *as it stands now*, when V7 seeds only the sites that existed *when it ran* — so it passed or failed on JUnit method order. It now asserts what its own name says: two checklists per seeded site, and no checklist referencing a site that does not exist.

**A record-scope rule that was written down, tested, and enforced nowhere.** `FleetAccessPolicy.requireRecordScope` carries the javadoc "this is what keeps the limited driver/mobile user class to their own trips and inspections", and `FleetAccessPolicyTest` proves it works. It had exactly one production call site, in `TripApplicationService`, under the comment "a driver may only inspect the vehicle on their own trip" — and it was passed `null` as the owner reference, which the policy returns on immediately at its null guard. The control was a no-op at the only place it was invoked, and no read called it at all, so a `FLEET_DRIVER` holding any trip id read that trip in full.

Fuel had the same shape one layer down. `FuelAccessPolicy.isDriverOnly` narrows the logbook **list** in SQL on `created_by`, guards creation against the trip's driver reference, and guards transitions — but `logbook(id, actor)` checked permission and site only. A driver holding a colleague's logbook id read journey, route, purpose and passenger notes through the detail endpoint. **A narrowing the collection obeys and the record does not is decorative:** the row still crosses the boundary, one at a time instead of in a page. Both ownership refusals also threw `IllegalStateException`, reaching the caller as a 500 and leaving no denial in the audit chain; they are now `FleetAuthorizationException`, which is the SRS's 403 envelope and is audited.

The join between the two identity models is the driver's `staffReference` — the value an actor signs in as — which fuel already relied on when refusing a driver a logbook opened for somebody else. Trips carry `driverId`, a register key, so `TripQueryService` and `TripApplicationService` resolve one to the other rather than inventing a second notion of ownership. A supervising `FLEET_TRIP_MANAGE` passes through, so the narrowing binds the driver and not the fleet office.

**Dispatch is deliberately not narrowed, and that is recorded rather than guessed.** `CENTRE_MANAGER` and `MAILROOM_OFFICER` read the whole dispatch register, and the obvious fix does not work: `Dispatch.destinationCentre` and `assignedHandler` are `VARCHAR(200)` free text supplied at creation, with no relationship to a principal. Narrowing on them would produce a rule that holds whenever somebody happened to type an actor id and silently fails otherwise — worse than no rule, because it looks like enforcement. Closing it properly needs a principal-bound centre or handler reference on the dispatch, which is a schema change and an identity decision for the Transportation & Logistics Unit. This is the same shape as S153's recorded choice to narrow vendors per person rather than per firm, and it is left in the same state: written down, owner named, not invented.

Proved by refusal **by id**, never by an absent row: a driver reads their own trip and is refused another's, a driver is refused a colleague's logbook and its transition, and an officer is unaffected by either.

### Pass — The audit chain that never verified, the events that never left, and the runbooks that did not exist

`SRS-SFL-S166-03`, `SRS-SFL-S152-04`, `SRS-SFL-S153-04`, `SRS-SFL-S159-04`, PLAT-01..05. Four pieces of platform work that had one thing in common: each was believed done because nothing was checking.

**The audit chain was permanently reporting tampered, and nobody had looked.** S152 §8a recorded two defects that broke the facilities chain and warned in writing that fleet was likely to carry both — "it should be checked before S166 is relied on for evidence." It was never checked. Checking it took one test and it came back `intact=false`, diverging at sequence 8 of 201.

The cause is S152's D-05 verbatim: `AuditHashChain` hashes `occurredAt.toString()`, the JVM clock yields nanoseconds and `timestamptz` stores microseconds, so the value hashed was never the value read back. The boundary is exactly visible in the data — sequence 7, written by the tests' fixed clock at whole seconds, verifies; sequence 8, the first record written by a real clock, does not. **In production that is every record.** It survived four build passes because every chain-intact assertion in the suite uses an in-memory double, which round-trips nothing and so cannot see a storage-precision defect at all, and because a fixed clock at whole seconds truncates to itself. The one end-to-end check asserted `isNotNull()` rather than `intact()`. `JpaAuditAdapter.storedPrecision()` truncates to microseconds; `FleetAuditChainPostgresTest` replays the whole chain off PostgreSQL and fails on the old code.

S152's other defect, D-04, does **not** apply here: fleet neutralises jsonb's key reordering by re-canonicalising on read where facilities solved it by storing `TEXT`. Both are correct and the difference is recorded so nobody unifies them on sight.

**Pre-fix records cannot be repaired**, because a hash chain has no mechanism for amending history — that is the property it exists to provide. The e2e database was truncated and restarted at genesis; a production environment would have to choose that or a documented divergence point, and either is survivable only if it is on the record. No production environment exists yet, so the requirement is simply that the first production record is written by the fixed code.

**IFIMP recorded events and published none of them.** `infrastructure/messaging` was an empty package, so S152, S153 and S159 wrote outbox rows inside the business transaction — correctly — and nothing ever drained them. Three gap reports describe the consequence in different words and they are one gap: no escalation, no booking decision and no readiness hold ever reached a person. `FacilitiesOutboxDrainer` closes it, claiming one message per transaction with `FOR UPDATE SKIP LOCKED`, backing off exponentially through the `next_attempt_at` column V5 added for exactly this and never used, and dead-lettering after N so one poison payload cannot hold up the queue behind it. Driven through a `TransactionTemplate` rather than `@Transactional` on a private method, because Spring proxies do not intercept self-invocation and an annotation there would look like a transaction boundary while being nothing of the kind — the claim and the settle have to be in one transaction or the row lock is released before the message is sent.

**The event names could not be bound.** Facilities published `ifimp.work-order.assigned` — no `sfl.` prefix, no `.v1` suffix, and a dot inside the event name where the catalogue allows hyphens only — and AVAMP published `sfl.asset.*` with the wrong platform token. A consumer binding `sfl.ifimp.*.v1` would have received nothing and had no way to distinguish that from a quiet week. Forty-eight literals across fifty call sites are renamed, and both services now validate the name **at the outbox write path**, because a list of known names has to be remembered and a write path cannot be avoided. The version in the name and the `event_version` column are checked against each other, so a consumer that binds on the routing key and one that reads the column cannot disagree.

**`docs/runbooks/` was an empty directory.** Four runbooks now exist — dead-letter recovery, incident response, backup and restore, disaster recovery — written from what the code does rather than from a template, including the traps this platform has actually hit: the `next_attempt_at` window that makes a healthy drainer look stalled, the `CHAR(n)` validation failure, the two unannotated constructors, and the restore that splits the audit table from its chain head and reports as an attack. The DR runbook states plainly that the procedure has never been rehearsed.

**ADR 0007 takes the RLS decision** that S166 C-09 left open across four passes: the per-request session GUC, failing closed, sequenced deliberately *after* authentication — because RLS enforcing scopes asserted by an unauthenticated caller is theatre.

Backend: **760 tests, 0 failures, 1 skipped**, where before this sequence it ran 641 with 102 skipped.

### Pass — Role-based portals for the stakeholders the SRS names

`SFL_SRS.docx` §2.3 User Classes, `CLET_Comprehensive_Digital_System_Mapping_v2.docx` §30A.6.9. Five modules existed and every one was built for the operator — Facilities Manager, Fleet/Logistics Officer, Emergency Coordinator. Eight roles held real permissions with no view designed for them, landed on somebody else's dashboard, and saw mostly-hidden controls, which reads as a broken build rather than as a role boundary.

**The trace matrix came before the code**, all 26 roles with one row each, and the permission counts were read from the four matrices by asking them rather than by transcribing: counting 145 permissions by hand is how a matrix and a portal drift apart. Eleven roles trace cleanly to a §2.3 class, six derive from one within a single system, and **three are Deviations that are recorded rather than invented** — including `SERVICE_INTEGRATION`, which is a machine principal and is listed precisely so nobody later reads its absence as an oversight.

**The problem a permission cannot solve.** `FLEET_DRIVER` holds eight permissions and **every one of them is also held by `FLEET_MANAGER`**. So no permission distinguishes a driver from the fleet office, and gating "My driving day" on `FUEL_LOGBOOK_CREATE` would have offered it to the manager as their landing page. What makes somebody a driver is not what they can do — it is what they cannot. `shared/layout/personas.ts` encodes that, and it transcribes rather than invents: the narrowest-role rule is exactly the one `FuelAccessPolicy.isDriverOnly` and `FacilityFaultService.requesterFilter` already enforce, down to S153's stated reason that "treating the union of roles as its narrowest member would make adding a role to somebody take capability away". It is **not** an authorisation check and the file says so — nothing there hides data, the services do that per record, and a wrong answer costs a click rather than a disclosure.

**Placement is the whole mechanism.** `landingPath()` returns the first item of the first entitled section, so putting the personal sections first in `navSections` is what makes a driver open on their own day rather than on a fleet dashboard — with no change to the router, the shell or the route guards, and no effect on operators, because every personal item is persona-gated.

**Two portals could not be built honestly, and say so on the page.** `CENTRE_MANAGER` has no way to know which consignments are its own: `destinationCentre` and `assignedHandler` are free text with no principal binding, and a rule built on them would hold whenever somebody happened to type an actor id and fail silently otherwise — worse than no rule, because it looks like enforcement. The screen lists consignments *at this site*, says so twice, and names the owner of the schema decision. And the driver's fuel panel is labelled "recorded at this site" rather than "mine", because `FUEL_TRANSACTION_READ` is not narrowed per record; calling it mine over a list containing a colleague's fill would be a lie the screen tells on the service's behalf.

**Recorded rather than built:** dispatch controller and logistics coordinator get no new portal, because both hold the full controller set and the dispatch module already is their view — duplicating fifteen screens to change a title buys nothing. Command, reporting-viewer and administrator landings are honest today and not designed, which is the next slice rather than a claim. And verification under authentication is owed: A1 was deferred, so the actor still arrives in a header, and the persona rule reads roles from it exactly as it will read them from a JWT claim.

Frontend: **84 tests**, up from 73, with eleven pinning the persona rule including every case where a persona must *not* apply. Docs: `docs/frontend/SFL_Role_Portal_Trace_Matrix.md`, `SFL_Role_Portal_Gap_Report.md`, and the portal pattern added to the module playbook.

### Pass — Authentication on by default

PLAT-01, `solution.md` §Security, ADR 0007. **Every API in this platform was unauthenticated, and an environment that simply forgot a variable stayed that way.**

`sfl.security.enabled` defaulted to `false`, and — the half that mattered more — the filter chain that permits everything carried `matchIfMissing = true`. So two independent things both had to go right for a deployment to be secure, and neither was the default. Both are inverted: the open chain now requires the property to be *explicitly* false, the secure chain is what an absent property selects, and taking the open path logs a warning naming the service on every startup. The local development scripts set the variable deliberately and carry a comment saying the line is now load-bearing.

**Rather less was missing than the gap report implied, and one thing more.** The resource server, both filter chains and the JWT actor resolvers in facilities, fleet and emergency were all already written — the Prompt 1 note that Keycloak was absent from compose was wrong, it was there with the issuer wired and a `depends_on`. What was actually missing was the **realm**: `start-dev` with no import means `/realms/sfl` does not exist, which under the old default degraded quietly and under the new one is a startup failure. `deploy/keycloak/sfl-realm.json` now carries all 26 `SflRole` values, the `site_scopes` mapper the resolvers already read, a public client for the dashboard, a service-account client for signed ingest, and one user per persona so a portal can be signed into rather than only reasoned about. Compose imports it and health-gates the services on the realm answering, not merely on the port opening.

**The thing more.** `sfl-asset-visibility-service` took its actor as `@RequestHeader(name = "X-SFL-User", defaultValue = "development-user")` on every controller method and passed it straight into the command. Harmless while the header *is* the identity; not harmless the moment a verified principal exists, because the service would still have attributed every asset registration, custody change and evidence link to whatever string the caller chose — or, absent one, to a user literally called `development-user`. It now resolves the JWT subject first and falls back to the header only when there is no authenticated principal, which is what the other three have always done.

**The chain that faces every real user had never been executed.** Searching every `src/test/java` in the reactor for `keycloakSecurity`, `sfl.security.enabled=true` or `JwtAuthenticationToken` returned nothing across four build passes, while the suite reported green off the development chain that permits everything — the same shape as the skipped mandatory scenarios and the audit chain that always replayed as tampered. `FacilitiesJwtSecurityTest` runs the real chain in a real context and pins five things: anonymous is refused rather than served as `development-user`; an `X-SFL-User` header cannot assert an identity while the chain is armed; a token is admitted and its realm roles reach the actor; a token whose roles lack the permission gets **403 rather than 401**, because "who are you" and "you may not" are different answers; and the health probe stays reachable, because a load balancer cannot present a token. It had to be a full context rather than a slice — a slice has no `HttpSecurity` for a chain to be built on, which is exactly why the existing controller tests exclude the resource server and disable filters.

**ADR 0007 is unblocked.** It sequenced row-level security behind this deliberately, on the grounds that RLS enforcing scopes asserted by an unauthenticated caller is theatre. The `site_scopes` claim those policies will read is now issued by the realm and consumed by every resolver.

Backend: **772 tests, 0 failures, 0 skipped.**

### Pass — Row-level security (ADR 0007)

Site scope had one layer of enforcement, and it was the layer this platform has twice been observed to get wrong: `FacilityFaultController.findAll()` once returned every fault at every site to any caller, and `FleetAccessPolicy.requireRecordScope` was passed `null` at its only call site and enforced nothing. Both were found by reading code, not by a test failing. RLS is the layer that makes that class of mistake harmless, and A1 unblocked it — enforcing scopes asserted by an unauthenticated caller would have been theatre.

**Built as ADR 0007 decided, plus one thing it did not name.** The per-request session GUC is there: `SiteScopeGuc` in the shared kernel issues `SET LOCAL app.site_scopes` in `afterBegin`. `SET LOCAL` rather than `SET` is the entire safety argument — it rolls back with the transaction either way, so a pooled connection never carries a stranger's scopes to its next borrower, which is the failure this design is usually accused of.

The addition is a **role split**, and it was forced by a real hazard. A table owner bypasses RLS unless `FORCE` is set, and `FORCE` would have applied the policies to Flyway — so a migration that backfills would have silently written nothing, which is worse than the problem and invisible. The owner therefore keeps its bypass and runs migrations, and a separate `sfl_app` role carries the policies. Development and the whole test suite keep connecting as the owner, so nothing that worked stopped working, and adopting RLS in an environment is a connection-string change rather than a deployment that must land in lockstep with a migration.

**The policies fail closed.** `site_in_scope` returns false when the GUC is unset or empty. That is the only setting worth having: a second layer that opens up when the first forgets to speak is not a second layer. `*` is the cross-site scope, matching `SiteScopeFilter.all()`. Two tables are exempt and the reason travels with the rule — the audit chain, because a tamper-evident record that is invisible in parts replays as a break that is really a filter; and runtime configuration, because it is read during evaluation for sites the actor may not hold and narrowing it would make an SLA silently unresolvable rather than refused.

**Proved against the role it applies to.** `FacilitiesRowLevelSecurityTest` opens its own connection as `sfl_app`, because a test running as the owner would have passed while proving nothing at all. Six cases, including that a write outside scope is refused by `WITH CHECK` with SQLSTATE 42501 rather than silently dropped.

Facilities is the reference implementation; the same migration is owed against the other three schemas, and the mechanism is already shared. Backend: **778 tests, 0 failures, 0 skipped.**

### Pass — The S159 booking UI

S159 shipped with twenty-five API paths and no client. This is the client: five screens, seven
dialogs, and the first IFIMP module whose route base does not mirror its service — `/bookings`, not
`/facilities/bookings`, because a lecturer booking a hall does not think of themselves as visiting
facilities and a URL somebody can be told over the phone is worth more than one that mirrors
deployment topology.

**Two mistakes were made in the first draft and both were caught by reading the service rather than
by a failing test**, which is the same way the last four passes found what they found.

`FACILITIES_BOOKING_CANCEL` does not mean "may cancel". `requireMayAct` uses it as the *"may act on
somebody else's booking"* grant and routes cancel, reschedule, start and completion through it
identically — so anything you requested you may move, start, complete and cancel holding nothing
beyond `FACILITIES_BOOKING_REQUEST`, and anything you did not you may touch only holding that one
grant. The first draft gated reschedule and start on `BOOKING_REQUEST`, which is the reading the
names invite and which would have offered a requester the Move button on a hall booked by the
registry. And the turnaround queue was gated on `FACILITIES_SETUP_TASK_MANAGE`; `BookingSetupService.queue`
gates the read on `FACILITIES_BOOKING_READ` and reserves the manage permission for raising and
resolving a task, so shipping it as written would have hidden the queue from everybody who can only
look at it — while the technicians who can resolve tasks saw it fine, so it would have looked correct
to whoever tested it.

**The occupied window is the thing every screen exists to make visible.** A lecture booked 09:00–11:00
with a fifteen-minute teardown refuses a meeting at 11:05, and the refusal names the *booked* window
in its message — so somebody reads 11:00, asked for 11:05, and is refused. The screens do not rewrite
the service's wording; they show the occupied window beside it, on the diary row, as its own stat card
on the detail page, and in the request dialog's description.

**Verified against PostgreSQL, not only against tests**, per the standing rule. A seeded site, two
rooms and one exclusive resource, then thirteen behaviours driven through the same paths the UI calls:
buffers widening the occupied window, the conflict landing on the buffer, setup tasks auto-raised at
the occupied start, completion releasing every allocation, the requester narrowing on both the list
and the by-id read, own-booking cancellation without `BOOKING_CANCEL`, the readiness override with a
recorded reason, and the self-approval refusal. Every field of every response matched the TypeScript
DTOs with no adjustment — they were transcribed from `BookingResponses` rather than inferred.

**One entitlement fact is worth stating because it happened silently.** Adding `FACILITIES_BOOKING_READ`
to the facilities matrix's shared `READ_ONLY` set entitled ten roles to the room diary and left
`VENDOR_TECHNICIAN` out — correctly, and only because a contractor's matrix entry is an explicit
`EnumSet` rather than a union with that set. A test now pins it, so rebuilding `VENDOR_TECHNICIAN` on
`READ_ONLY` cannot hand a contractor the estate's diary by accident.

Recorded rather than built, in `docs/facilities/S159_UI_Gap_Report.md`: the calendar grid (a half-grid
drawing the booked window would actively mislead), post-hoc resource allocation, resource editing, and
manual setup tasks. Each has an endpoint and no control, and each is named with the reason.

Frontend: **115 tests**, up from 84.

---

*Going forward, every new pass follows the API-First Build Recipe, references its `SRS-SFL-*` IDs, and updates the Workplan §15 backlog.*
