# S166 Fleet and Vehicle Management — Event Contracts

Naming follows `docs/integration/event-catalog.md` exactly: `sfl.{platform}.{event-name}.v{version}` with
platform `ftlmp`. Routing key is `{platform}.{event-name}.v{version}`; exchange `sfl.events`; dead-letter
`sfl.events.dlx`. See conflict **C-03** (two naming schemes existed) and **C-04** (catalog did not cover the
S166 lifecycle) in `S166_Gap_And_Conflict_Report.md`.

`FleetEventTypeTest` asserts every constant matches `^sfl\.ftlmp\.[a-z0-9-]+\.v\d+$` and that the numeric
`eventVersion` column always equals the `.vN` suffix, so the two can never drift.

## Envelope

Written to `fleet_logistics.outbox_messages` inside the same transaction as the state change, then drained to
RabbitMQ by `OutboxDrainer`.

| Field | Source |
|---|---|
| `eventId` | UUID minted in-process |
| `eventType` | constant from `FleetEventType` |
| `eventVersion` | integer mirroring the `.vN` suffix |
| `aggregateType`, `aggregateId` | emitting aggregate |
| `occurredAt` | business time (UTC) |
| `publishedAt` | set by the drainer |
| `siteScope` | site code of the record |
| `actorId` / `sourceSystem` | acting user, or the integration source for ingested events |
| `correlationId`, `causationId` | propagated from `X-Correlation-ID` / the causing command or message |
| `traceParent` | W3C trace context |
| `schemaVersion` | payload schema version, independent of the event version |
| `payload` | data-minimised JSON — identifiers, status and codes only; **never** licence numbers, VIN, personal data or evidence content |

## Fleet events

| Event type | Emitted when | Key payload | Catalog status |
|---|---|---|---|
| `sfl.ftlmp.vehicle-created.v1` | vehicle registered | vehicleId, registrationNumber, siteCode, category, responsibleUnit, lifecycleStatus | existing |
| `sfl.ftlmp.vehicle-updated.v1` | mutable vehicle attributes change | vehicleId, changedFields[], version | **added (C-04)** |
| `sfl.ftlmp.vehicle-lifecycle-changed.v1` | lifecycle transition | vehicleId, fromStatus, toStatus, reason | **added** |
| `sfl.ftlmp.vehicle-readiness-changed.v1` | readiness status changes | vehicleId, fromStatus, toStatus, blockerCodes[] | existing |
| `sfl.ftlmp.vehicle-availability-changed.v1` | availability changes (incl. became unavailable) | vehicleId, fromStatus, toStatus, cause | **added** |
| `sfl.ftlmp.vehicle-compliance-expiring.v1` | document enters the warning window | vehicleId, documentId, documentType, expiresOn, daysRemaining | **added** |
| `sfl.ftlmp.vehicle-compliance-expired.v1` | document expires | vehicleId, documentId, documentType, expiresOn | **added** |
| `sfl.ftlmp.vehicle-service-due.v1` | service becomes due | vehicleId, dueOn, dueOdometer | **added** |
| `sfl.ftlmp.vehicle-service-overdue.v1` | service becomes overdue | vehicleId, dueOn, overdueByDays | **added** |
| `sfl.ftlmp.driver-registered.v1` | driver profile reference created | driverId, staffReference, siteCode, eligibilityStatus | **added** |
| `sfl.ftlmp.driver-eligibility-changed.v1` | eligibility changes | driverId, fromStatus, toStatus, blockerCodes[] | **added** |
| `sfl.ftlmp.vehicle-assigned.v1` | trip assigned to vehicle + driver | tripId, vehicleId, driverId, plannedStart, plannedEnd, operatingMode | **added** |
| `sfl.ftlmp.trip-reassigned.v1` | vehicle or driver replaced | tripId, previousVehicleId, previousDriverId, vehicleId, driverId, reason | **added** |
| `sfl.ftlmp.trip-cancelled.v1` | trip cancelled | tripId, reason, cancelledBy | **added** |
| `sfl.ftlmp.trip-completed.v1` | trip closed | tripId, endOdometer, closureReason, closureEvidenceId | **added** |
| `sfl.ftlmp.vehicle-inspection-failed.v1` | inspection result `FAILED` or a critical defect found | inspectionId, vehicleId, tripId, defectCodes[], severity | **added** |
| `sfl.ftlmp.fleet-workflow-escalated.v1` | SLA breach or manual escalation | workflowItemId, escalationLevel, slaDueAt, escalationRole, reason | **added** |
| `sfl.ftlmp.fleet-evidence-registered.v1` | evidence reference registered | evidenceId, evidenceType, relatedWorkflowId, retentionClass, hashAlgorithm | **added** |
| `sfl.ftlmp.fleet-audit-integrity-failed.v1` | audit chain replay fails — **critical compliance alert** | firstDivergentSequence, expectedHash, actualHash, checkedAt | **added** |
| `sfl.ftlmp.vehicle-location-received.v1` | telematics position ingested | vehicleId, recordedAt, latitude, longitude, speed, sourceSystem | existing |

## Consumed events

| Event | Reaction | Idempotency |
|---|---|---|
| `sfl.ftlmp.vehicle-location-received.v1` (own, via inbox) | update last-known position + telematics freshness | `(source_system, idempotency_key)` |
| *(seam only)* `sfl.avamp.external-device-linked.v1` | link a telematics device to a vehicle | `eventId` in `inbox_messages` |

Consumers write the message id to the inbox **before** processing and are safe under at-least-once delivery.

## Out-of-scope events (seams only, not published by this slice)

`sfl.ftlmp.fuel-transaction-received.v1`, `sfl.ftlmp.fuel-exception-detected.v1` (S168_fuel);
`sfl.ftlmp.dispatch-created.v1`, `sfl.ftlmp.dispatch-received.v1` (S171).
