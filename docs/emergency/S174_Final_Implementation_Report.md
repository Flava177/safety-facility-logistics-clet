# S174 Emergency Mass Notification - Final Implementation Report

Date: 2026-07-27
Service: `services/sfl-emergency-notification-service`
Schema: `emergency_notification`
ADR: `docs/adr/0004-s174-emergency-notification-as-separate-service.md`

## Delivery status

S174 is implemented and ready for review as a separate deployable Spring Boot service.

| Requirement | Status | Primary implementation area |
|---|---:|---|
| `SRS-SFL-S174-01` Operational records | Done | Templates, scenarios, audience groups, recipient zones, site scope, duplicate-active checks |
| `SRS-SFL-S174-02` Workflow | Done | Routine approval-before-send, break-glass send-without-preapproval, cancel, degraded fallback routing, all-clear, closure and reopen gates |
| `SRS-SFL-S174-03` Evidence and audit | Done | Evidence references, retention class checks, append-only audit hash chain |
| `SRS-SFL-S174-04` Secure integrations | Done | Provider callbacks, HMAC validation, secure inbox, idempotency, outbox replay/dead-letter, recorded adapters |
| `SRS-SFL-S174-05` Dashboards and reports | Done | Dashboard freshness/reconciliation, drill runs, activation CSV export |

## Delivered artifacts

| Artifact | Status | Notes |
|---|---:|---|
| ADR and planning docs | Done | ADR 0004 plus API inventory, domain model, event contracts, gap report, migration plan, test plan and RTM |
| Service scaffold | Done | Boot 4.1, Java 17, OpenAPI, development actor headers, static page at `/emergency/` |
| Persistence | Done | Flyway migrations V1-V8 for service foundation, records, activations, delivery, acknowledgements, evidence, audit, dashboard, runtime defaults and command idempotency |
| Application workflow | Done | Activation lifecycle, provider callbacks, break-glass, cancellation, degraded fallback, after-action approval, all-clear, closure, reopen, dashboards, drills and sweeps |
| Integration catalog | Done | S174 events added to `docs/integration/event-catalog.md` |
| Deployment packaging | Done | Service module registered in `services/pom.xml`, Dockerfile added, microservices compose entry added on host port `8095`, local DB compose added as `compose.emergency-db.yml` |

## Reviewer entry points

- `/emergency/`
- `/swagger-ui.html`
- `/api/v1/emergency/templates`
- `/api/v1/emergency/scenarios`
- `/api/v1/emergency/audience-groups`
- `/api/v1/emergency/recipient-zones`
- `/api/v1/emergency/activations`
- `/api/v1/emergency/activations/break-glass`
- `/api/v1/emergency/activations/{id}/cancel`
- `/api/v1/emergency/activations/{id}/degraded-fallback`
- `/api/v1/emergency/activations/{id}/reopen`
- `/api/v1/emergency/provider-callbacks/{provider}/delivery-status`
- `/api/v1/emergency/provider-callbacks/{provider}/acknowledgements`
- `/api/v1/emergency/integrations/health`
- `/api/v1/emergency/dashboard`
- `/api/v1/emergency/drills`
- `/api/v1/emergency/reports/activations.csv`

## Verification

Latest commands run from `services/` with Java 17:

```powershell
mvn -pl sfl-emergency-notification-service -am test
mvn -pl sfl-fleet-logistics-service -am test
mvn -pl sfl-safety-security-service -am test
```

Current local result:

```text
S174 reactor against supplied Postgres: BUILD SUCCESS; Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
Release 1 changed-service backend gate: sfl-service-common + sfl-fleet-logistics-service + sfl-emergency-notification-service = 466 tests, 0 failures, 0 errors, 0 skips
Frontend SFL Operations UI: 156 tests, 0 failures; production build clean
```

The PostgreSQL-backed S174 E2E suite ran against the supplied emergency notification E2E database on `localhost:55445`.
The remaining skipped test is an existing Docker/Testcontainers-gated probe outside S174.

Command idempotency is implemented for activation creation and break-glass creation so retried requests do not
double-create or double-send. Provider callbacks remain idempotent through the secure inbox and delivery/ack
unique keys. Transition POSTs are state guarded and audited, but they do not yet replay stored response bodies.
Real outbound notification delivery remains intentionally deferred to the later CLET Comms integration; Release 1 uses the recorded outbound adapter.

## Review checklist

- ADR 0004 avoids the already-used ADR 0003.
- No hard-delete endpoint is exposed for operational, evidence, audit or workflow records.
- Break-glass is not gated by routine approval and requires after-action approval before closure.
- Retried create/break-glass activation requests with the same `Idempotency-Key` and payload return the original activation.
- SFL remains observe-only for certified life-safety systems.
- RBAC additions in `sfl-service-common` are additive and regression-gated against existing fleet/fuel/dispatch suites.
