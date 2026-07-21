# SFL Phase 1 — Technical Implementation Workplan

**Council for Legal Education and Training (CLET) — Cluster 9: Safety, Facilities & Logistics**
Version 1.0 · July 2026 · DTI / F&L
Drives implementation of: *CLET Cluster 9 SFL Phase 1 SRS v1.0* (the 13 Fast-Track systems).

---

## 0. Purpose and how to read this document

This workplan turns the **SRS** into a build sequence for the **four SFL Spring Boot microservices**. It follows the same delivery discipline that produced the S074 Comms System — **API-first, contract-driven, outbox-backed, adapter-isolated** — but implements each service as a **Clean / Hexagonal Architecture** (Ports & Adapters) modular service.

Three things are authoritative and must not drift:

1. **The SRS is the contract.** Every module, endpoint, event and acceptance test traces to an `SRS-SFL-*` requirement ID. Section 15 is the requirement→slice backlog.
2. **The comms-service is the reference pattern.** We mirror its *shape* (contract DTOs → 202 Accepted → fast/deferred processing → transactional audit outbox → runtime-resolved adapter registry → OIDC/JWT + permission checks → emergency fast-lane), re-expressed in idiomatic Java/Spring with hexagonal layering.
3. **The dependency rule is inviolable.** `api → application → domain`; `infrastructure → application/domain`. The **domain layer depends on nothing framework-specific**. Vendors, brokers, identity providers and databases live behind ports, in exactly one adapter each.

> Naming note: package bases follow the existing repo convention `gh.edu.clet.sfl.<service>.<feature>.<layer>` (e.g. `gh.edu.clet.sfl.facilities.maintenance.domain`). Where a service's base word is not yet fixed in code, use the service's domain word (`facilities`, `safetysecurity`, `fleetlogistics`, `assetvisibility`) and keep it consistent.

---

## 1. Architecture at a glance

### 1.1 The four deployable services (bounded contexts)

| Service | Platform (SRS Module) | Owns (Phase 1 SRS systems) | Schema |
|---|---|---|---|
| `sfl-facilities-service` | IFIMP (3.1) | S152 CAFM/IWMS, S153 CMMS, S159 Room & Resource Booking, hall-readiness | `facilities` |
| `sfl-safety-security-service` | SSEMP (3.2) | S160 Visitor, S160a Access Control, S161 CCTV, S162 Intrusion, S162a Life-Safety, S163 HSE, S174 Emergency Notification | `safety_security` |
| `sfl-fleet-logistics-service` | FTLMP (3.3) | S166 Fleet, S168_fuel Fuel & Logbooks, S171 Mailroom/Courier & Dispatch | `fleet_logistics` |
| `sfl-asset-visibility-service` | AVAMP-Lite (supporting) | Asset/device/location references for all services | `asset_visibility` |
| `sfl-service-common` | Cross-cutting (3.4) | Shared kernel: principal/RBAC, error envelope, event envelope, outbox/inbox contracts, integration-security primitives | *(library, no schema)* |

Rules from the SRS §2.6 and Module 4 that the topology enforces:
- **Schema per service. No cross-schema foreign keys, joins or views.** Reference other contexts by ID; resolve via their API or their published events.
- **No cross-service database transaction.** Each service commits its own state **plus** its outbox in one local transaction; cross-service consistency is eventual (events/sagas) with explicit compensation.
- **Own your IDs.** ULID/UUID minted in-process (also required for edge survivability).

### 1.2 Hexagonal layering (every service, every feature)

```
gh.edu.clet.sfl.<service>.<feature>.
  api/                 inbound adapter — REST controllers, request/response DTOs, Bean Validation,
                       principal mapping, HTTP status + error envelope, OpenAPI annotations
  application/
    command/           write use cases (one class per use case)  ── transaction boundary lives here
    query/             read use cases / read models
    port/              OUTBOUND ports (interfaces the app owns): repositories, EventPublisher,
                       AuditPort, VendorXAdapter, ClockPort, IdGenerator, IntegrationInbox
    workflow/          sagas / process managers (choreography state)
  domain/
    model/             aggregates, entities, value objects (no Spring/JPA/Jackson)
    event/             domain events
    policy/            invariants, status transitions, authorization predicates
  infrastructure/
    persistence/       JPA entities + Spring Data repositories implementing app repository ports
    messaging/         RabbitMQ publisher + outbox drainer + inbound consumers (implement ports)
    integration/       vendor adapters (CCTV/access/alarm/fire/fuel/notification/courier)
    security/          OIDC/JWT → SflPrincipal mapping, method authorization
    config/            Spring config, runtime-configuration provider (config-without-code)
```

**Dependency direction:** `api → application → domain`, and `infrastructure → application/domain`. Nothing points *into* `infrastructure`. The `domain` package imports no Spring, JPA, Jackson, RabbitMQ, Redis, HTTP or vendor types — enforced by ArchUnit (§12).

### 1.3 Ports & adapters catalogue

**Inbound adapters (drive the application):**
- REST controllers (`api/`) — synchronous commands/queries, return `202 Accepted` for async submits.
- RabbitMQ consumers (`infrastructure/messaging`) — react to integration events (own + other services').
- Vendor webhook receivers (`infrastructure/integration`) — device/vendor callbacks, secured per §9.

**Outbound ports (the application depends on; infrastructure implements):**

| Port (in `application/port`) | Adapter (in `infrastructure`) | SRS anchor |
|---|---|---|
| `<Aggregate>Repository` | JPA + Spring Data | data ownership §4 |
| `IntegrationEventPublisher` | Outbox writer + RabbitMQ drainer | PLAT-01, E-01 events |
| `AuditPort` | Hash-chained audit writer → S204 relay | PLAT-03 (tamper-evident) |
| `IntegrationInbox` | Inbox/idempotency store | PLAT-01 (idempotent consume) |
| `CctvVmsPort`, `AccessControlPort`, `IntrusionPort`, `FireLifeSafetyPort`, `NotificationGatewayPort`, `FuelProviderPort`, `TelematicsPort`, `CourierCarrierPort` | Vendor adapters behind IntegrationHub | Buy-and-Integrate §5, S160a/S161/S162/S162a/S174/S166/S168_fuel/S171 |
| `IdentityPort` / resource-server | OIDC/JWT resource server (provider-pluggable) | PLAT-02, S213 |
| `CacheStore` | Redis adapter (device-state, permission snapshot, dashboards) | edge/PLAT-05 |
| `ClockPort`, `IdGenerator` | System clock, ULID generator | PLAT-04 (edge IDs) |
| `RuntimeConfigPort` | DB-backed versioned config | PLAT-05 (config-without-code) |

### 1.4 Comms-service → SFL/Java pattern map

| Comms (Python/FastAPI) | SFL (Java/Spring, hexagonal) |
|---|---|
| Pydantic `NotificationSubmit` schema (contract, validation, idempotency_key) | `api` request DTO + Jakarta Bean Validation + `Idempotency-Key` header |
| FastAPI router `/v1/...`, `202 Accepted` | Spring MVC `@RestController` `/api/v1/...`, `ResponseEntity.accepted()` |
| `orchestrator.submit_notification` (fast phase, persist + 202) | `application/command` use case: validate + persist aggregate + outbox row in one tx, return id |
| `tasks/process_request` (deferred phase, network calls, retries) | RabbitMQ consumer or `@Scheduled` worker: MDM/vendor/consent calls, approval, dispatch, retry |
| `tasks/outbox_worker.flush_audit_outbox` (SKIP LOCKED, backoff, POISON) | `OutboxDrainer` `@Scheduled`: claim `FOR UPDATE SKIP LOCKED`, publish to `sfl.events`, backoff, DLQ |
| `services/provider_registry` (runtime ACTIVE config, fail-loud) | Vendor adapter registry: resolve ACTIVE integration config at call time, `IntegrationConfigNotFound` (never silent fallback) |
| `require_permission(...)` dependency | `AuthorizationPolicy.requireRole/requireSiteAccess` (already in `sfl-service-common`) + method security |
| Celery `comms-emergency` queue drained first | Dedicated **fast-lane** exchange/queue + consumer for life-safety/emergency (S162a/S174) |
| Startup provider-config assertion | Actuator startup check: refuse traffic if a required vendor integration is unconfigured in this env |
| `RequestContextMiddleware` correlation id | Servlet filter + MDC + OpenTelemetry `traceparent` in the event envelope |

---

## 2. Release 0 — Cross-cutting foundations (build before any feature module)

These land in `sfl-service-common` (shared) and a thin slice of each service, and are the enablers the SRS Module 4 requires. **No feature slice ships until Release 0 is green.**

### 2.1 `sfl-service-common` (shared kernel — keep minimal and stable)
Already present: `SflRole`, `SflPermission`, `SiteScopedPrincipal`, `ActorContext`, `AuthorizationPolicy` (+ tests). Add:
- **Shared kernel value objects** (SRS §4 domain model): `SiteCode`, `LocationRef`, `EntityId`, `Severity`, `WorkflowStatus`, `EvidenceReference`, `AuditActor`, `DateTimeRange`, `ApprovalDecision`, `Ulid`.
- **API error envelope**: `ApiError { code, message, correlationId, fieldErrors[] }` + shared `@RestControllerAdvice` base for validation/authorization/not-found → maps to the SRS "Error States" strings.
- **Integration event envelope** (event-catalog): `eventId, eventType, eventVersion, occurredAt, publishedAt, correlationId, causationId, siteCode, sourceModule, traceparent, payload`.
- **Outbox & Inbox contracts**: `OutboxMessage`, `InboxMessage`, `IntegrationEventPublisher`, `IntegrationInbox` interfaces + JSON canonicalisation helper (for audit hash + dedup keys).
- **Inbound-integration security primitives** (SRS 0F/§9): HMAC verifier, mTLS/allowlist guard, schema-validation hook, `RejectedInboundMessage` recorder.
- **Audit port + hash-chain** (SRS PLAT-03): `AuditPort.record(AuditEntry)`; `hash = H(prev_hash || canonical(record))`; insert-only writer role.

### 2.2 Per-service foundation slice
Each service already has `V1__service_foundation.sql` (schema + metadata + outbox + inbox). Extend/confirm:
- `outbox_messages` (id, aggregate_type, aggregate_id, event_type, event_version, payload jsonb, status, attempt_count, next_retry_at, created_at) — drained by `OutboxDrainer`.
- `inbox_messages` (event_id PK, source, received_at, processed_at, status) — idempotent consume dedup.
- `audit_records` (id, prev_hash, hash, actor, action, resource_type, resource_id, before jsonb, after jsonb, correlation_id, occurred_at) — insert-only.
- `runtime_config` (key, scope, version, value jsonb, effective_from, updated_by) — config-without-code.
- Spring wiring: OAuth2 resource-server (JWT), `OutboxDrainer` `@Scheduled`, RabbitMQ topology (`sfl.events`, `sfl.events.dlx`), Redis, Flyway, Actuator (`health`/`info`/`metrics`/`prometheus`), OpenTelemetry, `CorrelationIdFilter`.

### 2.3 Release 0 exit criteria (Definition of Done)
- [ ] `mvn -pl services -am verify` builds all services + common independently.
- [ ] ArchUnit rules pass: domain has no framework imports; modules reference only other modules' contracts; **DB lint fails the build on any cross-schema FK**.
- [ ] One end-to-end proof slice through the whole spine: `HTTP → command → aggregate → outbox → RabbitMQ → inbox consumer → audit hash-chain`, with a WebMvc contract test and a Testcontainers integration test.
- [ ] Inbound-webhook security proven (a forged/unsigned/wrong-IP payload is rejected and logged — commissioning test **CT-19**).
- [ ] Edge skeleton proven with one capability (local outbox + offline JWT validation via cached JWKS + permission snapshot) — **CT-17** scaffold.
- [ ] Tamper-evident audit proven (altering a stored row breaks chain replay — **CT-18** scaffold).

---

## 3. API-first, contract-driven slice recipe (do this for every requirement)

Every functional slice (one or a few `SRS-SFL-<system>-NN`) is built in this fixed order — the same "define the contract, then fulfil it" flow used for comms and already used for the AVAMP asset API:

1. **Contract** — write the OpenAPI operation(s) and the `api` request/response DTOs with Bean Validation. Derive fields and states directly from the requirement's *Requirements* and *Status Matrix*.
2. **Controller (stub)** — `@RestController` returning the right status (`202 Accepted` + resource id for async submits; `200/201` for sync) against a not-yet-implemented use case.
3. **Contract test** — a WebMvc slice test asserting the contract (happy path + the requirement's *Error States* envelope + authorization 403). This is the executable form of the *Acceptance Criteria*.
4. **Domain** — aggregate(s), value objects, domain events and `policy` (status transitions/invariants), with unit tests for each *Acceptance Criterion* that is a business rule.
5. **Application use case** — command/query orchestrating repository + domain + audit + outbox inside one transaction boundary; define any new outbound `port`.
6. **Persistence adapter + Flyway migration** — JPA entity + repository implementing the port; `V#__<slice>.sql` in the service's `db/migration`.
7. **Vendor/integration adapter (if Buy/Hybrid)** — implement the vendor port with a **simulator/fake first** (§5); real vendor wired only after contract tests pass.
8. **Async worker/consumer** — outbox drain publishes the requirement's events; inbox consumer(s) react idempotently (e.g. `camera-offline → raise IFIMP work order`).
9. **Read model / dashboard endpoint** — projection + query endpoint feeding the dashboards (SRS "Expose dashboards" requirements) and Analytics (S225).
10. **Authorization + site-scope + audit** — enforce `AuthorizationPolicy` on the command/query, apply site-scope filter (+ Postgres RLS), write the audit record.
11. **Integration + architecture tests** — Testcontainers (Postgres/RabbitMQ/Redis), saga tests where cross-service, ArchUnit gates.

**Contract conventions (all services):**
- URLs: `/api/v1/<domain>/<resource>` (e.g. `/api/v1/facilities/work-orders`, `/api/v1/security/visitors`, `/api/v1/fleet/trips`).
- Async submit: `202 Accepted` + `{ requestId }`; expose a **status query** (`GET .../{id}`) and, for source-system callers, a signed **callback** (mirrors comms A-04).
- `Idempotency-Key` header on all state-creating POSTs; dedup on it (mirrors comms).
- Uniform error envelope; every rejection message matches the SRS *Error States* text.
- Cursor/opaque pagination on list endpoints; `X-Correlation-ID` propagated end-to-end.

---

## 4. Per-service delivery plan

For each service: **current state → target packages → aggregates → API-first endpoint list → vendor adapters → events → migrations**. Endpoint lists are the API-first backlog; each row is a slice built per §3.

### 4.1 `sfl-facilities-service` — IFIMP (S152, S153, S159, readiness)

**Current state:** S152 master data (`masterdata`) and S153 faults + work orders (`maintenance`) implemented with domain + application + api + persistence and tests (`FacilitiesMasterDataTest`, `FacilityFaultTest`, `WorkOrderTest`, `WorkOrderServiceTest`); migrations `V1..V4`; Bootstrap ops console. Build **on** this.

**Target feature packages:** `masterdata` (S152), `spaces` (S152-05/06), `maintenance` (S153), `preventive` (S153-04), `materials` (S153-05/06), `booking` (S159), `readiness` (hall-readiness saga sink).

**Aggregates:** `Facility` (site→building→floor→room/hall→zone), `Space`, `DeviceLocationMap`, `WorkOrder`, `PreventiveMaintenancePlan`, `RoomBooking`, `ResourceReservation`, `ReadinessChecklist`.

| Endpoint (API-first) | Method | SRS |
|---|---|---|
| `/api/v1/facilities/locations` (+ `/{id}`, tree) | POST/GET/PATCH | S152-01 |
| `/api/v1/facilities/locations/{id}/iwms-sync` | POST | S152-02 |
| `/api/v1/facilities/spaces` (capacity, layout, permitted-use) | POST/GET/PATCH | S152-05 |
| `/api/v1/facilities/device-locations` | POST/GET/PATCH | S152-06 |
| `/api/v1/facilities/readiness/{spaceId}` (score, block reasons) | GET | S152-03 |
| `/api/v1/facilities/work-requests` (multi-source intake) | POST | S153-01 |
| `/api/v1/facilities/work-orders` (+ `/from-fault`, `/{id}/assignment`, `/{id}/closure`) | POST/PATCH/GET | S153-02/03 |
| `/api/v1/facilities/pm-plans` (+ `/{id}/generate`) | POST/GET | S153-04 |
| `/api/v1/facilities/work-orders/{id}/materials` | POST/GET | S153-05 |
| `/api/v1/facilities/assets/{id}/history` · `/readings` | GET/POST | S153-06 |
| `/api/v1/facilities/bookings` (+ `/{id}/approval`, `/{id}/outcome`) | POST/PATCH/GET | S159-01/02/04 |
| `/api/v1/facilities/bookings/recurring` · `/resources` | POST | S159-05 |
| `/api/v1/facilities/availability` · `/calendar` · `/utilisation` | GET | S159-06 |
| `/api/v1/facilities/dashboards/*` (backlog, SLA, readiness) | GET | S152-05, PLAT-07 |

**Vendor adapters (Hybrid, optional):** `IwmsCafmPort` (S152-02).
**Events published:** `sfl.ifimp.facility-fault-reported.v1`, `work-order-created/assigned/status-changed/closed.v1`, `room-booking-created.v1`, `room-readiness-changed.v1`.
**Events consumed:** `sfl.ssemp.camera-health-changed.v1` (→ camera-offline work order), `sfl.ssemp.fire-alarm-received.v1`(panel fault → ticket), exam-schedule (→ readiness windows).
**Migrations:** extend `V5__spaces.sql`, `V6__pm_plans.sql`, `V7__materials.sql`, `V8__room_booking.sql`, `V9__readiness.sql`.

### 4.2 `sfl-safety-security-service` — SSEMP (S160, S160a, S161, S162, S162a, S163, S174)

**Current state:** foundation migration only. This is the largest service; **split into sub-contexts** (SRS 0D-note) so life-safety is not coupled to visitor badges:
`visitor` (S160), `accesscontrol` (S160a), `cctv` (S161), `intrusion` (S162), `lifesafety` (S162a), `hse` (S163), `emergencycomms` (S174), plus a shared `integrationhub` for inbound device security.

**Aggregates:** `VisitorVisit`, `VisitorPass`, `Watchlist`, `AccessEvent`, `AccessZone/Schedule/DoorGroup`, `CctvCamera`, `EvidenceRequest`, `AlarmEvent`, `ProtectedZone`, `LifeSafetyEvent`, `EvacuationRollCall`, `SecurityIncident`, `CorrectiveAction`, `HazardObservation`, `EmergencyNotification`, `NotificationTemplate`, `AudienceGroup`.

| Endpoint (API-first) | Method | SRS |
|---|---|---|
| `/api/v1/security/visitors` (+ `/{id}/approval`, `/check-in`, `/check-out`) | POST/PATCH | S160-01/03 |
| `/api/v1/security/visitors/{id}/badge` | POST | S160-02 |
| `/api/v1/security/visits/group` · `/passes` | POST | S160-05 |
| `/api/v1/security/watchlists` | POST/GET | S160-06 |
| `/api/v1/security/roll-call` | GET | S160-03, S162a-04 |
| `/api/v1/integrations/webhooks/access-control` (inbound) | POST | S160a-01 |
| `/api/v1/security/access-events` · `/exceptions` · `/overrides` | GET/POST | S160a-01/03/04 |
| `/api/v1/security/access/zones` · `/schedules` · `/door-groups` | POST/GET | S160a-05 |
| `/api/v1/security/occupancy` · `/muster` | GET | S160a-06 |
| `/api/v1/security/cameras` · `/{id}/health` · `/live-sessions` | GET/POST | S161-01/04 |
| `/api/v1/security/evidence-requests` (+ `/{id}/approve`, `/{id}/export`) | POST/PATCH | S161-02/03 |
| `/api/v1/security/footage/retention` · `/disclosures` | POST/GET | S161-05 |
| `/api/v1/integrations/webhooks/intrusion` · `/fire` (inbound) | POST | S162-01, S162a-01 |
| `/api/v1/security/alarms` (+ `/{id}/ack`, `/{id}/escalate`) | GET/PATCH | S162-02/03 |
| `/api/v1/security/protected-zones/arming` | POST/GET | S162-04 |
| `/api/v1/security/incidents` (+ `/{id}/severity`, `/investigation`, `/capa`) | POST/PATCH | S163-01/02/03 |
| `/api/v1/security/hazards` · `/statutory-notifications` | POST | S163-05/06 |
| `/api/v1/security/emergency/activations` (+ `/break-glass`, `/{id}/status`, `/all-clear`) | POST | S174-01/02/03/04 |
| `/api/v1/security/emergency/templates` · `/audience-groups` · `/drills` | POST/GET | S174-05/06 |
| `/api/v1/security/dashboards/soc` | GET | PLAT-07 |

**Vendor adapters (Buy-and-Integrate / Hybrid):** `AccessControlPort`, `CctvVmsPort`, `IntrusionPort`, `FireLifeSafetyPort` (observe-only), `NotificationGatewayPort` (→ S074/S077), `BadgePrinterPort` (S160). All behind IntegrationHub with the inbound-security guard.
**Events published:** `sfl.ssemp.visitor-pre-registered/checked-in.v1`, `access-event-received/exception-detected.v1`, `camera-health-changed/cctv-evidence-requested.v1`, `intrusion-alarm-received.v1`, `fire-alarm-received.v1`, `hse-incident-reported/corrective-action-created.v1`, `emergency-notification-activated/status-received.v1`.
**Fast lane:** life-safety and emergency events (`fire-alarm-received`, `emergency-notification-activated`) publish to a **priority exchange/queue** consumed ahead of standard traffic (comms emergency-queue analogue) — meets **CT-20** latency.
**Migrations:** `V2__visitor.sql` … `V9__emergency_notification.sql` (one per sub-context), plus `V#__integration_inbox_security.sql`.

### 4.3 `sfl-fleet-logistics-service` — FTLMP (S166, S168_fuel, S171)

**Current state:** foundation migration only. Feature packages: `fleet` (S166), `fuel` (S168_fuel), `courier` (S171).

**Aggregates:** `Vehicle`, `Driver`, `Trip`, `VehicleInspection`, `FuelCard`, `FuelTransaction`, `FuelException`, `DriverLogbook`, `CourierItem`, `DispatchManifest`, `CustodyRecord`.

| Endpoint (API-first) | Method | SRS |
|---|---|---|
| `/api/v1/fleet/vehicles` (+ compliance, readiness) | POST/GET/PATCH | S166-01 |
| `/api/v1/fleet/trips` (+ `/{id}/assignment`, `/{id}/closure`) | POST/PATCH | S166-02 |
| `/api/v1/integrations/webhooks/telematics` (inbound) · `/fleet/vehicles/{id}/movement` | POST/GET | S166-03 |
| `/api/v1/fleet/emergency-logistics` | POST | S166-04 |
| `/api/v1/fleet/drivers` (+ licence/eligibility) | POST/GET | S166-05 |
| `/api/v1/fleet/trips/{id}/inspections` | POST | S166-06 |
| `/api/v1/integrations/webhooks/fuel` (inbound) · `/fuel/transactions` | POST/GET | S168_fuel-01 |
| `/api/v1/fuel/reconciliation` · `/exceptions` (+ `/{id}/decision`) | GET/PATCH | S168_fuel-02/03 |
| `/api/v1/fuel/cards` · `/analytics` | POST/GET | S168_fuel-04/05 |
| `/api/v1/logistics/items` · `/manifests` (+ `/{id}/custody`) | POST/PATCH | S171-01/02 |
| `/api/v1/logistics/receipts` · `/returns` | POST | S171-03/06 |
| `/api/v1/integrations/webhooks/courier` · `/logistics/scans` | POST | S171-04 |
| `/api/v1/logistics/inbound-mail` | POST/GET | S171-05 |
| `/api/v1/fleet/dashboards/*` · `/logistics/dashboards/*` | GET | PLAT-07 |

**Vendor adapters:** `TelematicsPort` (Phase-2-ready seam), `FuelProviderPort`, `CourierCarrierPort`/`ScannerPort` (optional).
**Events published:** `sfl.ftlmp.vehicle-created/readiness-changed/vehicle-location-received.v1`, `dispatch-created/received.v1`, `fuel-transaction-received/fuel-exception-detected.v1`.
**Events consumed:** `sfl.ssemp.security-incident-escalated.v1` (emergency logistics), exam-dispatch readiness.
**Migrations:** `V2__fleet.sql`, `V3__driver.sql`, `V4__trip_inspection.sql`, `V5__fuel.sql`, `V6__courier_custody.sql`.

### 4.4 `sfl-asset-visibility-service` — AVAMP-Lite

**Current state:** asset/device/location references implemented (`POST/GET /api/v1/assets`, move/custody/evidence, outbox) with tests. **Extend, do not expand into a full asset system.**
**Additions:** `external-device-linked` events consumed by SSEMP/FTLMP; device lifecycle state; alignment with S152 `DeviceLocationMap` (S152-06) so device→location is authoritative in facilities and referenced here.
**Events:** `sfl.avamp.asset-registered/asset-status-changed/external-device-linked.v1`.

---

## 5. Vendor integration (Buy-and-Integrate) build pattern

Every purchased-system integration (SRS §5.2, Appendix B) is built the same way, isolated behind the IntegrationHub so the vendor is replaceable (PLAT-01):

**Adapter anatomy (`infrastructure/integration/<vendor>`):**
1. `<Vendor>Port` interface lives in `application/port`; domain never sees it.
2. Inbound receiver (`/api/v1/integrations/webhooks/{vendor}`) → **authenticate (per-vendor HMAC or mTLS) → allowlist source IP → schema-validate → store raw in inbox → normalise to canonical SFL event → publish** (SRS 0F). Reject-and-log otherwise.
3. Outbound calls resolve the **ACTIVE integration config at call time** (runtime config, not startup constant) and **fail loud** with `IntegrationConfigNotFound` — never a silent fallback (the comms `provider_registry` lesson).
4. Anti-corruption mapping: vendor field names/models never leak past the adapter.
5. Idempotent consume keyed on `eventId`; rate-limit/back-off/DLQ per vendor.
6. **Evidence by reference:** store references + hashes, not raw video/large files, unless policy exception (SRS §4.2, S161).

**Build sequence per adapter:** simulator/fake → inbound schema + contract tests → auth + health check → webhook/polling receiver → normalise → inbox persist → publish → dashboard/read model → integration-health metrics → connect real vendor.

**Phase-1 adapters:** AccessControl, Cctv, Intrusion, FireLifeSafety, EmergencyNotification, Fuel, (Telematics, Courier — seams now, wire when procured).
**Procurement gate:** no vendor is selected until it passes the SRS §5.2 integration-readiness checklist; each has a named integration owner, sandbox, event mapping, retention plan and contract-test plan.

---

## 6. Cross-service sagas (choreography + process managers)

Cross-service flows are **sagas** computed from each service's events into their own read model — never a cross-schema join. Consumers are idempotent (inbox); failures compensate explicitly.

1. **Hall Readiness saga** (owner: facilities `readiness`) — consumes facilities work-order/booking events, SSEMP camera-health/access/life-safety status, FTLMP dispatch readiness; computes `ReadinessChecklist`; emits `HallReadinessConfirmed` / `HallReadinessBlocked` to examination systems (NBES). SRS S152-03, BR-01.
2. **Emergency Incident Response saga** (owner: SSEMP) — fire/panic/intrusion → notify (S174 fast lane) → access lockdown (S160a) → CCTV preservation (S161) → emergency logistics (FTLMP S166-04) → roll-call (S162a) → all-clear. SRS §6.3, S162a-02/04.
3. **Secure Dispatch Chain-of-Custody saga** (owner: FTLMP `courier`) — manifest → custody handovers → receipt → return reconciliation, with SSEMP incident on variance and AVAMP device references. SRS S171-02/03/06, BR-04.

Each saga: a process-manager aggregate holding state, idempotent event handlers, timeouts/escalation, compensation actions, and a dedicated read model + dashboard.

---

## 7. Eventing, messaging & data

- **Broker:** RabbitMQ. Exchange `sfl.events` (topic), routing key `{platform}.{event-name}.v{version}`, dead-letter `sfl.events.dlx`, consumer queues `sfl.{consumer}.{purpose}`. **Fast-lane** exchange/queue for life-safety/emergency drained first.
- **Envelope:** the `sfl-service-common` envelope (§2.1) on every message; `traceparent` propagated for OpenTelemetry.
- **Outbox → publish:** business change + outbox row in one local transaction; `OutboxDrainer` (`@Scheduled`, `FOR UPDATE SKIP LOCKED`, exponential backoff, poison after N) publishes — at-least-once.
- **Inbox → consume:** every consumer writes the `eventId` to `inbox_messages` before processing and is idempotent (SRS 0K); Redis may front the dedup but Postgres is the record.
- **Data:** schema per service; Flyway per service; entity groups per feature; **Postgres Row-Level Security** + repository site-scope filter driven by the principal's `SiteScopes` (SRS 0L); no cross-schema FK (ArchUnit/DB-lint enforced).
- **Redis:** device-state cache (`sfl:{platform}:device-state:{type}:{id}`), permission snapshots (edge), dashboard acceleration — never the system of record.

---

## 8. Security

- **Authentication:** OAuth2 **resource server** validating OIDC/JWT (standards-based; provider-pluggable — swap = config, not code). Runtime token validation is pure OIDC/JWKS so it works **offline at the edge** via cached JWKS.
- **Authorization:** `AuthorizationPolicy` (in `sfl-service-common`) enforced on every command/query — role **and** site-scope; denials audited; no local user store. Dev uses the `X-SFL-*` header actor resolver; prod swaps in the JWT/claims-backed `ActorContext` producer (same interface).
- **Inbound integration security:** §5/§9 (HMAC/mTLS/allowlist/schema) — commissioning **CT-19**.
- **Secrets:** vault references only; rotation without redeploy (config-without-code, PLAT-05).

---

## 9. Observability & operations

- Actuator `health` (liveness/readiness/startup distinct), `info`, `metrics`, `prometheus`.
- OpenTelemetry traces/metrics/logs; correlation id spans HTTP → outbox → RabbitMQ → consumers.
- **Integration-health dashboard** (SRS PLAT-06): vendor/device health, inbox backlog, DLQ/rejection rate, reconciliation status, consumer lag — first-class SLOs.
- Startup guards refuse traffic when a required vendor integration is unconfigured for the environment (comms analogue).

---

## 10. Delivery waves (SRS-traceable)

Sequenced so the platform spine and highest-risk safety flows come first. Each wave lists scope (SRS IDs), key deliverables and exit criteria.

| Wave | Scope (SRS) | Key deliverables | Exit criteria |
|---|---|---|---|
| **R0 Foundation** | Module 4: PLAT-01..05 | common kernel, ports, outbox/inbox, audit hash-chain, RabbitMQ topology, resource-server, ArchUnit, edge skeleton | §2.3 DoD; CT-17/18/19 scaffolds |
| **W1 Facilities core + AVAMP** | S152-01/03/05/06, S153-01/02/03, AVAMP | facilities master data, spaces, device-location map, work-request→work-order spine, readiness score v1; AVAMP references extended | fault→work-order→audit→outbox→event proven end-to-end; readiness score returns block reasons |
| **W2 Physical security + IntegrationHub** | S160, S160a, S161, S162 | visitor lifecycle; access-control adapter + JML + overrides + zones; CCTV inventory/health/evidence; intrusion alarms + SOC ack/escalate; IntegrationHub inbound security | CT-19 passes on all inbound webhooks; evidence-by-reference enforced; SOC dashboard live |
| **W3 Life-safety + Emergency comms + Fast lane** | S162a, S174 | fire/life-safety observe-only feed; emergency activation + break-glass + delivery/ack + degraded fallback; fast-lane exchange | CT-20 latency met; break-glass records after-the-fact approval; SFL never in actuation path |
| **W4 HSE** | S163 | incident/near-miss, severity routing, investigation/CAPA, hazard register, statutory notifications | CAPA overdue + statutory-deadline escalation proven; closure blocked on open mandatory actions |
| **W5 Fleet & Logistics** | S166, S168_fuel, S171 | fleet register/compliance/trips/inspections/driver eligibility; fuel ingest/reconcile/exceptions/cards/analytics; courier manifest/custody/receipt/return | fuel reconciliation exception path; unbroken chain-of-custody with variance exception |
| **W6 Sagas + Edge + Dashboards** | §6 sagas, PLAT-04/06/07 | hall-readiness, emergency-incident, secure-dispatch sagas; edge survivability; role-based dashboards + Analytics publish | CT-17 edge-failover passes; sagas compensate on failure; dashboards site-scoped |
| **W7 Commissioning & Go-Live** | NFRs §6, CT-17..21 | perf/latency/DR tests, security/pen test, config-without-code proof, data-protection/retention proof | all commissioning tests green; runbooks + DR drill; vesting-day readiness sign-off |

Waves W2–W5 are largely parallelisable across service teams once R0 is in place; sagas (W6) depend on their contributing events existing.

---

## 11. Testing & commissioning strategy

- **Unit (domain):** aggregates/policies — one test per business-rule acceptance criterion.
- **Application:** use-case tests with fakes for ports (already present, e.g. `WorkOrderServiceTest`, `AssetVisibilityServiceTest`).
- **Contract (WebMvc slice):** per endpoint — happy path, error envelope (SRS *Error States*), 403 authorization (already present, e.g. `AssetReferenceControllerTest`).
- **Integration (Testcontainers):** Postgres + RabbitMQ + Redis — persistence, outbox→publish, inbox idempotency, RLS site-scope.
- **Vendor contract tests:** against simulators; verify normalisation + inbound security.
- **Saga tests:** event-in → state → compensation.
- **Architecture tests (ArchUnit):** dependency rule, contracts-only cross-module refs, no product names in domain, DB-lint no cross-schema FK.
- **Commissioning tests mapped to SRS NFRs:** **CT-17** edge failover (NFR-ES1), **CT-18** audit tamper-evidence (NFR-ES2), **CT-19** forged-webhook rejection (NFR-SEC2), **CT-20** emergency fast-lane latency (NFR-P1), **CT-21** identity-provider swap (PLAT-02).

---

## 12. CI/CD, Docker & environments

- **Build:** multi-module Maven (`services/pom.xml` reactor); `sfl-service-common` published/consumed as a library.
- **Containers:** one Dockerfile per service (already present); `deploy/compose` for local (4 services + Postgres + RabbitMQ + Redis + dev identity + per-service `migrate` (Flyway) job), mirroring the comms `db/redis/api/worker/beat/migrate` shape.
- **Pipeline stages:** compile → unit/app tests → ArchUnit + DB-lint → build images → integration (Testcontainers) → contract → deploy to env → smoke/commissioning subset.
- **Environments:** dev/staging/prod isolation enforced on the config read-path (comms lesson); core tier (HQ+DR) and edge tier per examination centre (SRS §2.5, 0B).

---

## 13. Definition of Done & coding standards

**Per slice:** contract + DTO validation; controller with correct status; WebMvc contract test (happy + error + 403); domain unit tests for each business acceptance criterion; use case with transaction boundary + outbox; Flyway migration; adapter (simulator for vendors); authorization + site-scope + audit; integration test; ArchUnit green; SRS ID referenced in code/tests and this backlog updated.
**Standards:** domain free of framework imports; ports own the boundary; one adapter per product; events versioned and backward-compatible; every state change audited; every query site-scoped; British English in user-facing strings matching the SRS *Error States*.

---

## 14. Immediate next steps (first two sprints)

1. **R0**: finish `sfl-service-common` kernel (envelope, error, audit hash-chain, integration-security primitives, ports) + per-service foundation (outbox drainer, RabbitMQ topology, resource server, ArchUnit + DB-lint). Prove the spine with one facilities slice.
2. **W1 slice 1 (reference slice)**: `POST /api/v1/facilities/work-requests` → `work-order` → outbox `work-order-created.v1` → audit hash-chain → readiness re-score, built strictly through the §3 recipe as the template every team copies.
3. Stand up **IntegrationHub inbound-security** + one **AccessControl simulator** so CT-19 and the vendor pattern are proven before W2 fans out.

---

## 15. Requirement → slice → wave backlog (traceability)

The living backlog. Every `SRS-SFL-*` maps to a service, a primary endpoint/slice and a wave. (Condensed; maintain per-slice as build proceeds.)

| SRS ID range | Service | Wave |
|---|---|---|
| S152-01..06 | facilities/masterdata,spaces | W1 |
| S153-01..06 | facilities/maintenance,preventive,materials | W1 (01–03), W4/parallel (04–06) |
| S159-01..06 | facilities/booking | W1–W2 |
| S160-01..06 | safety-security/visitor | W2 |
| S160a-01..06 | safety-security/accesscontrol | W2 |
| S161-01..05 | safety-security/cctv | W2 |
| S162-01..05 | safety-security/intrusion | W2 |
| S162a-01..05 | safety-security/lifesafety | W3 |
| S163-01..06 | safety-security/hse | W4 |
| S174-01..06 | safety-security/emergencycomms | W3 |
| S166-01..06 | fleet-logistics/fleet | W5 |
| S168_fuel-01..05 | fleet-logistics/fuel | W5 |
| S171-01..06 | fleet-logistics/courier | W5 |
| PLAT-01..08 | common + all services | R0 (01–05), W6 (06–08) |

---

*This workplan is a living document. It is governed by the SRS; where they disagree, the SRS wins and this plan is corrected.*
