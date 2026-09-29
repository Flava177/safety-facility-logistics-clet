# S169 Cleaning & Janitorial Schedule Management - Event Contracts

- Service: `sfl-facilities-service`
- Outbox table: `facilities.outbox_messages` (`ServiceOutbox.record`)
- Requirements: `SRS-SFL-S169-01..04`
- Naming: `^sfl\.[a-z0-9]+\.[a-z0-9-]+\.v\d+$` (SRS CORR-04's single catalogue convention, adopted by
  every Phase 2 module - not the pre-Phase-2 `ifimp.<aggregate>.<event>` scheme S152's own events doc
  predates this convention with)

## Status

Recorded, not yet delivered - the same honest state every Phase 2 module is in: the outbox row commits
in the same transaction as the change it describes, and a drainer will publish it once one exists for
this service. See `docs/facilities/S152_Event_Contracts.md` §Status for the platform-wide picture.

## Envelope

| Field | Source |
|---|---|
| `eventType` | one of the names below |
| `eventVersion` | `1` for every event in this document |
| `aggregateType` | `CleaningTask`, `CleaningLowRatingFlag`, `CleaningSlaBreach`, `CleaningCapacityReservation` |
| `aggregateId` | the aggregate's UUID |
| `siteScope` | the record's site code |
| `correlationId` | the acting actor's correlation id |
| `causationId` | the acting actor's id |
| `payload` | a purpose-shaped map - references and classifications only, never a comment, a description or a person's name beyond an id |

## Published

| Event | Raised when | Payload keys |
|---|---|---|
| `sfl.ifimp.cleaning-task-created.v1` | A task is raised - routine (by the sweep), booking setup/teardown (by the S159 observer), reactive (an occupant), event (S173's reservation), or ad-hoc | `taskId, taskNumber, siteCode, roomId, roomCode, spaceType, origin, status, windowStart, dueBy`, plus whichever of `bookingId/bookingReference/reservationId/eventReference/scheduleId` apply |
| `sfl.ifimp.cleaning-task-completed.v1` | A task moves to `COMPLETED` - only once every required checklist item and photo is addressed | same shape as above, `status=COMPLETED`, `completedAt` |
| `sfl.ifimp.cleaning-task-cancelled.v1` | A task moves to `CANCELLED` - by a supervisor, an occupant withdrawing their own unassigned request, a booking withdrawal, or an S173 release | same shape, `status=CANCELLED`, `cancelledAt` |
| `sfl.ifimp.cleaning-low-rating-flagged.v1` | Repeated low ratings for one space or vendor reach the configured threshold within the configured window - only when the flag **opens**, not on every subsequent low rating while it stays open | `flagId, siteCode, subjectType, subjectId, lowRatingCount, windowStart, lastFeedbackId` |
| `sfl.ifimp.cleaning-sla-breach-recorded.v1` | A response, completion or quality breach is computed against a vendor's contracted terms - from the task's own timestamps and occupant ratings, never a vendor's own claim | `breachId, vendorId, taskId, taskNumber, slaTermsId, slaTermsVersion, breachType, contractedValue, actualValue, origin` |
| `sfl.ifimp.cleaning-capacity-reserved.v1` | S173's `reserve` call finds capacity free and raises the linked task | `reservationId, siteCode, roomId, windowFrom, windowTo, eventReference, status=RESERVED, taskId, taskNumber` |
| `sfl.ifimp.cleaning-capacity-conflict.v1` | S173's `reserve` call finds capacity already committed | `reservationId, siteCode, roomId, windowFrom, windowTo, eventReference, status=CONFLICT, competingTaskId, competingFrom, competingTo` (the `competingCommitment` prose itself is on the stored reservation row and in the contract's return value, not duplicated into the event payload) |

**Deliberately not an event of its own:** a capacity `release` (audited as `CLEANING_CAPACITY_RELEASED`)
and a checklist item being recorded are not published - the brief's catalogue names the seven events
above, and every other state change is visible through `cleaning-task-*` or is a read-model concern
(the dashboard, the capacity view) rather than an integration signal another system needs pushed to it.

## Consumed

None. S169 has no `IntegrationEventHandler` and no signed vendor-inbox endpoint - it has no reactive
integration with anything outside this service's own transaction boundary. The only inbound
cross-module call is the in-process `EventCleaningCapacity` contract from S173 (same deployable, not an
event) and the in-process `BookingLifecycleObserver` call from S159 (same deployable, not an event).
