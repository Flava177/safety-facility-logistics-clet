# S158 Space Planning & Move Management - Event Contracts

- Service: `sfl-facilities-service`
- Outbox table: `facilities.outbox_messages` (shared with every module in this service)
- Requirements: `SRS-SFL-S158-01..04`

## Status

Recorded, not yet delivered. Every event below is written to the outbox in the same transaction as the
state change it reports, through `ServiceOutbox.record`. This service has no drainer, so nothing
reaches the broker yet - the same honest gap `S152_Event_Contracts.md` records, carried forward for
Phase 2 (ADR 0009 §6 "Cross-service consumers"). A consumer cannot subscribe to any of these yet.

## Naming

`sfl.ifimp.<event-name>.v1`, the single Phase 2 catalogue scheme (SRS CORR-04), enforced by a pattern
check on `ServiceOutbox.record`. Every event below is version 1.

## Envelope

| Field | Source |
|---|---|
| `eventType` | the names below |
| `eventVersion` | `1` |
| `aggregateType` | `AllocationScenario`, `SpaceAllocationChange`, `OccupancyOverride`, `UtilisationSignal`, `SpaceChangeRequest` |
| `aggregateId` | the aggregate's UUID |
| `siteScope` | the record's site code |
| `correlationId` | the request's `X-Correlation-ID`, or the scheduler's per-run id |
| `causationId` | the acting actor's id (a person, or a system principal for a scheduled run) |
| `payload` | a purpose-shaped record - references and classifications, never the request's free-text justification or an occupant's name |

## Catalogue

### Scenarios and the S152 register - `SRS-SFL-S158-01`

| Event | Raised when | Payload |
|---|---|---|
| `sfl.ifimp.space-scenario-committed.v1` | a scenario is committed - the explicit, named, audited act | `{scenarioId, planReference, versionNumber, siteCode, commitOutcome, committedBy, committedAt, roomIds, flaggedRooms, projectId, projectReference, spaceChangeRequestId}` |
| `sfl.ifimp.space-scenario-handed-over.v1` | S176 confirms handover and the scenario's allocations are applied | `{scenarioId, planReference, versionNumber, siteCode, projectId, projectReference, confirmedBy, handedOverAt, roomIds}` |
| `sfl.ifimp.space-allocation-applied.v1` | a committed change (like-for-like commit, or S176 handover) is applied to the S152 register | `{scenarioId, scenarioReference, siteCode, roomIds, allocationsStarted, allocationsEnded, appliedAt}` |

`space-allocation-applied` is the S152 event, published by `SpaceAllocationService` (masterdata), not
by an S158 class - it is the S152 register recording its own change, whatever committed scenario
caused it. It always accompanies `space-scenario-committed` for a like-for-like commit, and
`space-scenario-handed-over` for a physical-works one.

### Occupancy compliance - `SRS-SFL-S158-02`

| Event | Raised when | Payload |
|---|---|---|
| `sfl.ifimp.occupancy-override-recorded.v1` | an override is approved (the two-person act complete) | `{overrideId, scenarioId, roomId, roomCode, siteCode, headcountCovered, requestedBy, approvedBy, approvedAt}` |

Requesting an override is not published - it is a draft-time act with no operational consequence until
approved. The `reason` free text is never on the wire; it lives in the audit trail
(`OCCUPANCY_OVERRIDE_REQUESTED` / `_RECORDED`), which is the correct place for something a person wrote
to be read back.

### Utilisation reconciliation - `SRS-SFL-S158-03`

| Event | Raised when | Payload |
|---|---|---|
| `sfl.ifimp.utilisation-signal-raised.v1` | a room's utilisation first meets a signal condition | `{signalId, siteCode, roomId, roomCode, kind, status, periodEnd, utilisationRate, thresholdRate}` |
| `sfl.ifimp.utilisation-signal-cleared.v1` | a previously active signal's condition no longer holds | same shape, `status = CLEARED` |

`kind` is `UNDER_UTILISED` or `PLANNED_ACTUAL_GAP` - see `UtilisationPolicy` and the runbook for what
each means precisely. No event is raised for a snapshot that neither raises nor clears anything;
`UTILISATION_SNAPSHOT_RECORDED` in the audit trail covers the routine case.

### Space-change requests - `SRS-SFL-S158-04`

| Event | Raised when | Payload |
|---|---|---|
| `sfl.ifimp.space-change-request-submitted.v1` | a request is submitted | `{requestId, reference, siteCode, status, urgency, targetRoomId, requestedBy}` |
| `sfl.ifimp.space-change-request-decided.v1` | a request is approved or declined | `{requestId, reference, siteCode, status, urgency, targetRoomId, outcomeType, linkedScenarioId, linkedProjectId, linkedProjectReference, requestedBy, decidedBy}` |
| `sfl.ifimp.space-change-request-resolved.v1` | a request is resolved with a linked outcome | same shape, `status = RESOLVED`, plus `resolvedBy` |

`justification` is never in the payload - organisational, not classified, but still free text a
requester wrote for the decider, not for an integration.

## Consumed

| Contract | Provider | How |
|---|---|---|
| `ConstructionProjectIntake` | `construction.application.contract` (S176, built in another worktree) | In-process call through `ConstructionHandoffPort` / `S176ConstructionHandoffAdapter` - both modules are in this deployable, so this is a real call, not a simulated one. |
| `BookingUtilisationReader` | `booking.application` (S159) | In-process, read-only, through `BookingUtilisationPort` / `S159BookingUtilisationAdapter`. See `SpacePlanningArchitectureTest` for the rule that no other S158 class may depend on a `booking` class. |
| `ScenarioHandover.confirmHandover` | **provided by S158** (`spaceplanning.application.contract`), called by S176 | S176's own worktree calls this in-process at handover; this module does not consume it, it implements it. |

No inbound vendor message: S158 is a pure Build module with no external device or vendor feed, so
`VendorMessageVerifier` and the forged-message test pattern do not apply here (see the gap report).
