# S159 Room and Resource Booking — design

The third IFIMP system, built on the S152 platform alongside S153. It shares the estate register, the
audit chain, the idempotency store, the runtime configuration and the permission matrix; it adds six
tables, five application services and one database constraint that is the reason the module works.

Everything below is a decision with a consequence. Where a choice could reasonably have gone the
other way, the reason it did not is recorded.

---

## 1. The one thing that matters

**A space cannot be double-booked.** Every other rule in S159 is a convenience beside that one, and
it is the only rule that cannot be enforced in Java.

The application checks for conflicts before it writes, and that check earns its place — it produces
the message a requester can act on: *"HALL-A is already held by BK-MAIN-000001 from 09:00 to 11:00."*
It is not a guarantee. Two requests can both read an empty diary before either writes, and no amount
of care in `BookingApplicationService` changes that.

The guarantee is in `V10`:

```sql
CONSTRAINT ux_bookings_no_double_booking EXCLUDE USING gist (
    room_id WITH =,
    tstzrange(occupied_from, occupied_to, '[)') WITH &&
) WHERE (status IN ('REQUESTED', 'CONFIRMED', 'IN_USE'))
```

Three details, each load-bearing:

- **`'[)'` — half-open.** A booking ending at 10:00 and one starting at 10:00 do not overlap. Get
  this wrong one way and every back-to-back lecture reports a phantom clash until people stop
  trusting the check; wrong the other way and the hall is double-booked on the hour, which is
  precisely when lectures change over. Written explicitly rather than left to `tstzrange`'s default,
  which somebody could otherwise assume.
- **`occupied_*`, not `starts_at`/`ends_at`.** The widened window, including setup and teardown.
  Booking against the bare window lets the next booking start while the chairs are still being moved.
- **The `WHERE` clause** lists the three statuses `BookingStatus.holdsTheSpace()` returns true for.
  Two expressions of one rule in two languages with no compiler between them, so
  `S159MandatoryScenariosTest` reads `V10` off the classpath and asserts they match.

`btree_gist` is required, because a GIST index can handle the range overlap out of the box but not
UUID equality. It is a trusted extension from PostgreSQL 13, so the database owner can install it
and the migration does. If that line fails the module cannot offer its central guarantee, and failing
loudly at migration time is the right outcome — the alternative is a service that starts and
double-books.

### What it cost, and what it bought

Measured against a real database, sixteen simultaneous requests for one hall:

| | Bookings created | Losers get |
|---|---|---|
| Constraint only | 1 | 15 × HTTP 500 (`SQLSTATE 40P01`, deadlock) |
| Constraint + advisory lock | 1 | 15 × HTTP 409 `BOOKING_CONFLICT` |

The deadlock is inherent: each transaction inserts its row, then has to check the constraint against
the others' uncommitted rows, and they end up waiting on each other in a cycle. The correctness was
never in doubt — one booking either way — but a requester told "internal server error" has nothing
to do next. `BookingRepository.lockSpace` takes a transaction-scoped advisory lock on the room before
the conflict check, so same-space requests queue and the second one reads a diary that already
contains the first. Different spaces are unaffected. The deadlock translation stays as a backstop.

---

## 2. The aggregates

Seven records, from SRS-SFL-S159-01. Every one carries `RecordMetadata` — created by/at, last
modified by/at, version, source channel, correlation id — and is scoped to a site.

| Record | What it is | Notes |
|---|---|---|
| `Booking` | A reservation of a space for a window | The aggregate root |
| `BookableResource` | A portable thing booked alongside a room | Not an S152 asset — see below |
| `ResourceAllocation` | A resource attached to a booking, for that window | Carries a copy of the window |
| `BookingApproval` | A decision on a request, with who and why | An event, not a status |
| `SetupTask` | Room turnaround before a booking | Deliberately not an S153 work order |
| `ReadinessHold` | Why a booking is at risk | A **flag on the booking**, not a table |
| `NoShowRecord` | A booking held and never used | Written by the sweep, in the same transaction |

### `BookingStatus`

```
REQUESTED ──→ CONFIRMED ──→ IN_USE ──→ COMPLETED
    │             │            │
    ├─→ REJECTED  ├─→ NO_SHOW  └─→ CANCELLED
    └─→ CANCELLED └─→ CANCELLED
```

**A request already holds the space.** `REQUESTED` occupies the room exactly as `CONFIRMED` does.
The obvious alternative — let anybody request anything, resolve clashes at approval — fails in a way
that is hard to undo: three people request the same hall on Tuesday, all three are told "requested",
two plan around a room they will not get, and the approver is handed a conflict to arbitrate rather
than a decision to make. Refusing the second request at the moment it is made is unkinder for one
second and kinder for the following week.

**There is no `APPROVED` state.** Approval is an *event*, recorded as a `BookingApproval`. A booking
that has been approved is confirmed; there is nothing further to do to it. An `APPROVED` state
between the two would differ from `CONFIRMED` only in that somebody has not yet pressed a second
button, and that button does not exist. Bookings needing no approval go `REQUESTED → CONFIRMED`
directly, and the *absence* of a `BookingApproval` row is what records that they needed none.

**`NO_SHOW` is terminal and automatic.** Reached only by the sweep. "They did not turn up" is an
observation, and letting it be asserted by hand would make it an accusation.

### The readiness hold is a flag, not a state

`readinessHoldReason` sits beside the status rather than inside it. A confirmed booking on a space
that has just been blocked is *still a confirmed booking* — somebody has it in their diary and is
planning around it. Moving it to `AT_RISK` would mean deciding, on the estate's behalf, that a hall
blocked on Tuesday will still be blocked on Friday. It usually will not be.

So the booking keeps its status and gains a visible reason, the space keeps its own readiness, and a
human decides whether to move the booking. When the space recovers, the flag clears with no state
change and nobody has to be told twice.

---

## 3. The window, and the buffers

`BookingWindow` is `[start, end)` with `setupMinutes` and `teardownMinutes`. `occupied()` widens by
both, and **that** is what conflict is tested on.

The buffers are on the *window* rather than on the *space*, because they are a property of what is
being done rather than of the room: the same hall needs no reset for a two-hour meeting and half an
hour for an examination. Defaults come from runtime configuration per purpose — zero for an ordinary
booking, thirty minutes each side for an examination.

A window is refused if it is inverted, zero-length, longer than fourteen days, already finished, or
beyond the site's booking horizon. A start slightly in the past is allowed: somebody recording a
session that has just begun is doing something reasonable.

---

## 4. Readiness — one function, two jobs

`ReadinessHoldPolicy.holdFor(...)` answers "is the state of this space a reason not to use it?" and
is asked at two moments meaning different things:

- **At request time** a non-null answer is a **refusal**. Overridable, with a recorded reason, by an
  actor holding `FACILITIES_BOOKING_OVERRIDE`.
- **Afterwards** a non-null answer is a **hold**. The booking already exists and keeps its status.

Two functions would eventually disagree, and the disagreement would show up as a booking that could
be made but was permanently held, or one refused for a condition no sweep ever flagged.

The ladder, most severe first:

| Condition | Reason |
|---|---|
| Not bookable, or lifecycle not active | `SPACE_WITHDRAWN` |
| Readiness `BLOCKED` | `SPACE_BLOCKED` |
| Examination, and not `READY` or not examination-capable | `NOT_EXAMINATION_READY` |
| Readiness-locked, and this is not an examination | `LOCKED_FOR_EXAMINATION` |

`DEGRADED` does not block an ordinary booking. S152 already made that call in
`FacilityRoom.availableForBooking`: a hall with one failed projector is usable and refusing it would
be worse than warning about it. An examination is held to the stricter standard because "probably
fine" is not something an examination centre can run on.

### Why readiness reaches bookings by a sweep, not a port

S153 tells readiness about a fault synchronously, through `ExternalBlockerPort`. The symmetric move
here would be a port readiness calls when a space changes, so a hall blocked at 09:00 flags its
bookings at 09:00.

It is the wrong shape, and the reason is the dependency arrow. Booking depends on the estate and on
readiness — it reads a space to decide whether it can be used. A port pointing back would make
readiness depend on bookings, the same inversion the S152 architecture test exists to prevent, and it
would mean *assessing a space* could fail because a booking three weeks out was in an unexpected
state.

So `BookingReconciliationService.sweepReadinessHolds` runs on a timer instead. The cost is latency
rather than correctness: a hall blocked at 09:00 has its bookings flagged by 09:15. Both sweeps are
idempotent, so running twice, or on two instances at once, changes nothing.

---

## 5. Resources

Deliberately a separate register from S152 `FacilityAsset`. An asset is fixed plant whose condition
feeds a space's readiness; a resource is portable and its *scarcity* is the point. The same projector
can be an asset while bolted to a ceiling and a resource once it is on a trolley, and `assetId` links
the two as a value rather than a foreign key.

One row for a set of forty chairs, not forty rows. A quantity of exactly one makes a resource
**exclusive**, and exclusivity is what the database can enforce:

| | Enforced by | Under concurrency |
|---|---|---|
| One projector | `ux_booking_allocations_exclusive` | Guaranteed |
| Forty chairs | `BookingConflictPolicy` arithmetic | Best effort |

An exclusion constraint can say "these two rows may not overlap". It cannot say "the quantities of
the overlapping rows must sum to no more than forty". The gap is real and recorded rather than
papered over: two concurrent requests for the last twenty of forty chairs can both succeed. That is
a chair shortage found at setup, not a hall double-booked at examination time, and closing it would
mean serialising every booking in the estate behind one lock.

Reducing a resource's quantity below what is already allocated is **allowed**. Refusing would be the
tidier rule and the wrong one: the chairs are genuinely gone, and a register that insists otherwise
has stopped describing the estate. The oversubscription surfaces on the availability screen, where a
human decides which booking loses out — a decision, not an arithmetic error.

---

## 6. Setup tasks are not work orders

The obvious move is to raise an S153 work order and get the queue, the SLA and the closure evidence
for free. It is the wrong move, and the reason is what would end up in that queue.

A setup task is a twenty-minute room turnaround — chairs into examination layout, a projector wheeled
in, water on the table. Routing that through the CMMS would put it in the same queue as a failed
standby generator, give it an escalation ladder, and demand closure evidence before anybody could say
the chairs were straight. The queue would fill with turnarounds and the generator would be on page
four.

So `SetupTask` stays thin: what, by when, done or not, who did it. If a setup reveals something
actually broken, that is a fault, and S153 already has a register for it.

The queue is ordered by **when the room is needed**, not when the task was raised. A task for this
afternoon matters more than one raised last week for next month.

---

## 7. No-shows

A confirmed booking still unstarted a configured grace period after it should have begun is marked
`NO_SHOW`, its resources released and its outstanding setup work skipped — all in one transaction,
alongside a `NoShowRecord`.

**The grace is measured from the start, not the end.** A three-hour lecture nobody attended should
not hold a hall for three hours; twenty minutes is long enough to cover a late start and short enough
that the room is recoverable. The trade is real and is why the value is configurable: arriving at
minute twenty-five finds the booking gone, because `NO_SHOW` is terminal. A site that runs late by
habit should raise the grace rather than work around it.

`NoShowRecord` is written by the sweep rather than inferred later from a `NO_SHOW` booking, because
it captures what the status cannot: **the room-time the booking took out of the diary**. That is the
figure a no-show policy is written to answer, and reconstructing it from bookings means re-deriving
it every time somebody asks — and archiving a booking would silently change the answer.

The grace period is applied in memory rather than in the query, because it is site-scoped runtime
configuration and one SQL statement cannot carry a different threshold per row. The candidate set
stays small: it drains as fast as it fills.

---

## 8. Authorisation

Eight permissions, granted through the same `FacilitiesPermissionMatrix` S152 and S153 use.

| Permission | Held by |
|---|---|
| `FACILITIES_BOOKING_READ` | Every facilities-facing role, plus the requester (narrowed) |
| `FACILITIES_BOOKING_REQUEST` | Requester, manager, director, supervisor, centre manager |
| `FACILITIES_BOOKING_APPROVE` | Manager, director, supervisor, centre manager, command |
| `FACILITIES_BOOKING_CANCEL` | The same, minus the requester |
| `FACILITIES_BOOKING_OVERRIDE` | **Centre manager, command, director, administrators only** |
| `FACILITIES_RESOURCE_READ` / `_MANAGE` | Read widely; manage by facilities roles |
| `FACILITIES_SETUP_TASK_MANAGE` | Technician, supervisor, manager, centre manager |

Four rules are worth naming:

- **The requester is the busiest role in this module**, where it was the narrowest in S153. A
  requester is exactly the person who books a room. `FACILITIES_BOOKING_READ` is narrowed per record
  to their own bookings, the same treatment `FACILITIES_FAULT_READ` gets and for the same reason — a
  full room diary would tell somebody which halls are empty and when.
- **`FACILITIES_BOOKING_CANCEL` is the "act on somebody else's booking" grant.** It covers
  cancelling, moving and marking in use. Acting on your own booking needs none of it. A second
  permission that always travelled with it would be ceremony.
- **The maintenance supervisor holds `FACILITIES_READINESS_OVERRIDE` and deliberately not
  `FACILITIES_BOOKING_OVERRIDE`.** The two would be redundant and the redundancy is harmful: a
  supervisor who needs a blocked hall used should clear or downgrade the blocker — which leaves a
  readiness record somebody can review — rather than book past it and leave the hall still reading
  `BLOCKED` to everyone else.
- **An actor may not approve their own booking request**, administrators included. It is the one
  thing separation of duties exists to stop and it costs one line.

The vendor technician holds nothing here. A contractor is not staff and does not book CLET's rooms.

---

## 9. The migration

`V10` adds six tables and touches nothing existing — unlike S153, which inherited a pre-platform
faults spine, booking is new work with no rows to preserve.

`occupied_from` and `occupied_to` are derived state, stored. They are not `GENERATED ALWAYS` columns,
which would have been tidier and is not available: `timestamptz - interval` is `STABLE` rather than
`IMMUTABLE` in PostgreSQL, because adding days or months depends on the session time zone, and a
stored generated column may only use immutable expressions. So the application writes them, and

```sql
CONSTRAINT ck_bookings_occupied CHECK (occupied_from <= starts_at AND occupied_to >= ends_at)
```

catches the one way that can go wrong. A change that widened the buffers but forgot these columns
would otherwise let the next booking start early, silently.

Default runtime configuration is seeded so the module works on a database nobody has run a seed
script against.

---

## 10. Scheduling

| Job | Interval | Why that interval |
|---|---|---|
| Readiness reconciliation | 15 minutes | Advisory. A flag a human will look at; latency costs nothing. |
| No-show sweep | 5 minutes | **Releases a space.** Every minute between the grace expiring and the sweep noticing is a minute the hall is unusable by anybody else. |

Both catch and log their own exceptions. An uncaught exception from a `fixedDelay` task cancels the
schedule for the life of the process, so one bad row would silently stop every future sweep — the
failure mode least likely to be noticed, on the job whose whole purpose is to notice things.

---

## 11. Tests

290 tests in the service, 61 of them S159:

- `BookingWindowTest` — the half-open boundary, in fifteen cheap assertions. The most valuable file
  in the module relative to its size.
- `BookingConflictPolicyTest` — space and resource clashes as arithmetic, no database.
- `ReadinessHoldPolicyTest` — the ladder, and that every reason can be explained to a requester.
- `S159MandatoryScenariosTest` — the SRS acceptance criteria end to end through the application
  services, including `DatabaseAgreesWithTheDomain`, which reads `V10` off the classpath and pins the
  exclusion constraint's status list against `BookingStatus.holdsTheSpace()`.

What the tests cannot cover is in `S159_Gap_And_Conflict_Report.md` §4, along with what running it
against a real database found.
