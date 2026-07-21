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
