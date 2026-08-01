# S174 Requirement Traceability Matrix

Package root `gh.edu.clet.sfl.emergencynotification`. Every SRS-SFL-S174 requirement maps to code and
tests; every SRS error state maps to `EmergencyErrorCode` and is exercised by the domain or E2E suite.

## SRS requirement → artifacts → tests

| Requirement | Key artifacts | Verifying tests |
|---|---|---|
| **S174-01** Operational records | `domain.model.{NotificationTemplate, EmergencyScenario, AudienceGroup, RecipientZone}`; `application.service.EmergencyRecordsService`; `infrastructure.persistence.Jdbc*Repository`; `api.{Template,Scenario,AudienceGroup,RecipientZone}Controller`; V2/V3 | `EmergencyDomainTest` (record validation, duplicate-active id); service auth/site-scope tests (duplicate/missing-scope/unauthorized messages); E2E #1 |
| **S174-02** Workflow | `domain.model.NotificationActivation` (+ status/mode enums); `domain.policy.BreakGlassPolicy`; aggregate cancellation, degraded-fallback, all-clear, closure and reopen gates; `application.service.ActivationService`; `api.ActivationController`, `BreakGlassController`; V4 | `EmergencyDomainTest`; E2E #2-#7, #16, #17 plus cancel/reopen/degraded-fallback operator scenarios; SLA escalation E2E #15 |
| **S174-03** Evidence & audit | `application.port.{AuditPort, EvidencePort}`; `infrastructure.persistence.{JdbcAudit, JdbcEvidence}`; append-only hash chain | audit-chain integrity/tamper tests; closure-evidence + retention-class + export-approval message tests; E2E #18 |
| **S174-04** Integrations | `application.port.{NotificationGatewayPort, DeliveryReceiptPort, AudienceDirectoryPort, IncidentLinkPort, LifeSafetyEventPort, AccessControlLockdownPort, CctvEvidencePort, ReportingPort, OutboxAdminPort}`; secure inbox; outbox+drainer+dead-letter+replay; recorded adapters; `api.ProviderCallbackController`, `IntegrationController` | integration-security tests (invalid-signature/schema/duplicate messages); E2E #8–#13, #22 (fast-lane), #23 (degraded), #24 (observe-only) |
| **S174-05** Dashboards & reports | `application.service.EmergencyDashboardService`; `infrastructure.persistence.JdbcDashboard*`; `api.{DashboardController, ReportController}`; V6 | dashboard reconcile + stale-warning tests; E2E #14, #19, #20; drill E2E #21 |

## SRS error states → exact message (asserted in tests)

| Error state | Message |
|---|---|
| Duplicate Identifier | `An active record with this identifier already exists for this site.` |
| Missing Site Scope | `Select a valid CLET site before saving this record.` |
| Unauthorized Scope | `You are not authorised to access this site or record.` |
| Closure Evidence Missing | `Required evidence must be attached before closure.` |
| SLA Breach | `This item has breached its configured SLA and has been escalated.` |
| Unauthorized Approval | `You do not have permission to approve this workflow transition.` |
| Export Not Approved | `Evidence export requires approval and a recorded reason.` |
| Retention Class Missing | `Select a retention class before saving this evidence.` |
| Audit Chain Failure | `Audit integrity check failed. Escalate to compliance and security.` |
| Invalid Signature | `Integration message rejected: signature verification failed.` |
| Schema Validation Failed | `Integration message rejected: payload does not match registered schema.` |
| Duplicate Message | `Duplicate integration message received and safely ignored.` |
| Data Stale | `Dashboard data is older than the configured freshness threshold.` |
| No Scope | `No site scope is assigned to your user profile.` |
| Restricted Drilldown | `You do not have permission to view the underlying record.` |

## Cross-cutting

- Every operational record carries site scope, actor/time metadata, source channel, correlation ID and
  optimistic-lock version; mutable aggregates use explicit transitions (no generic status PATCH).
- Break-glass is never gated by routine approval; closure of a break-glass activation is blocked until
  after-the-fact approval + justification are recorded.
- SFL never performs certified life-safety actuation (Arch §0E); `LifeSafetyEventPort` is observe-only.
- Activation creation/break-glass retries use `CommandIdempotencyPort` + V8 `command_idempotency_keys`; provider
  callbacks use the secure inbox and receipt/ack unique keys.
- Verified by `mvn -pl sfl-emergency-notification-service -am test`: 40 tests, 0 failures/errors/skips,
  plus the S166/S168/S171 regression gate in `sfl-fleet-logistics-service`: 423 tests, 0 failures/errors/skips.
