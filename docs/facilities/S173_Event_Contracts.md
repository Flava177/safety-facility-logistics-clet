# S173 Event Logistics & Set-Up Workflow - Event Contracts

- Service: `sfl-facilities-service`
- Outbox table: `facilities.outbox_messages` (via `ServiceOutbox.record`)
- Requirements: `SRS-SFL-S173-01..04`
- Naming: `sfl.ifimp.<event-name>.v1` (SRS 2026/002 CORR-04, single catalogue scheme)

## Status

Published events are recorded to the outbox in the same transaction as the change, exactly as every
other Phase 2 IFIMP event in this service is. Whether a drainer has shipped them to a broker by the
time this is read is a platform (S217) question, not an S173 one - see the outbox's own documentation
for the current delivery state. This document is the contract: name, when it fires, and payload shape.

Every payload carries references and classifications by value, never a free-text description that
could carry PII, and never a secret.

## Outbound events

### `sfl.ifimp.event-handoff-accepted.v1`

Raised when a signed CCP Events (S078) hand-off passes verification and is recorded in the register -
whether it created, updated, cancelled a set-up task, left one unchanged, or found the task already
closed. **Not raised for a rejected hand-off** - see `EVENT_HANDOFF_REJECTED` in the audit trail and
`sfl.integration.vendor-message-rejected.v1` (the shared vendor-rejection event) instead.

Aggregate: `EventHandoff`. Payload:

```json
{
  "handoffId": "uuid", "s078EventReference": "S078-1044", "s078Status": "CONFIRMED",
  "action": "CREATED", "setupTaskId": "uuid", "taskReference": "EV-MAIN-000042",
  "taskStatus": "OPEN"
}
```

`action` is `CREATED`, `UPDATED`, `UNCHANGED`, `CANCELLED` or `IGNORED_TASK_CLOSED`.

### `sfl.ifimp.event-setup-task-created.v1`

Raised once per set-up task, the moment the first confirmed hand-off for an S078 event creates it -
SRS-SFL-S173-01's "a set-up task is created... without manual re-entry of the event's details."

Aggregate: `EventSetupTask`. Payload: `taskId`, `taskReference`, `s078EventReference`, `eventCategory`,
`startsAt`, `endsAt`, `roomCode`, `expectedAttendance`, `status`.

### `sfl.ifimp.event-resource-request-status-changed.v1`

Raised on every status transition of a resource request - routed, conflicted, confirmed, fulfilled,
requeued after the event moved, or cancelled. This is the event a consolidated-dashboard consumer
(or the S169/S153/S159 owning systems themselves, in the other direction) would subscribe to for
"what changed on this event's resourcing."

Aggregate: `EventResourceRequest`. Payload:

```json
{
  "resourceRequestId": "uuid", "setupTaskId": "uuid", "resourceType": "AV", "owningSystem": "S159",
  "status": "CONFIRMED", "externalReference": "uuid-of-the-s159-allocation",
  "manualCoordination": false, "previousStatus": "REQUESTED"
}
```

### `sfl.ifimp.event-manual-coordination-recorded.v1`

Raised when a request first becomes a manual-coordination item (its owning system is not built or
switched off - S173-02) **and** again when a named person accepts it (S173-04). The
`manualCoordination: true` and, once accepted, `acceptedBy` fields are what a dashboard reads to show
the item as marked rather than silently missing.

Aggregate: `EventResourceRequest`. Payload: the same shape as
`event-resource-request-status-changed`, plus `acceptedBy` once accepted.

### `sfl.ifimp.event-setup-task-confirmed.v1`

Raised when a set-up task moves to `CONFIRMED` - after the higher-risk gate (S173-03) has been
checked and passed, or found not to apply.

Aggregate: `EventSetupTask`. Payload: the task facts (as above) plus `higherRisk` (boolean),
`riskTriggers` (array of `LARGE_ATTENDANCE`/`EXTERNAL_CONTRACTORS`/`TEMPORARY_STRUCTURES`/
`HIGHER_RISK_CATEGORY`), `riskAssessmentId`, `riskAssessmentVersion`.

### `sfl.ifimp.event-readiness-escalated.v1`

Raised by the escalation sweep, once per event per sweep that finds unresolved requests - SRS-SFL-
S173-04's "notified before the event, not after." Also the notification intent, since IFIMP has no
notification provider (see the runbook).

Aggregate: `EventSetupTask`. Payload:

```json
{
  "setupTaskId": "uuid", "taskReference": "EV-MAIN-000042", "s078EventReference": "S078-1044",
  "resourceRequestIds": ["uuid", "uuid"], "resourceTypes": ["AV", "SECURITY"],
  "notifiedTo": "coordinator.actor.id", "beforeEventStart": true, "windowMinutes": 2880
}
```

### `sfl.ifimp.event-reconciliation-recorded.v1`

Raised once per `POST .../reconciliation` call - SRS-SFL-S173-04's post-event step.

Aggregate: `EventSetupTask`. Payload: `setupTaskId`, `taskReference`, `linesRecorded` (count),
`gaps` (array of `"<resourceType>:<outcome>"` for every non-`DELIVERED` line).

## Inbound events - the S165 projection

SRS-SFL-S173-03: the linked risk assessment must be current, checked by the shared
`RiskAssessmentCurrency` rule S164 will also use. S165 lives in `sfl-safety-security-service`, a
different deployable, so S173 keeps a local projection (`facilities.event_risk_assessments`) fed by
these four reserved event names, consumed through `RiskAssessmentEventsHandler`
(`shared.application.integration.IntegrationEventHandler`).

**Nothing in this build publishes these events. S165 is not built.** The names below are this
service's own reservation, written so the S165 build (in SSEMP) has an exact contract to publish
against; until it exists, `RiskAssessmentEventsHandler` never runs, the projection stays empty, and
every higher-risk confirmation attempt is refused (`EVENT_RISK_ASSESSMENT_NOT_CURRENT`, reason
`NONE_LINKED`) - fail-closed by construction, not by omission. See the gap report.

| Event | Expected payload |
|---|---|
| `sfl.ssemp.risk-assessment-published.v1` | `assessmentId`, `version` (int), `siteCode`, `riskLevel` (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`), `reviewDueAt` (ISO instant), `authorId`, `signedOffBy` (nullable) |
| `sfl.ssemp.risk-assessment-superseded.v1` | `assessmentId`, `version` - the version being superseded |
| `sfl.ssemp.risk-assessment-review-lapsed.v1` | `assessmentId`, `version`, `reviewDueAt` (the date that passed) |
| `sfl.ssemp.risk-assessment-signed-off.v1` | `assessmentId`, `version`, `signedOffBy`, `reviewDueAt` (the renewed date, optional) |

Handling notes for the S165 build:
- `published` marks every earlier version of the same `assessmentId` superseded, and is itself marked
  superseded on arrival if a later version is already known - so out-of-order delivery cannot make an
  assessment look more current than it is.
- `review-lapsed` only ever brings the review date **forward**, never back.
- A payload missing `assessmentId` or `version` is logged and dropped, not retried forever (see
  `IntegrationEventHandler`'s own contract on this point).
- `RiskAssessmentEventsHandler` is registered on the existing inbound queue, which already binds
  `ssemp.#` (`FacilitiesInboundMessaging`); no new binding is needed once S165 exists and publishes
  under this prefix.
