# Runbook — an event did not arrive

**Symptom.** A downstream module did not react: a work order was escalated and nobody was notified, a
booking was rejected and the requester was not told, a dispatch variance did not reach SSEMP.

**First thing to understand.** Every SFL service writes the business change *and* an outbox row in one
local transaction, then a drainer publishes. So an event that "did not arrive" stopped in one of four
places, and they are distinguishable. Work down the list in order — each step rules out everything
above it.

---

## 1. Was the row ever written?

If the business change committed, the outbox row committed with it. If there is no row, the event was
never raised and this is an application defect, not a delivery failure.

```sql
-- facilities (IFIMP). Swap schema and table prefix per service; see README for the map.
SELECT id, event_type, status, attempt_count, created_at, published_at, failure_reason
  FROM facilities.outbox_messages
 WHERE aggregate_id = '<the record id>'
 ORDER BY created_at DESC;
```

No row → stop here and raise a defect against the module that owns the aggregate. The outbox is
transactional; a missing row means the publish call is missing from the code path, not that delivery
failed.

## 2. Is it still `PENDING`?

```sql
SELECT status, count(*), min(created_at) AS oldest
  FROM facilities.outbox_messages GROUP BY status;
```

A growing `PENDING` count with an old `oldest` means the drainer is not running. Check, in this order:

- **Is the drainer switched off?** `sfl.facilities.messaging.drainer-enabled` (and the `sfl.fleet.*`
  and `sfl.emergency.*` equivalents). It defaults to `true`; a deployment that set it `false` to quiet
  a noisy log will silently stop every event.
- **Is the service running at all?** `GET /actuator/health` on the port in the README table.
- **Is the scheduler thread alive?** The drain logs at `INFO` only when it publishes something, so
  silence is ambiguous. Set `logging.level.gh.edu.clet.sfl.facilities.shared.infrastructure.messaging=DEBUG`
  and confirm a tick appears within `drain-delay` (default `PT10S`).

### The trap: `next_attempt_at` in the future

A row that failed once is `PENDING` **with a backoff**, and will not be claimed until its window opens:

```sql
SELECT id, event_type, attempt_count, next_attempt_at, failure_reason
  FROM facilities.outbox_messages
 WHERE status = 'PENDING' AND next_attempt_at > now()
 ORDER BY next_attempt_at;
```

This is working as designed — the delay doubles from `retry-base` (default `PT10S`) and caps at
`retry-cap` (default `PT1H`). **Do not clear `next_attempt_at` to force a retry while the cause is
still broken**; you will simply burn the remaining attempts and dead-letter the message. Fix the
transport first, then §4.

## 3. Is it `DEAD_LETTERED`?

```sql
SELECT id, event_type, aggregate_type, aggregate_id, attempt_count, dead_lettered_at, failure_reason
  FROM facilities.outbox_messages
 WHERE status = 'DEAD_LETTERED'
 ORDER BY dead_lettered_at DESC;
```

The message failed `max-attempts` times (default 5) and was set aside so it could not block the queue
behind it. `failure_reason` carries the transport's own message, truncated to 2000 characters.

**Read the reason before replaying.** A dead letter is usually one of:

- *Broker unreachable* — infrastructure. Fix, then replay (§4); the payload is fine.
- *Serialization or routing rejection* — the event name may not match the catalogue. Since 31 July 2026
  both facilities and asset-visibility validate the name at the write path (`ServiceEventType`), so
  this should now be impossible for new rows; a pre-rename row can still carry `ifimp.work-order.assigned`
  and will never route. Those rows must be renamed before replay, not just retried.
- *Consumer rejection* — the message is being delivered and refused. Replaying will not help; fix the
  consumer.

## 4. Replaying

**Fleet and fuel have an API for this.** Prefer it — it is permission-gated (`FUEL_INTEGRATION_REPLAY`)
and audited, where a direct `UPDATE` is neither:

```
GET  /api/v1/fuel/outbox/health          # pending / published / dead-letter counts
POST /api/v1/fuel/outbox/{id}/replay     # requeue one dead letter
```

**Facilities, emergency and asset-visibility have no replay endpoint yet.** Requeue by hand, in a
transaction, and record what you did and why:

```sql
BEGIN;
UPDATE facilities.outbox_messages
   SET status = 'PENDING', attempt_count = 0, next_attempt_at = NULL,
       dead_lettered_at = NULL, failure_reason = NULL
 WHERE id = '<message id>';
COMMIT;
```

The drainer picks it up on the next tick. **Requeue in small batches** — if a hundred dead-lettered
because the broker was down, releasing all hundred at once against a broker that has just come back is
how you take it down again.

## 5. Consumers, and why replay is safe

Delivery is **at-least-once**: a crash between `send` and the status update replays the message. Every
consumer therefore writes `eventId` to its `inbox_messages` table *before* processing, and drops a
repeat. That is what makes §4 safe to run twice — but it also means a consumer that has *not* got an
inbox is not safe to replay into, so check before replaying into anything new.

## 6. Escalate when

- Dead letters are accumulating faster than you can read them → the transport is misconfigured, not
  flaky; stop replaying and fix configuration.
- A dead letter is older than the retention on its payload's evidence → involve Compliance before
  replaying, because the event may reference an object that has since been disposed.
- The event is life-safety or emergency (`sfl.ssemp.*` fast lane) → escalate immediately rather than
  working the list; the fast lane exists because these must not queue behind routine traffic.
