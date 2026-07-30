# S152 CAFM / IWMS — Event Contracts

- Service: `sfl-facilities-service`
- Outbox table: `facilities.outbox_messages`
- Requirements: `SRS-SFL-S152-01`, `-04`

## Status

Events are **recorded, not yet published**. Every state change writes an outbox row in the same
transaction as the change; there is no drainer in this service, so nothing reaches a broker yet. V5
adds the delivery-state columns (`attempt_count`, `next_attempt_at`, `last_attempt_at`,
`dead_lettered_at`, `trace_parent`, `schema_version`) that a drainer will use, and
`ix_facility_outbox_claimable` is the index it will claim on.

That is the honest state and it is worth stating plainly: a consumer cannot subscribe to these yet.
Only `sfl-fleet-logistics-service` currently has an AMQP transport. See the gap report, §9.

## Envelope

Written by `ServiceOutbox.record`:

| Field | Source |
|---|---|
| `id` | minted per row |
| `eventType` | the names below |
| `eventVersion` | `1` for every event in this document |
| `aggregateType` | `Site`, `Building`, `FacilityFloor`, `FacilityRoom`, `Zone`, `DeviceReference`, `FacilityAsset`, `ReadinessChecklist`, `ReadinessAssessment`, `ReadinessBlocker` |
| `aggregateId` | the aggregate's UUID |
| `siteScope` | the record's site code |
| `correlationId` | the request's `X-Correlation-ID` |
| `causationId` | the acting actor's id |
| `payload` | the aggregate or a purpose-shaped record, as JSON |
| `status` | `PENDING` on write |

## Naming

`ifimp.<aggregate>.<past-tense-event>`. The `ifimp` prefix names the **programme**, matching the
convention the pre-S152 code already used (`ifimp.site.created`), not the service — S153 and S159 will
publish under the same prefix from the same service.

## Catalogue

### Estate — `SRS-SFL-S152-01`

| Event | Raised when | Payload |
|---|---|---|
| `ifimp.site.created` | a site is registered | `Site` |
| `ifimp.site.updated` | a site's attributes change | `Site` |
| `ifimp.site.lifecycle-changed` | a site moves through its lifecycle | `Site` |
| `ifimp.site.operating-mode-changed` | Routine ⇄ Examination | `{siteCode, from, to, reason, changedBy, changedAt}` |
| `ifimp.building.created` | a building is registered | `Building` |
| `ifimp.floor.created` | a floor is registered | `FacilityFloor` |
| `ifimp.room.created` | a space is registered | `FacilityRoom` |
| `ifimp.room.updated` | a space's attributes change | `FacilityRoom` |
| `ifimp.room.lifecycle-changed` | a space moves through its lifecycle | `FacilityRoom` |
| `ifimp.room-readiness.changed` | a space's readiness status changes | `FacilityRoom` |
| `ifimp.zone.created` | a zone is registered | `Zone` |
| `ifimp.zone.member-added` | a record joins a zone | `ZoneMembership` |
| `ifimp.zone.member-removed` | a record leaves a zone | `{memberType, memberId}` |
| `ifimp.device-reference.registered` | a device reference is registered | `DeviceReference` |

`ifimp.site.operating-mode-changed` carries a purpose-shaped payload rather than the `Site` aggregate,
because a consumer of a mode change wants the transition — from, to, why, who — not the site's current
attributes. It is the event S159 booking and S174 emergency will both care about most.

`ifimp.room-readiness.changed` is published **only when the status actually changes**, not on every
assessment. A consumer that re-planned on every re-assessment of an unchanged hall would thrash.

### Facility assets — `SRS-SFL-S152-01`, §21.1

| Event | Raised when | Payload |
|---|---|---|
| `ifimp.facility-asset.registered` | an asset is registered | `FacilityAsset` |
| `ifimp.facility-asset.updated` | an asset's attributes change | `FacilityAsset` |
| `ifimp.facility-asset.status-changed` | operational status changes | `FacilityAsset` |
| `ifimp.facility-asset.relocated` | the asset moves space | `FacilityAsset` |

`status-changed` is the one S153 will consume: an asset going out of service is what a preventive-
maintenance module reacts to.

### Readiness — `SRS-SFL-S152-01`, `-02`, `-05`

| Event | Raised when | Payload |
|---|---|---|
| `ifimp.readiness-checklist.created` | a checklist is created | `ReadinessChecklist` |
| `ifimp.readiness-checklist.updated` | a checklist changes (version bumps) | `ReadinessChecklist` |
| `ifimp.readiness-assessment.submitted` | an assessment is submitted | `ReadinessAssessment` |
| `ifimp.readiness-blocker.created` | a blocker is raised, from any source | `ReadinessBlocker` |
| `ifimp.readiness-blocker.resolved` | a blocker is closed | `ReadinessBlocker` |
| `ifimp.readiness-lock.engaged` | an examination lock is engaged | `FacilityRoom` |
| `ifimp.readiness-lock.released` | an examination lock is released | `FacilityRoom` |

## Who will consume these

Recorded now so the payload shapes are chosen against real consumers rather than guessed:

| Consumer | Events | Why |
|---|---|---|
| **S153 CMMS** | `facility-asset.status-changed`, `readiness-blocker.created` | raise a work order for an asset that failed or a blocker nobody has picked up |
| **S159 Room booking** | `room-readiness.changed`, `room.lifecycle-changed`, `site.operating-mode-changed` | withdraw a blocked space from the calendar; a centre entering examination mode changes what may be booked |
| **S162a Life-safety** | `zone.member-added/removed`, `device-reference.registered` | resolve an alarm to the spaces and zones it affects |
| **S174 Emergency** | `zone.member-*`, `site.operating-mode-changed` | resolve a recipient zone to buildings and spaces |
| **S225 Analytics** | all | readiness and estate reporting |

## Conventions a consumer must assume

- **At-least-once.** Consume idempotently; the `eventId` is the dedup key.
- **Order is not guaranteed across aggregates.** It is guaranteed per aggregate, because rows are
  written in the transaction that changed it.
- **References, not sensitive content** (§21.2). Payloads carry identifiers and metadata; nothing here
  contains evidence files or personal data beyond the actor id.
- **Additive versioning.** A field may be added within `eventVersion: 1`; removing or retyping one
  requires `v2`.
