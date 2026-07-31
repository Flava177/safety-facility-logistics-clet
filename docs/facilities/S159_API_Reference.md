# S159 Room and Resource Booking — API reference

Base path `/api/v1/facilities`. Every response is the platform envelope `{data, error}`.
`X-Correlation-ID` is honoured and echoed. `Idempotency-Key` is honoured on the two
state-**creating** POSTs and nowhere else — every other operation is a PATCH guarded by the record's
version and its state machine, so a repeat is either a no-op or an invalid-transition error, and a
key would be ceremony with no failure mode behind it.

Actor headers in development (`SFL_SECURITY_ENABLED=false`): `X-SFL-User`, `X-SFL-Roles`,
`X-SFL-Sites`, `X-SFL-Source-Channel`.

---

## Bookings — `/bookings`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/bookings` | `FACILITIES_BOOKING_REQUEST` | Holds the space immediately. Confirmed at once where no approval is required. Accepts `Idempotency-Key`. |
| `GET` | `/bookings` | `FACILITIES_BOOKING_READ` | A requester-only actor sees their own bookings whatever the filters say. |
| `GET` | `/bookings/counts?siteCode=` | `FACILITIES_BOOKING_READ` | Upcoming, awaiting approval, on readiness hold, no-shows in the last 30 days. |
| `GET` | `/bookings/{id}` | `FACILITIES_BOOKING_READ` | |
| `PATCH` | `/bookings/{id}/decision` | `FACILITIES_BOOKING_APPROVE` | Approve or reject. A rejection needs a reason. An actor may not decide their own request. |
| `PATCH` | `/bookings/{id}/schedule` | own booking, or `FACILITIES_BOOKING_CANCEL` | Moves the booking and its allocations in one transaction. Refused once `IN_USE`. |
| `PATCH` | `/bookings/{id}/start` | own booking, or `FACILITIES_BOOKING_CANCEL` | Also what stops the no-show sweep releasing the space. |
| `PATCH` | `/bookings/{id}/completion` | own booking, or `FACILITIES_BOOKING_CANCEL` | Releases every resource held. |
| `PATCH` | `/bookings/{id}/cancellation` | own booking, or `FACILITIES_BOOKING_CANCEL` | Reason required. Skips outstanding setup work. |
| `GET` | `/bookings/{id}/approvals` | `FACILITIES_BOOKING_READ` | Empty for a booking that needed none — which is what says so. |
| `GET` | `/bookings/{id}/resources` | `FACILITIES_BOOKING_READ` | |
| `POST` | `/bookings/{id}/resources` | own booking, or `FACILITIES_BOOKING_CANCEL` | Re-runs the availability arithmetic. |
| `DELETE` | `/bookings/{id}/resources/{allocationId}` | own booking, or `FACILITIES_BOOKING_CANCEL` | |
| `GET` | `/bookings/{id}/setup-tasks` | `FACILITIES_BOOKING_READ` | |
| `POST` | `/bookings/{id}/setup-tasks` | `FACILITIES_SETUP_TASK_MANAGE` | |

### `POST /bookings`

```json
{
  "roomId": "1068cab8-2625-42b6-b908-7ea040b29ea1",
  "purpose": "EXAMINATION",
  "title": "Land law paper 1",
  "description": null,
  "startsAt": "2026-08-11T09:00:00Z",
  "endsAt": "2026-08-11T12:00:00Z",
  "setupMinutes": null,
  "teardownMinutes": null,
  "expectedAttendees": 180,
  "requestedFor": null,
  "resources": { "98fbf9c5-62d0-45e0-965e-464d380fed0a": 1 },
  "overrideReason": null
}
```

`setupMinutes` and `teardownMinutes` null mean "use the site's default for this purpose" — zero for
an ordinary booking, thirty minutes each side for an examination.

`overrideReason` is only honoured when the space's readiness would otherwise refuse the booking
**and** the actor holds `FACILITIES_BOOKING_OVERRIDE`. Supplied without the permission, the booking
is refused exactly as if it had been omitted, and the attempt is audited.

Response `201`, with the derived fields a client would otherwise recompute:

```json
{
  "data": {
    "bookingReference": "BK-MAIN-000003",
    "status": "REQUESTED",
    "holdsTheSpace": true,
    "startsAt": "2026-08-11T09:00:00Z",
    "endsAt": "2026-08-11T12:00:00Z",
    "setupMinutes": 30,
    "teardownMinutes": 30,
    "occupiedFrom": "2026-08-11T08:30:00Z",
    "occupiedTo": "2026-08-11T12:30:00Z",
    "approvalRequired": true,
    "approvalId": null,
    "readinessHoldReason": null,
    "overridden": false,
    "metadata": { "createdBy": "lecturer", "version": 0, "sourceChannel": "WEB" }
  },
  "error": null
}
```

`occupiedFrom`/`occupiedTo` are returned rather than left to the client, because a client
recomputing them from the buffers will eventually disagree with the exclusion constraint about
whether two bookings clash.

---

## Availability — `/booking-availability`

| Method | Path | Permission |
|---|---|---|
| `GET` | `/booking-availability/spaces` | `FACILITIES_BOOKING_READ` |
| `GET` | `/booking-availability/resources` | `FACILITIES_RESOURCE_READ` |
| `GET` | `/booking-availability/calendar` | `FACILITIES_BOOKING_READ` |

Query parameters: `siteCode`, `from`, `to`, and optionally `purpose`, `spaceType`,
`minimumCapacity`, `setupMinutes`, `teardownMinutes`.

The buffers are accepted here because availability must be asked with the same buffers the booking
will carry — a hall that looks free for a two-hour examination is not free once thirty minutes of
layout change are added at each end.

**Unavailable spaces come back with the reason rather than being filtered out.** The question behind
"what is free at ten?" is usually "can I have Hall A at ten?", and a hall simply absent from a list
answers neither:

```json
[
  { "roomCode": "HALL-A", "free": false, "readinessIssue": null,
    "heldBy": [{ "bookingReference": "BK-MAIN-000001" }] },
  { "roomCode": "MR-1", "free": true, "readinessIssue": null, "heldBy": [] },
  { "roomCode": "HALL-B", "free": false, "readinessIssue": "SPACE_BLOCKED",
    "readinessDetail": "HALL-B has an open critical readiness blocker and cannot be used.",
    "availableWithOverride": true, "heldBy": [] }
]
```

**These endpoints reserve nothing.** Two people can both be told Hall A is free and both request it;
the first wins and the second is refused. Holding a space during a five-minute browse would mean the
estate's diary was mostly locked by people who had wandered off.

---

## Bookable resources — `/bookable-resources`

| Method | Path | Permission |
|---|---|---|
| `POST` | `/bookable-resources` | `FACILITIES_RESOURCE_MANAGE` (accepts `Idempotency-Key`) |
| `GET` | `/bookable-resources` | `FACILITIES_RESOURCE_READ` |
| `GET` | `/bookable-resources/{id}` | `FACILITIES_RESOURCE_READ` |
| `PATCH` | `/bookable-resources/{id}` | `FACILITIES_RESOURCE_MANAGE` |
| `PATCH` | `/bookable-resources/{id}/lifecycle` | `FACILITIES_RESOURCE_MANAGE` |

One row for a set of forty chairs, not forty rows. `quantity: 1` makes the resource **exclusive**,
which is what lets the database refuse a second booking of it under concurrency; the response carries
`"exclusive": true` so a client need not infer it.

---

## Setup tasks — `/setup-tasks`

| Method | Path | Permission |
|---|---|---|
| `GET` | `/setup-tasks?siteCode=&dueBefore=&limit=` | `FACILITIES_BOOKING_READ` |
| `PATCH` | `/setup-tasks/{id}/resolution` | `FACILITIES_SETUP_TASK_MANAGE` |

Ordered by when the room is needed, not when the task was raised. `dueBefore` defaults to the next
two days. Resolving with `SKIPPED` requires notes: a skipped task that says nothing cannot be told
from one nobody got to.

---

## Error states

| HTTP | Code | Meaning |
|---|---|---|
| 409 | `BOOKING_CONFLICT` | The space or a resource is already held for part of the window. Raised by the pre-write check **and** by the database's exclusion constraint — losing a race and asking late are one error state, because from the requester's side they are the same event. |
| 422 | `SPACE_NOT_BOOKABLE` | Readiness refuses this space for this purpose. Overridable with the permission and a reason. |
| 409 | `RESOURCE_UNAVAILABLE` | A resource is committed elsewhere, retired, at another site, or the pool is short. |
| 422 | `VALIDATION_FAILED` | Inverted, zero-length, over fourteen days, already finished, or beyond the booking horizon. |
| 403 | `UNAUTHORIZED_SCOPE` | Permission or site scope. Every denial is audited. |
| 403 | `UNAUTHORIZED_APPROVAL` | Approving your own request, or confirming one that needs an approval it does not have. |
| 409 | `INVALID_STATE_TRANSITION` | The state machine does not allow it — moving an in-use booking, deciding on a confirmed one. |
| 409 | `VERSION_CONFLICT` | `expectedVersion` is behind. Optional: omit it and accept last-write-wins. |
| 404 | `RECORD_NOT_FOUND` | |

`BOOKING_CONFLICT` messages name the booking that has the space:

```json
{ "data": null,
  "error": { "code": "BOOKING_CONFLICT",
             "message": "HALL-A is already held by BK-MAIN-000001 from 2026-08-10T09:00:00Z to 2026-08-10T11:00:00Z.",
             "correlationId": "a45e2d75-1a87-4495-84ce-d8ddb513fe59" } }
```

---

## Events

Published to the service outbox, version 1, aggregate type `Booking`.

| Event | When |
|---|---|
| `ifimp.booking.requested` | A booking is created |
| `ifimp.booking.confirmed` | Approved, or needed no approval |
| `ifimp.booking.rejected` | Refused by an approver |
| `ifimp.booking.rescheduled` | Moved |
| `ifimp.booking.start` / `.complete` | Taken up, and finished |
| `ifimp.booking.cancelled` | Withdrawn |
| `ifimp.booking.no-show` | The sweep released it |
| `ifimp.booking.readiness-hold-placed` / `-cleared` | The reconciliation sweep |
