# S174 Test Plan (tests-first)

S174 follows the platform tests-first rule: the expected API contracts, domain behaviours, state
transitions, validations, error envelopes, integration behaviours, audit behaviours, dashboards and E2E
scenarios are defined as tests before the production feature logic. Tests initially fail; production code
is implemented until they pass. Every SRS-SFL-S174 requirement maps to ≥1 test, and every SRS error state
is asserted with its **exact** user-facing message.

## Quality layers

- **Domain unit tests** (`domain/EmergencyDomainTest`, plain JUnit 5, no Spring): record validation,
  duplicate-active-identifier rule, activation lifecycle transitions (routine + break-glass), all-clear
  gating, closure gating (reason + summary + evidence + break-glass after-action), delivery/ack updates.
- **Domain / state-transition tests** (`EmergencyDomainTest`): `NotificationActivation` and
  `BreakGlassPolicy` allowed and rejected paths; break-glass never gated by routine approval; after-action
  required before break-glass closure.
- **Application-service tests**: authorization + site scope on every operation (exact SRS messages for
  duplicate/missing-scope/unauthorized/closure-evidence/export/retention), idempotency (activation +
  callback), audit/outbox atomicity.
- **Controller / API coverage**: HTTP controllers use the shared `ApiResponse` envelope and
  `EmergencyApiExceptionHandler`; the service-level E2E suite drives the same application methods and asserts
  the SRS validation paths. Add MockMvc tests before changing response envelopes or request mappings.
- **Integration-security tests**: signed vs unsigned callback (`Invalid Signature`), schema-invalid
  callback (`Schema Validation Failed`), duplicate callback (`Duplicate Message` → 200, no double-apply),
  inbox-before-domain ordering.
- **Event-catalog tests**: every published S174 `sfl.ssemp.*` event has a catalog entry and a
  `sfl.ssemp.*.v1` name; the two pre-seeded events are reused verbatim.
- **Architecture tests** (`architecture/EmergencyArchitectureTest`): `..emergencynotification.domain..`
  must not depend on Spring/JPA/JDBC/servlet/HTTP/Jackson/vendor SDKs nor on `..api..`/`..infrastructure..`.
- **Persistence / Flyway tests** (Testcontainers PostgreSQL): migrations apply on a fresh DB; constraints;
  optimistic locking; idempotency constraints; read-model projections.
- **Scheduled-sweep tests**: unacknowledged-escalation / dashboard-refresh / stale-integration sweeps are
  multi-execution-safe (no duplicate escalations, audit or outbox rows).
- **Audit-chain tests**: append-only integrity + tamper detection.

## Mandatory E2E scenarios (`e2e/EmergencyMandatoryScenariosEndToEndTest`, gated on Postgres availability)

1. Create template, scenario, audience group and recipient zone.
2. Submit routine activation for approval.
3. Reject unauthorised activation approval (`You do not have permission to approve this workflow transition.`).
4. Approve routine activation and activate it.
5. Break-glass activation sends without pre-approval for an authorised role.
6. Break-glass activation requires after-the-fact approval before closure.
7. Break-glass attempt by an unauthorised role is denied.
8. Delivery-status callback updates channel/recipient state idempotently.
9. Duplicate provider callback is safely ignored.
10. Unsigned provider callback is rejected before domain side effects.
11. Schema-invalid callback is rejected before domain side effects.
12. Failed outbound provider delivery is retried and visible on integration health.
13. Privileged replay requeues a dead-lettered provider delivery.
14. Acknowledgement tracking updates dashboard counts.
15. Failed/unacknowledged recipients escalate after SLA.
16. All-clear can be sent only for an active activation.
17. Activation cannot close without closure reason / evidence / summary.
18. Audit chain integrity holds after activation and callbacks.
19. Dashboard counts reconcile to source records.
20. Stale provider/dashboard data shows a warning.
21. Drill run records performance and produces report metrics.
22. CT-20 fast-lane path is represented and measured with a recorded adapter/timer.
23. Degraded-mode activation records fallback path and reconciliation metadata.
24. SFL does not perform certified life-safety actuation; it only observes/governs/supplements
    (`LifeSafetyEventPort` observe-only; no actuation call is made).

## Regression gate

`mvn -pl sfl-emergency-notification-service -am test` green, plus `mvn -pl sfl-fleet-logistics-service -am
test` (S166/S168/S171) remaining green. Runtime: service on 8095, `/actuator/health` UP, `/v3/api-docs`,
Swagger UI, `/emergency/`.
