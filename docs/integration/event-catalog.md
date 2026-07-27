# Phase 1 Event Catalog

## Purpose

The SFL platform uses event-first thinking. Business actions should be represented as events and published through the approved messaging infrastructure. For Phase 1, RabbitMQ is the approved broker.

The event catalog defines stable names and responsibilities before implementation starts.

## Naming Rules

| Item | Pattern | Example |
|---|---|---|
| Integration event type | `sfl.{platform}.{event-name}.v{version}` | `sfl.ifimp.facility-fault-reported.v1` |
| RabbitMQ exchange | `sfl.events` | `sfl.events` |
| RabbitMQ routing key | `{platform}.{event-name}.v{version}` | `ifimp.facility-fault-reported.v1` |
| Dead-letter exchange | `sfl.events.dlx` | `sfl.events.dlx` |
| Consumer queue | `sfl.{consumer}.{purpose}` | `sfl.workflow.tasks` |
| Redis key | `sfl:{platform}:{purpose}:{scope}:{id}` | `sfl:ssemp:device-state:camera:CAM-001` |

## Required Event Envelope Fields

Every integration event should carry these fields:

| Field | Purpose |
|---|---|
| `eventId` | Unique event ID for idempotency. |
| `eventType` | Stable event name. |
| `eventVersion` | Schema version. |
| `occurredAt` | Business time when the event occurred. |
| `publishedAt` | Time the event was published to RabbitMQ. |
| `correlationId` | Links related operations across APIs, workers and events. |
| `causationId` | Identifies the command/event that caused this event. |
| `siteCode` | Site or campus scope where applicable. |
| `sourceModule` | Module that produced the event. |
| `payload` | Versioned event body. |

## Phase 1 Event Groups

### SFL.IFIMP

| Event | Trigger |
|---|---|
| `sfl.ifimp.facility-fault-reported.v1` | A user reports a facility fault. |
| `sfl.ifimp.work-order-created.v1` | A work order is created from a fault, planned task or maintenance request. |
| `sfl.ifimp.work-order-assigned.v1` | A work order is assigned to a team, staff member or vendor. |
| `sfl.ifimp.work-order-status-changed.v1` | A work order changes status. |
| `sfl.ifimp.work-order-closed.v1` | A work order is completed and closed with required evidence. |
| `sfl.ifimp.room-booking-created.v1` | A room or resource booking is created. |
| `sfl.ifimp.room-readiness-changed.v1` | A room readiness state changes. |

### SFL.SSEMP

| Event | Trigger |
|---|---|
| `sfl.ssemp.visitor-pre-registered.v1` | A visitor is pre-registered. |
| `sfl.ssemp.visitor-checked-in.v1` | A visitor checks in. |
| `sfl.ssemp.access-event-received.v1` | An access-control decision is received from a vendor system. |
| `sfl.ssemp.access-exception-detected.v1` | An access event requires review. |
| `sfl.ssemp.camera-health-changed.v1` | A VMS reports camera health changed. |
| `sfl.ssemp.cctv-evidence-requested.v1` | A user requests CCTV evidence for an incident. |
| `sfl.ssemp.intrusion-alarm-received.v1` | An intrusion alarm is received. |
| `sfl.ssemp.fire-alarm-received.v1` | A fire/life-safety event is received. |
| `sfl.ssemp.hse-incident-reported.v1` | A safety incident or near miss is reported. |
| `sfl.ssemp.corrective-action-created.v1` | A corrective action is created from an HSE case. |
| `sfl.ssemp.emergency-notification-activated.v1` | A mass notification campaign is approved and activated. |
| `sfl.ssemp.emergency-notification-status-received.v1` | Delivery or acknowledgement status is received from the notification provider. |

#### S174 Emergency Mass Notification additions

Added July 2026 with the S174 slice (separate deployable `sfl-emergency-notification-service`; see
[ADR 0004](../adr/0004-s174-emergency-notification-as-separate-service.md)). The two events above are the
pre-seeded catalog names and are reused verbatim; the remainder are justified lifecycle events documented
in `docs/emergency/S174_Event_Contracts.md`. Payloads carry references and classifications only — never
message bodies, unmasked recipient PII or provider secrets.

| Event | Trigger |
|---|---|
| `sfl.ssemp.emergency-template-created.v1` | A notification template is created. |
| `sfl.ssemp.emergency-activation-submitted.v1` | A routine activation is submitted for approval. |
| `sfl.ssemp.emergency-activation-approved.v1` | A routine activation is approved. |
| `sfl.ssemp.emergency-break-glass-activated.v1` | A break-glass activation fires without pre-approval (declared emergency). |
| `sfl.ssemp.emergency-after-action-approved.v1` | After-the-fact approval/justification is recorded for a break-glass activation. |
| `sfl.ssemp.emergency-all-clear-sent.v1` | An all-clear is issued for an active activation. |
| `sfl.ssemp.emergency-acknowledgement-received.v1` | A recipient acknowledgement is recorded. |
| `sfl.ssemp.emergency-activation-closed.v1` | An activation is closed with reason/summary/evidence. |
| `sfl.ssemp.emergency-drill-completed.v1` | A drill run completes and records performance metrics. |

### SFL.FTLMP

| Event | Trigger |
|---|---|
| `sfl.ftlmp.vehicle-created.v1` | A vehicle is registered. |
| `sfl.ftlmp.vehicle-readiness-changed.v1` | A vehicle readiness/compliance status changes. |
| `sfl.ftlmp.vehicle-location-received.v1` | A tracking provider sends vehicle location. |
| `sfl.ftlmp.dispatch-created.v1` | A dispatch movement is created. |
| `sfl.ftlmp.dispatch-received.v1` | A dispatch item is received and acknowledged. |
| `sfl.ftlmp.fuel-transaction-received.v1` | A fuel transaction is received from a vendor/source. |
| `sfl.ftlmp.fuel-exception-detected.v1` | Fuel reconciliation identifies an exception. |

#### S168_fuel lifecycle additions

| Event | Trigger |
|---|---|
| `sfl.ftlmp.fuel-transaction-reconciled.v1` | A policy-versioned reconciliation passes. |
| `sfl.ftlmp.fuel-transaction-rejected.v1` | A fuel record fails controlled validation. |
| `sfl.ftlmp.fuel-anomaly-assigned.v1` | A fuel anomaly receives an accountable assignee and SLA. |
| `sfl.ftlmp.fuel-anomaly-approved.v1` | A manager accepts a documented fuel exception. |
| `sfl.ftlmp.fuel-anomaly-rejected.v1` | A manager rejects the transaction or explanation. |
| `sfl.ftlmp.fuel-anomaly-escalated.v1` | An anomaly is manually or automatically escalated. |
| `sfl.ftlmp.driver-logbook-submitted.v1` | A driver declares and submits a journey logbook. |
| `sfl.ftlmp.driver-logbook-returned.v1` | A reviewer returns a logbook for correction. |
| `sfl.ftlmp.driver-logbook-approved.v1` | A manager approves and locks a logbook. |
| `sfl.ftlmp.driver-logbook-overdue.v1` | A scheduled compliance sweep detects an overdue logbook. |

**Finance/Audit visibility.** Material fuel exceptions are surfaced to Finance/Audit through the
`FinanceAuditVisibilityPort`, whose recorded adapter re-publishes `sfl.ftlmp.fuel-exception-detected.v1`
with a `"visibility":"FINANCE_AUDIT"` payload marker and a `causationId` of the anomaly id. No Finance
database is written directly; delivery rides the same transactional outbox and is therefore observable and
replayable. Outbound delivery health and privileged dead-letter replay are exposed at
`GET /api/v1/fuel/integrations/outbox/health` and `POST /api/v1/fuel/integrations/outbox/{messageId}/replay`
(permission `FUEL_INTEGRATION_REPLAY`).

#### S166 Fleet lifecycle additions

Added July 2026 with the S166 Fleet and Vehicle Management slice. The three fleet events above already covered
vehicle creation, readiness and location; `SRS-SFL-S166-01/02/03` also require change events for compliance,
service, driver eligibility, assignment, inspection, workflow escalation and evidence. These follow the same
naming rule and are documented in `docs/fleet/S166_Event_Contracts.md`.

| Event | Trigger |
|---|---|
| `sfl.ftlmp.vehicle-updated.v1` | Mutable vehicle attributes change. |
| `sfl.ftlmp.vehicle-lifecycle-changed.v1` | A vehicle moves between active/inactive/suspended/archived. |
| `sfl.ftlmp.vehicle-availability-changed.v1` | Vehicle availability changes, including becoming unavailable. |
| `sfl.ftlmp.vehicle-compliance-expiring.v1` | A compliance document enters the configured warning window. |
| `sfl.ftlmp.vehicle-compliance-expired.v1` | A compliance document expires. |
| `sfl.ftlmp.vehicle-service-due.v1` | Vehicle service becomes due by date or odometer. |
| `sfl.ftlmp.vehicle-service-overdue.v1` | Vehicle service becomes overdue. |
| `sfl.ftlmp.driver-registered.v1` | A driver profile reference is registered. |
| `sfl.ftlmp.driver-eligibility-changed.v1` | Driver eligibility changes. |
| `sfl.ftlmp.vehicle-assigned.v1` | A vehicle and driver are assigned to a trip. |
| `sfl.ftlmp.trip-reassigned.v1` | A trip's vehicle or driver is replaced. |
| `sfl.ftlmp.trip-cancelled.v1` | A trip is cancelled. |
| `sfl.ftlmp.trip-completed.v1` | A trip is closed with required evidence. |
| `sfl.ftlmp.vehicle-inspection-failed.v1` | An inspection fails or records a critical defect. |
| `sfl.ftlmp.fleet-workflow-escalated.v1` | A fleet workflow item breaches its SLA or is escalated manually. |
| `sfl.ftlmp.fleet-evidence-registered.v1` | A fleet evidence reference is registered. |
| `sfl.ftlmp.fleet-audit-integrity-failed.v1` | Audit hash-chain replay detects tampering (critical compliance alert). |

#### S171 Mailroom / Courier and Dispatch Tracking additions

Added July 2026 with the S171 slice (`SRS-SFL-S171-01…06`). `sfl.ftlmp.dispatch-created.v1` and
`sfl.ftlmp.dispatch-received.v1` were the two pre-seeded catalog names and are reused verbatim; the remainder are
justified lifecycle events documented in `docs/dispatch/S171_Event_Contracts.md`. Payloads carry references and
classifications only — never signature binaries, unmasked recipient PII or seal secrets. State changes and outbox
records commit atomically through the shared transactional outbox. Inbound scanner/carrier events reuse the secure
integration inbox.

| Event | Trigger |
|---|---|
| `sfl.ftlmp.dispatch-item-registered.v1` | A courier item is registered in the outbound register. |
| `sfl.ftlmp.inbound-item-registered.v1` | An inbound mail item is registered. |
| `sfl.ftlmp.inbound-item-distributed.v1` | An inbound item is distributed with a recorded acknowledgement (item closed). |
| `sfl.ftlmp.inbound-item-undelivered.v1` | A scheduled sweep flags an undelivered/unclaimed inbound item after its window. |
| `sfl.ftlmp.dispatch-created.v1` | A dispatch manifest is created. |
| `sfl.ftlmp.dispatch-dispatched.v1` | A sealed manifest is dispatched (leaves the warehouse). |
| `sfl.ftlmp.custody-handover-recorded.v1` | A chain-of-custody hop is recorded. |
| `sfl.ftlmp.custody-gap-detected.v1` | A missing handover / broken seal / count mismatch is detected (blocks closure). |
| `sfl.ftlmp.dispatch-received.v1` | A clean destination receipt is confirmed. |
| `sfl.ftlmp.dispatch-receipt-variance.v1` | A receipt variance opens an exception (seal/tamper variants surface to SSEMP). |
| `sfl.ftlmp.dispatch-return-reconciled.v1` | A return leg reconciles cleanly against the manifest. |
| `sfl.ftlmp.dispatch-return-discrepancy.v1` | A return shortfall/extra/broken seal blocks custody closure. |
| `sfl.ftlmp.dispatch-scan-mismatch.v1` | A scanned item does not match its manifest entry. |
| `sfl.ftlmp.dispatch-exception-assigned.v1` | A dispatch exception case receives an accountable owner/SLA. |
| `sfl.ftlmp.dispatch-exception-approved.v1` | A manager accepts the documented dispatch exception. |
| `sfl.ftlmp.dispatch-exception-rejected.v1` | A manager rejects the explanation. |
| `sfl.ftlmp.dispatch-exception-escalated.v1` | Manual or SLA escalation of a dispatch exception occurs. |
| `sfl.ftlmp.dispatch-security-variance.v1` | A security-relevant variance (seal/tamper/custody gap) is surfaced to SSEMP. |

> **Known inconsistency (do not copy).** `sfl-facilities-service` currently publishes `sfl.facilities.work-order-created`
> and `sfl-asset-visibility-service` publishes `sfl.avamp.*` without the `.vN` suffix, neither of which matches the
> naming rule above. S166 follows this catalog. Renaming the earlier services is tracked as conflict **C-03** in
> `docs/fleet/S166_Gap_And_Conflict_Report.md` and must happen before an external consumer binds those routing keys.

### SFL.AVAMP

| Event | Trigger |
|---|---|
| `sfl.avamp.asset-registered.v1` | An asset/device is registered for reference. |
| `sfl.avamp.asset-status-changed.v1` | Asset/device status changes. |
| `sfl.avamp.external-device-linked.v1` | A vendor device ID is linked to an SFL asset reference. |

### Shared Platform Events

| Event | Trigger |
|---|---|
| `sfl.workflow.task-created.v1` | A workflow task is created. |
| `sfl.workflow.task-completed.v1` | A workflow task is completed. |
| `sfl.audit.audit-record-written.v1` | An auditable action is recorded. |
| `sfl.integration.vendor-system-health-changed.v1` | A connected vendor system changes health state. |
| `sfl.integration.vendor-message-received.v1` | A vendor message has been accepted into the inbox. |
| `sfl.integration.vendor-message-rejected.v1` | A vendor message fails validation or security checks. |

## Inbox and Outbox Rule

All events produced by SFL should be written to the local outbox in the same transaction as the business data change. Worker processes then publish pending outbox messages to RabbitMQ.

All inbound vendor or external-system messages should be written to the inbox before business processing. Consumers must be idempotent and must not process the same external message twice.

## Kafka Position

Kafka is not part of the Phase 1 implementation baseline. If future analytics or high-retention streaming requires Kafka, it should be added as a separate adapter behind the same messaging abstractions.
