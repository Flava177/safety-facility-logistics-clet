# S153 CMMS — gap and conflict report

What is not built, what the SRS asks for that the platform cannot yet do, and what running it found.
Companion to [S153_CMMS_Design.md](S153_CMMS_Design.md).

## 1. The three defects this round existed to fix

All three were in the pre-S152 maintenance spine and all three are fixed. They are recorded first
because the shape may exist in other services written in the same weeks.

**D-01 — No authorisation on the fault path.** `FacilityFaultController.findAll()` had no permission
check and no site filter. Any caller, holding any role or none, received every fault at every site:
which examination halls were unusable, which security equipment was broken, who reported it. This was
not a narrow scope bug — there was no scope logic at all.

**D-02 — A second actor model.** These controllers read `X-SFL-User` directly through
`DevActorHeaderResolver`. S152 resolves an `ActorContext` through `FacilitiesActorResolver`, which
also works with a JWT. Two actor models in one deployable is how the first one gets forgotten, and
the forgotten one is the one that does not check anything.

**D-03 — Outside the audit chain.** No fault or work-order state change was hash-chained, so
`GET /audit/integrity` verified a chain with holes where the maintenance work should have been, and
reported `intact: true`. It now carries 21 maintenance actions and still verifies.

## 2. Four design defects the tests found

Recorded because each was wrong in a way review would not have caught, and each was caught by
asserting the SRS criterion rather than the implementation.

| # | What | Why it mattered |
| --- | --- | --- |
| D-04 | `IN_PROGRESS → CLOSED` was not a legal transition | A supervisor doing a job themselves had to hand it to themselves first — and every pre-S153 row, which went `ASSIGNED → CLOSED`, would have become unclosable after migration |
| D-05 | A technician could reopen their own completed work | They held `FACILITIES_WORK_ORDER_CLOSE`, which collapsed the two states the SRS separates. Fixed by removing it: a technician completes, a supervisor accepts |
| D-06 | `resolve` and `dismiss` cleared `blockerRaised` before reconciliation read it | A rejected fault left its readiness blocker open **forever**: a hall nobody could book, with nothing on the fault to explain why |
| D-07 | The sweep escalated a fault *and* its work order | Two people notified about one problem. The fastest way to make escalations ignored is to send them twice |

D-06 is the one worth remembering. The transition "helpfully" cleared a flag that the code running
immediately after it used to decide whether there was anything to clean up.

## 3. Not built, and why

### 3.1 S153-04 integration is a stub

SRS-SFL-S153-04 asks for integration with "CAFM/IWMS, BMS/fire/life-safety events where available,
procurement/vendor master, notifications, audit/evidence, reporting", with HMAC or mTLS on every
inbound webhook.

What exists: the CAFM half, because S152 and S153 are the same deployable and the join is a port
rather than a wire. Outbound events publish through the existing outbox with idempotency keys and
correlation IDs.

What does not: **no inbound webhook endpoint, and therefore no signature verification.** S152 has the
same gap and it is recorded there as C-04. Building signature verification for a webhook nobody sends
would produce a security control with no traffic to validate it against, and those tend to be wrong
in ways nobody discovers. `MaintenanceVendor.externalVendorId` is the seam for procurement.

### 3.2 Notifications are events, not delivery

SRS-SFL-S153-02 requires notifying "when work is assigned, overdue, escalated or blocked". This
publishes the events and stops. Delivering to a person is the notification service's job; a second
notifier here would be a second place for CLET's escalation contact list to be wrong. **The
requirement is not met end to end until something consumes those events**, and that is a real gap
rather than a design flourish.

### 3.3 Parts are not a stores system

No stock level, no reorder point, no reservation. CLET has no inventory system for this to reconcile
against, and inventing one here would produce numbers nobody maintains. What is recorded is what was
fitted, how many, and what it cost — enough for a job cost, not enough for procurement.

### 3.4 Evidence disposal is not swept

Retention classes are recorded and `disposalEligibleFrom` is computed and returned, and the index
`ix_maintenance_evidence_retention` exists for exactly this query. Nothing runs it. Disposal deletes
things, and a sweep that deletes evidence should not ship in the same round that first defines what
the retention classes mean.

### 3.5 The response SLA is carried but not enforced

`maintenance.sla.response.*` is read, stored in `SlaPolicy` and exposed. Only the resolution deadline
drives escalation. Separating "nobody has picked this up" from "nobody has finished this" needs a
second escalation track with its own recipients, and it is worth doing deliberately rather than as a
second `if` in the sweep.

## 4. Verified by running it against a real database

Not a formality: this is the step that found eight defects in S152 and one in the S152 UI round.

A database was built at V8, seeded with rows in the **old** shape — including a fault against
`HALL-A` and one against `CAR-PARK-B` — and V9 was run over it by starting the service.

| Check | Result |
| --- | --- |
| V9 applies to a populated pre-S153 database | Yes, and Hibernate's `ddl-auto: validate` passed |
| Provenance backfilled from reporter and report time | Yes |
| `HALL-A` fault linked to its room | Yes |
| `CAR-PARK-B` fault left unlinked | Yes — the case the migration comment describes |
| Historic closed order got `evidence_required = 0` | Yes; it remains closable |
| 15 configuration defaults seeded | Yes |
| Critical fault blocks its hall, closure clears it | Yes: `BLOCKED` → bookable again |
| Closure refused without evidence, allowed with it | Yes, with the SRS wording and a correlation ID |
| Preventive generation idempotent | Run 1 raised `WO-MAIN-000002`, run 2 raised nothing |
| Escalation fires and is idempotent | Level 1 on sweep 1, nothing on sweep 2 |
| A live configuration change applies to the next triage | Yes — SLA changed to `PT1S` and the next fault used it |
| Audit chain verifies with maintenance in it | `intact: true`, 21 records |
| Vendor sees only work assigned to them | Yes, and is refused by id |
| Requester sees only their own faults | Yes |

**It found no new defects.** Recorded plainly rather than omitted: the four in §2 were found by the
tests before it ran, and the database run confirmed the migration and the runtime behaviour rather
than surfacing anything the tests had missed. That is a better outcome than S152's and probably
because the migration was written second.

## 5. Consequences worth knowing before go-live

**Migrated open faults have no SLA and will never escalate until re-triaged.** `slaDueAt` is left
null on migration deliberately: the priority was never confirmed under the S153 rules, and
back-dating deadlines would produce a wall of instant breaches on day one. The consequence is that
every fault open at cutover needs a triage pass, and until it gets one the escalation sweep will not
see it. **This should be on the go-live checklist.**

**`FACILITIES_AUDIT_INTEGRITY_CHECK` is not held by `FACILITIES_DIRECTOR`.** Inherited from S152 and
correct — an integrity failure escalates *to* compliance, so compliance runs the check — but it
surprises a director who expects to be able to run everything. Noted because it was surprising while
testing.

**The vendor narrowing is per person, not per firm.** A vendor firm with three technicians sees three
disjoint queues. That is the stricter reading and the safer default, but if CLET wants a firm-level
view it is a real change: it needs the vendor id on the actor, not just the assignment string.

## 6. What has to happen next

1. **Consume the escalation events.** The requirement is not met until somebody is told (§3.2).
2. **S153 UI.** Fifteen S152 screens exist and none of them show a fault or a work order. The
   endpoints are now stable and enveloped, and `S152_UI_Gap_Report.md` §2.1 — which recorded this as
   the one capability lost when the static page retired — can be closed by it.
3. **S159 room and resource booking**, against the flags S152 already surfaces.
4. **Inbound integration and signature verification**, once there is a sender (§3.1).
5. **The evidence disposal sweep** (§3.4), deliberately and on its own.
