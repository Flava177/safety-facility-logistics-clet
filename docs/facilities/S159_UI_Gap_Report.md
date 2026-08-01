# S159 room and resource booking UI — gap report

What the screens do not do, what driving them against a real database found, and what is deliberately
absent.

S159's twenty-five API paths shipped with no client at all. This module is that client.

## 1. The screens

| Screen | Route | What it is for |
| --- | --- | --- |
| `BookingDiaryPage` | `/bookings` | The register. Status, occupied window, readiness hold |
| `AvailabilitySearchPage` | `/bookings/availability` | What can take a window, and what cannot — with the reason |
| `BookingDetailPage` | `/bookings/{id}` | One booking: approvals, allocations, turnaround, every transition |
| `SetupTaskQueuePage` | `/bookings/turnaround` | Room turnaround, ordered by when the room is needed |
| `BookableResourcesPage` | `/bookings/resources` | Projectors, furniture sets, and what makes one exclusive |

Five dialogs: request, decide, reschedule, cancel, complete, resolve-setup-task, register-resource.

**The route base is `/bookings`, not `/facilities/bookings`,** and it is the only IFIMP system whose
path does not mirror its service. A lecturer booking a hall does not think of themselves as visiting
facilities, and a URL somebody can be told over the phone is worth more than one that mirrors
deployment topology.

## 2. What driving it against a real database found

Three things, none of which a green build would have shown.

**F-01 — The turnaround queue was gated on the wrong permission.** The nav item declared
`FACILITIES_SETUP_TASK_MANAGE`. `BookingSetupService.queue` gates the read on
`FACILITIES_BOOKING_READ` and reserves `SETUP_TASK_MANAGE` for raising and resolving a task. Shipping
it as written would have hidden the queue from everybody who can only look at it — which is most of
the people who need to — while the technicians who can resolve tasks saw it fine, so it would have
looked correct to whoever tested it.

Every `permission` in `navigation.ts` is now read off the service that enforces it. That field exists
precisely because the name of a permission is not evidence of what it gates.

**F-02 — `FACILITIES_BOOKING_CANCEL` does not mean "may cancel".**
`BookingApplicationService.requireMayAct` uses it as the **"may act on somebody else's booking"**
grant and routes cancel, reschedule, start and complete through it identically. The first draft of
`workflow.ts` gated reschedule and start on `FACILITIES_BOOKING_REQUEST`, which is the reading the
names invite — and would have offered a requester the Move button on a hall booked by the registry,
then had the service refuse it.

Confirmed live: a requester holding no `BOOKING_CANCEL` cancelled their own booking successfully, and
a second requester was refused `UNAUTHORIZED_SCOPE` on rescheduling it.

**F-03 — The conflict lands on the buffer, not on the booking.** A lecture booked 09:00–11:00 with a
15-minute teardown refuses a meeting at 11:05, because the occupied window runs to 11:15. Verified:

```
BOOKING_CONFLICT — HALL-A is already held by BK-CLET-HQ-000001
                   from 2026-08-05T09:00:00Z to 2026-08-05T11:00:00Z
```

Note that the message names the **booked** window while the refusal was decided on the **occupied**
one, so somebody reading it sees 11:00, asked for 11:05, and is refused. That is the service's
wording and the SRS's, so the screens do not rewrite it — they show the occupied window beside every
booking instead, on the diary row, on the detail page as its own stat card, and in the request
dialog's description. `bufferSummary` exists solely to say this out loud.

## 3. Verified against PostgreSQL, not only against tests

Per the repository's standing rule. Against `sfl_facilities_service` on 5441, with a seeded site,
two rooms and one exclusive resource:

| Behaviour | Result |
| --- | --- |
| Occupied window widened by buffers | 09:00–11:00 + 30/15 → `occupiedFrom` 08:30, `occupiedTo` 11:15 |
| Conflict on the teardown buffer | `BOOKING_CONFLICT` at 11:05 |
| Setup task auto-raised for a `requiresSetup` resource | Due 08:30, the occupied start |
| Resource with quantity 1 | `exclusive: true`, enforced by the exclusion constraint |
| Start → complete | `IN_USE` → `COMPLETED`, `holdsTheSpace` false, allocations `released: true` |
| Requester narrowing, list | Sees 1 of 2 bookings; an administrator sees both |
| Requester narrowing, by id | `UNAUTHORIZED_SCOPE — You may only view bookings you requested.` |
| Own booking, no `BOOKING_CANCEL` | Cancelled |
| Somebody else's, no `BOOKING_CANCEL` | `UNAUTHORIZED_SCOPE` |
| Examination into an unassessed hall | `SPACE_NOT_BOOKABLE`; availability reports `availableWithOverride: true`, `readinessIssue: NOT_EXAMINATION_READY` |
| Same booking with `overrideReason` | Accepted, `overridden: true`, reason recorded |
| Approving one's own request | `UNAUTHORIZED_APPROVAL — You cannot approve your own booking request.` |
| A colleague approving it | `CONFIRMED`, `approvalId` set, approval record readable |

Every field of every response matched the TypeScript DTOs with no adjustment after the first pass —
they were transcribed from `BookingResponses`, not inferred.

## 4. Not built, and why

### 4.1 No calendar grid

`GET /booking-availability/calendar` is called by nothing. The diary is a table.

A week grid is the obvious next screen and it is a real piece of work — overlapping bookings in a
single room-hour, the occupied window drawn distinctly from the booked one, and a click target that
opens the right booking. Shipping a half-grid that shows the booked window would actively mislead,
because the booked window is not the one people are refused on. The table states both.

### 4.2 Resources cannot be added to a booking after it is made

`POST /bookings/{id}/resources` and `DELETE /bookings/{id}/resources/{allocationId}` exist and have
no control. Resources are chosen in the request dialog and are read-only afterwards.

The reason is that adding one re-runs the availability arithmetic against everything else committed
for the window and can fail — and the failure needs the same "what is free" view the request dialog
gets from the availability search. Bolting a resource picker onto the detail page without that view
would produce a control that mostly fails with no explanation. Cancelling and re-requesting works
today.

### 4.3 A bookable resource cannot be edited or retired from the dashboard

`PATCH /bookable-resources/{id}` and `PATCH /bookable-resources/{id}/lifecycle` have no control. The
register is create-and-read.

Reducing a quantity below what is already allocated is deliberately **allowed** by the service — the
chairs are genuinely gone, and the oversubscription surfaces on the availability screen where a human
can decide which booking loses out. A UI for that needs to show the affected bookings at the moment
of the edit, or it is a control that silently breaks somebody's Tuesday.

### 4.4 No no-show register

`NoShowResponse` is a wire type with no endpoint behind it — `recentNoShows` on the counts response
is the only way the number surfaces, and the detail page explains the status when a booking has it.
There is nothing to list.

### 4.5 Setup tasks cannot be raised by hand

`POST /bookings/{id}/setup-tasks` has no control. Tasks are raised automatically for every allocated
resource declaring `requiresSetup`, which covers the case the SRS describes. Manual creation is for
turnaround a resource does not imply — a hall that needs re-laying between a moot and a lecture — and
that is a real requirement with no screen yet.

## 5. Deliberate, and not a gap

**No client-side filter narrows the diary.** A requester receives a shorter register because the
service narrows it per record on reads *and* writes. A filter in the browser would be a display
convention over rows that had already crossed the boundary.

**The readiness hold is a column, never a status.** A confirmed booking on a hall blocked on Tuesday
is still confirmed and still in somebody's diary. Folding the hold into the status would decide on
the estate's behalf that Tuesday's leak will still be there on Friday.

**Availability reserves nothing, and every screen says so.** Two people can both be told Hall A is
free and both request it; the first wins. Holding a space during a five-minute browse would mean the
estate's diary was mostly locked by people who had wandered off.

**Unavailable spaces stay on the availability list.** The question behind "what is free at ten?" is
almost always "can I have Hall A at ten?", and a hall absent from a list answers neither.

**Nothing recomputes what the service derives.** `holdsTheSpace`, `occupiedFrom`, `occupiedTo` and a
setup task's `overdue` all arrive on the record. A browser adding the buffers itself would eventually
disagree with the exclusion constraint, and the constraint is what decides whether two lectures can
have the same hall.

## 6. Entitlement

S159 is a `SystemCode` in `programmeModel.ts`. Ten roles are entitled to it and one is not:
`VENDOR_TECHNICIAN` is the only facilities role whose matrix entry is an explicit `EnumSet` rather
than a union with the shared `READ_ONLY` set, so adding `FACILITIES_BOOKING_READ` to that set
entitled everybody else and left the contractor out — correctly, and silently. A test now says so, so
that rebuilding `VENDOR_TECHNICIAN` on `READ_ONLY` cannot hand a contractor the estate's diary by
accident.

`IFIMP_TECHNICIAN` is entitled for a narrower reason than the rest: it holds
`FACILITIES_SETUP_TASK_MANAGE` and no booking-request permission at all. The section renders for them
with the turnaround queue and nothing that reserves a hall.
