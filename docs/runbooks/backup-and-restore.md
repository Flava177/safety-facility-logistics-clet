# Runbook — backup and restore

**Scope.** Per-service PostgreSQL backup, restore, and how to prove a restore actually worked. Whole-site
loss is [`disaster-recovery.md`](disaster-recovery.md).

## What has to be backed up together

Each service owns one schema and they are **not** interchangeable, but two invariants cross tables
*within* a schema and a restore that splits them produces a database that starts and lies:

1. **The audit chain and its head row.** `*_audit_records` is hash-chained, and the current head hash
   lives in a separate single-row table. Restore them from different points and the chain verifies as
   tampered — indistinguishable, at a glance, from an actual attack.
2. **The outbox and the aggregates it describes.** A business change and its outbox row commit in one
   transaction. Restoring to a point that has one without the other either replays an event for a
   change that no longer exists, or loses the event for one that does.

So: **back up and restore a schema as a unit, at a single point in time.** Never table-by-table.

## Backing up

```bash
# One service, schema only, custom format so a selective restore is possible.
docker exec sfl-facilities-postgres pg_dump -U sfl -Fc \
  -n facilities -d sfl_facilities_service > facilities-$(date +%Y%m%dT%H%M).dump
```

Repeat per service against its own container and database — see the map in
[`README.md`](README.md). There is no cross-service consistent snapshot and there does not need to be:
services reconcile through events, and the inbox dedup makes replay safe.

**What is not in the dump.** Evidence is stored *by reference* with a SHA-256, not as bytes — S161
video, S153 closure photographs and S171 signed receipts live in object storage. A database restore
without the corresponding object-store restore gives you rows pointing at objects that are gone, and
the hash is what lets you detect that.

## Restoring

```bash
# Stop the service first. A running service will migrate, drain the outbox and move the audit chain
# underneath the restore.
docker exec -i sfl-facilities-postgres pg_restore -U sfl -d sfl_facilities_service \
  --clean --if-exists -n facilities < facilities-20260731T1400.dump
```

Then, **before** starting the service:

```sql
-- Flyway must agree with the code that is about to run against it.
SELECT version, description, success FROM facilities.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

A restore from an older dump than the deployed code means Flyway will apply the missing migrations on
startup. That is fine and expected — but check the gap first, because V6-class migrations rewrite live
tables and you want to know that is about to happen rather than discover it.

## Proving the restore worked

A service that starts is not proof. Run all four:

**1. Hibernate agrees with the schema.** The service starting at all with `ddl-auto: validate` is this
check. If it starts, the schema shape is right.

**2. The audit chain verifies.**

```
GET /api/v1/facilities/audit/verify
```

Must report `intact: true`. If it reports tampered, the restore split the chain from its head row —
re-restore both from the same dump. Do not "fix" the chain.

**3. The outbox is in a sane state.**

```sql
SELECT status, count(*), min(created_at), max(created_at)
  FROM facilities.outbox_messages GROUP BY status;
```

Rows restored as `PENDING` **will be republished** when the drainer starts. That is correct
at-least-once behaviour and consumers dedup on `eventId` — but if the restore rolled back past events
that were already consumed, expect a burst. Consider setting `drainer-enabled=false` until you have
looked at the counts.

**4. A read that crosses the interesting joins.** For facilities, fetch a space and confirm its
readiness state and open blockers come back together; that exercises the estate tables, the readiness
tables and the derived state in one request.

## Restoring one service and not the others

Normal, and the architecture is built for it — but the restored service is now behind. It will have
outbox rows to republish (§3 above) and it will have **missed** events other services published while
it was down. There is no automatic catch-up: inbox dedup makes replay safe but nothing re-sends.

If the gap matters, ask the publishing service to replay from its outbox for the window — see
[`dead-letter-recovery.md`](dead-letter-recovery.md) §4. Record which window you replayed.

## Retention and legal hold

Evidence carries a mandatory retention class and may carry a legal hold. **A backup taken before a
disposal, restored after it, resurrects the disposed evidence** — which is a data-protection incident,
not a convenience. Before restoring a dump older than the last disposal sweep, involve Compliance.

Note that the disposal sweep is **not yet built** (S153 gap §3.4): `disposalEligibleFrom` is computed
and nothing acts on it. Until it exists, no disposal has happened and this paragraph is a
forward-looking constraint rather than a live one.
