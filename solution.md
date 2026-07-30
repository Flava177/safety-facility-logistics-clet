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
Persistence/migrations: `V1__service_foundation.sql` (schema, metadata, outbox, inbox/idempotency), `V2__facilities_master_data.sql`, `V3__facility_faults.sql`. Eventing records service-local integration events in `facilities.outbox_messages` via the `ServiceOutbox` port (no writes to old monolith schemas). A lightweight service landing page exists at `/` for dev visibility only.

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

---

*Going forward, every new pass follows the API-First Build Recipe, references its `SRS-SFL-*` IDs, and updates the Workplan §15 backlog.*
