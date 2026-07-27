# S171 Test Plan

## Quality layers

- **Domain unit tests** (`dispatch/domain/DispatchDomainTest`, plain JUnit 5, no Spring): item lifecycle
  transitions and clean-outcome closure, chain-of-custody derivation, custody gap detection (missing hop /
  out-of-order / broken seal / count mismatch), receipt variance classification, return reconciliation
  outcomes, and the full exception-case transition set incl. closure gating.
- **Policy / state-transition tests**: `CustodyChainPolicy`, `ReceiptVariancePolicy`,
  `ReturnReconciliationPolicy`, `DispatchClosurePolicy` — illegal transitions rejected, closure blocked
  while an exception is open.
- **Application-service tests**: authorization + site scope on every operation, idempotency (edge receipt
  + scan + provider dedup), audit/outbox atomicity, and closure gates.
- **Controller / API tests** (MockMvc): validation, envelope shape, pagination + stable sorting, explicit
  workflow endpoints, error mapping and OpenAPI exposure.
- **Persistence / integration tests** (Testcontainers PostgreSQL): Flyway apply on fresh DB and on a DB
  pre-loaded with V1–V15, constraints, optimistic locking, idempotency constraints, and read-model
  projections.
- **ArchUnit** (`dispatch/architecture/DispatchArchitectureTest`): `..dispatch.domain..` must not depend
  on Spring/JPA/servlet/jdbc/vendor libraries, nor on `..dispatch.api..`/`..dispatch.infrastructure..`.
- **Event-contract tests**: every published `FleetEventType` dispatch constant has a catalog entry and a
  `sfl.ftlmp.*.v1` name.
- **Scheduled-job tests**: undelivered / outstanding-return / SLA-escalation / dashboard-refresh /
  stale-integration sweeps are multi-execution-safe (no duplicate cases, notifications, audit or outbox).
- **Audit-chain tests**: append-only integrity + tamper detection.

## Mandatory E2E scenarios (`dispatch/e2e/…`, `@SpringBootTest`, gated on `FleetPostgresSupport.databaseAvailable`)

Reuses `fleet/e2e/FleetPostgresSupport` (external DB → Testcontainers `postgres:16-bookworm` → skip) and
the `MutableClock` for deterministic SLA/staleness time-travel. Each scenario builds an isolated tenant
site and drives the application services directly (as the fuel E2E suite does).

1. Register an item and track RECEIVED → STAGED → DISPATCHED → IN_TRANSIT → DELIVERED.
2. Reject dispatch of an unregistered item (`UNREGISTERED_ITEM`).
3. Build a manifest with seal IDs/counts and record a full custody handover chain.
4. Detect a custody gap (missing handover / broken seal / count mismatch) and block closure.
5. Confirm a clean destination receipt (seal + count + signature) and complete chain-of-custody.
6. Open and escalate a receipt variance to logistics and security (SSEMP).
7. Capture a receipt offline (WAN loss) and reconcile idempotently on restore (no double-apply).
8. Reconcile a matched return leg and close custody.
9. Raise a return discrepancy (shortfall/extra/broken seal) and block custody closure.
10. Register inbound mail and distribute with a recorded acknowledgement.
11. Flag and escalate an undelivered inbound item after its configurable window.
12. Escalate an outstanding (not-returned) item after its configurable window.
13. Flag a scan mismatch from optional scanner ingestion and route it to variance handling.
14. Reject unsigned/schema-invalid integration input via the secure inbox.
15. Retry and surface a failed outbound integration (outbox dead-letter + privileged replay).
16. Prevent exception closure without explanation, decision and evidence.
17. Verify audit-chain integrity and detect tampering.
18. Verify dashboard counts against source records.
19. Full CT-05 "Secure Dispatch": generate an examination dispatch manifest, seal, assign an S166
    vehicle/trip, dispatch, receive at centre and reconcile all items with no unexplained variance
    (GPS/RFID mocked).

## Regression gate

`mvn -pl sfl-fleet-logistics-service -am test` must keep all S166 and S168 tests green. Runtime
verification: PostgreSQL on 5443, app on 8093, `/actuator/health` UP, `/v3/api-docs`, Swagger UI, and
`/dispatch/`.
