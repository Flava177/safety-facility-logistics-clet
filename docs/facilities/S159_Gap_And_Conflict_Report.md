# S159 Room and Resource Booking — gap and conflict report

What is built, what is not, what running it found, and what somebody has to decide before this goes
live. Honest by design: an unrecorded gap is one that will be discovered by a user.

---

## 1. Two defects found by running it against a real database

290 unit tests were green when both of these were present. Neither is a rule anybody got wrong; both
are the database and the JVM disagreeing, which is the class of defect a test double cannot exhibit.

### 1.1 Every booking search returned HTTP 500

```
ERROR: could not determine data type of parameter $11
```

The search query used the idiom this codebase uses everywhere — `(:p is null or column = :p)` — for
its two temporal bounds. PostgreSQL rejects it. `IS NULL` gives the planner nothing to infer a type
from, so the placeholder's type has to come from the driver, and pgjdbc sends `UNSPECIFIED` when
Hibernate binds a null `Instant` as `TIMESTAMP_WITH_TIMEZONE`. String, UUID and enum parameters carry
a concrete OID, which is why the same idiom works everywhere else in this service and in
`JpaWorkOrderRepository`.

**Fixed** by making the bounds non-nullable in JPQL and substituting wide sentinels in the adapter
(`JpaBookingRepositoryAdapter.UNBOUNDED_FROM` / `UNBOUNDED_TO`). `Instant.MIN` and `Instant.MAX` are
not usable — both fall outside what `timestamptz` can represent.

The same bug was present in the no-show history query and fixed with it.

### 1.2 Fifteen of sixteen simultaneous bookings returned HTTP 500

The correctness was never in doubt — exactly one row held the slot, as designed. The **error** was
wrong:

```
ERROR: deadlock detected
  Where: while checking exclusion constraint on tuple (0,15) in relation "bookings"
SQLSTATE 40P01
```

Two transactions each insert a row for the same space, then each has to check the exclusion
constraint against the other's still-uncommitted row, and they wait on each other. PostgreSQL detects
the cycle and aborts an arbitrary victim. The adapter translated `DataIntegrityViolationException`
(the constraint firing) but not `CannotAcquireLockException` (the deadlock), so the losers got a 500.

**Fixed** two ways, both worth having:

- `BookingRepository.lockSpace` takes a transaction-scoped advisory lock on the room *before* the
  conflict check reads the diary, so same-space requests queue and the second reads a diary that
  already contains the first. Requests for different spaces are unaffected. Resources are locked the
  same way, always after the space and in a stable order, so two requests for the same pair of things
  cannot deadlock on each other.
- The translation now covers the deadlock as a backstop, reporting it as `BOOKING_CONFLICT` with
  "try again". That is a small lie in one direction — the victim is told the slot is taken when the
  transaction it lost to might itself roll back — and it leaves the requester with something to do,
  which "internal server error" does not.

| | Bookings created | Losers get |
|---|---|---|
| Before | 1 | 15 × HTTP 500 |
| After | 1 | 15 × HTTP 409 `BOOKING_CONFLICT` |

---

## 2. What running it proved

All against PostgreSQL 16 on a schema dropped and rebuilt from `V1` through `V10`.

| Check | Result |
|---|---|
| Flyway `V1..V10` on an empty schema | 10 migrations, 0.85 s |
| `btree_gist` installed by the application user | Yes — trusted extension, no superuser needed |
| Hibernate `ddl-auto: validate` against V10 | Passed; all six entities map |
| Both exclusion constraints present | `ux_bookings_no_double_booking`, `ux_booking_allocations_exclusive` |
| Overlapping rows inserted directly into the table | Refused by the constraint, transaction rolled back |
| Back-to-back rows inserted directly | Accepted — the half-open boundary holds in SQL |
| Sixteen simultaneous API requests for one slot | 1 booking, 15 × `BOOKING_CONFLICT`, 1 row holds the slot |
| Overlapping booking through the API | `BOOKING_CONFLICT`, message names `BK-MAIN-000001` and its window |
| Back-to-back booking through the API | Accepted |
| Examination on an unassessed hall | `SPACE_NOT_BOOKABLE`, "needs a space assessed READY, not merely usable" |
| Examination buffers | `09:00–12:00` booked, `08:30–12:30` occupied; the 12:00 slot is refused |
| Approval | Requester refused; manager confirms; `approvalId` set |
| Exclusive resource in two rooms | `RESOURCE_UNAVAILABLE`, "Only 0 of PROJ-A1 remain free" |
| Turnaround task raised for a resource needing setup | `PENDING`, due at the start of the occupied window |
| Requester sees only their own bookings | 3 of 4; the manager sees all 4 |
| Override without the permission | Refused and audited; the centre manager's override succeeds and is recorded |
| Readiness reconciliation sweep | Placed 4 holds across 9 bookings; every one stayed `CONFIRMED` |
| The same sweep after the hall was repaired | Cleared all 4 with nobody asking it to — the half that matters, since a flag nothing clears is one people learn to ignore |
| No-show sweep | Recorded 1 of 1 candidate; booking `NO_SHOW`, `NoShowRecord` written with `minutes_held_unused = 160` — the full booked duration, not the 20 minutes before release |
| Audit hash chain after all of the above | `intact: true`, 27 records verified |

The scripts are in the job scratch directory (`verify_s159.sh`, `s159_smoke.sh`,
`s159_constraint.sh`). They are not committed: they depend on the local e2e container and on a
schema that gets dropped, and a test that only runs on one machine belongs in a runbook rather than
in `src/test`.

---

## 3. Not built, and why

### 3.1 Recurring bookings

A weekly lecture for a twelve-week term is twelve `POST /bookings` calls. The SRS does not ask for
recurrence and adding it properly is not small: a recurrence needs its own aggregate, an exception
model for the week the hall is unavailable, and a decision about whether moving one occurrence moves
the series. Doing it badly — expanding to twelve independent bookings at creation and hoping — is
worse than not doing it, because nothing then connects them when the room is lost in week six.

**Consequence:** timetabling a term is repetitive. Worth doing before the first full academic year.

### 3.2 Waiting lists

A refused request is refused. There is no queue, so nobody is told when a hall is released by a
cancellation or a no-show. This is the most-requested feature of every booking system ever built and
it is deliberately out of this pass: it needs a notification channel that does not exist yet (§3.3),
and a policy on who gets the released slot.

### 3.3 Notifications are events, not delivery

Every state change publishes to the service outbox. Nothing drains it. A requester whose booking is
rejected, put on a readiness hold, or marked a no-show learns by looking. Same gap as S153 §3.2, same
fix: one drainer, once, for all of IFIMP.

### 3.4 Capacity is advisory

`expectedAttendees` is recorded and is used to filter the availability list, but a booking of a hall
for more people than it holds is accepted. That is deliberate — capacity figures are frequently
stale, and refusing a booking on one would make the estate register's accuracy a blocker on people
doing their jobs — but it means the number is a planning aid, not a control.

### 3.5 Pooled resources are not race-proof

The database guarantees a single-instance resource cannot be in two places at once. It cannot
guarantee the arithmetic for a pool: two concurrent requests for the last twenty of forty chairs can
both succeed, because an exclusion constraint can say "these rows must not overlap" but not "their
quantities must sum to no more than forty". The advisory lock on resource ids makes this very
unlikely; it does not make it impossible, because the lock is taken per resource and a request that
does not go through `assertResourcesAreFree` — there is none today — would bypass it.

**Consequence:** a chair shortage discovered at setup, not a hall double-booked at examination time.
Closing it properly means a per-resource counter row updated with `SELECT ... FOR UPDATE`, which is a
real option if it ever bites.

### 3.6 No-show is terminal

A booking marked `NO_SHOW` cannot be revived. Somebody arriving at minute twenty-five finds it gone
and has to book again — into a slot that is now free, so they will usually succeed, but the original
reference is lost. The grace period is configurable per site precisely so this can be tuned rather
than worked around.

### 3.7 The readiness hold is 15 minutes behind

By design — see the design note §4 on why a synchronous port would invert the dependency. A hall
blocked at 09:00 has its bookings flagged by 09:15. For an examination starting at 09:05 that is too
slow, and the mitigation is that `SpaceReadinessPort` already exists for the estate: if the latency
ever matters, `BookingReconciliationService.reconcileRoom` is a single-room entry point ready to be
called from wherever readiness changes.

### 3.8 S159-04 and -05 are untouched

Integration and reporting. Same position as S153: the outbox is written, nothing consumes it, and
there is no scheduled report generation. The dashboard counts endpoint (`/bookings/counts`) is what
S159 contributes to S152-05 for now.

---

## 4. What the tests cannot see

Recorded because the reader should know the shape of the safety net, not only its size.

- **The exclusion constraint** cannot be exercised by `InMemoryBookingRepository`: the race it exists
  to catch needs two transactions, and a single-threaded map has none. `S159MandatoryScenariosTest`
  compensates in the only way available to it — `DatabaseAgreesWithTheDomain` reads `V10` off the
  classpath and asserts the constraint's `WHERE` clause lists exactly the statuses
  `BookingStatus.holdsTheSpace()` returns true for. That catches the realistic drift (somebody adds a
  holding state in Java and forgets the migration) and nothing else.
- **The advisory lock** is a no-op in the test double, and has to be.
- **JPQL and the JPA mappings** are covered only by running the service. `FacilitiesMigrationIntegrationTest`
  would cover them, and is skipped in this environment: the Java Docker client cannot reach the
  Windows named pipe, so Testcontainers does not start. Twelve tests skipped, every run.

Both defects in §1 lived entirely inside that blind spot.

---

## 5. Conflicts with the SRS, and how they were resolved

| SRS | Reading taken | Why |
|---|---|---|
| S159-01 lists "readiness hold" as a record | Implemented as two columns on `bookings`, not a table | A hold is one nullable reason with one timestamp, and it is always exactly one per booking. A table would add a join to every read of the diary for no fact it could hold that the columns cannot. The audit trail lives in `AuditAction.BOOKING_READINESS_HOLD_PLACED`/`_CLEARED`, which is where the history belongs anyway. |
| S159-02 "the system shall prevent double-booking" | Enforced in the database, explained in the application | Read-then-write cannot prevent it. Both layers are present and neither is redundant. |
| S159-02 approval | No `APPROVED` state; approval is a recorded event | See the design note §2. |
| S159-02 setup tasks | Not S153 work orders | See the design note §6. This is the reading most likely to be challenged, and the reason is the maintenance queue, not the model. |

---

## 6. What has to happen next

1. **The UI.** S159 has no screens. The backend is complete and driven only by the API.
2. **The outbox drainer**, shared with S153. Until it exists, no booking notifies anybody.
3. **Recurrence**, before the first full academic year is timetabled.
4. **A decision on `booking.no-show.grace` per site.** Twenty minutes is a default, not a policy.
5. **Confirm `btree_gist` is installable in the production database.** It is trusted from PostgreSQL
   13 and the application user installed it here, but a managed service with an extension allow-list
   would refuse it — and the module cannot offer its central guarantee without it. Worth checking
   before the first deploy rather than at it.
