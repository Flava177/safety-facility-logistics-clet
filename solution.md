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

---

*Going forward, every new pass follows the API-First Build Recipe, references its `SRS-SFL-*` IDs, and updates the Workplan §15 backlog.*
