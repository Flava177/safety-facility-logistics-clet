# Runbook — a service is down or refusing requests

**Scope.** One SFL service is unreachable, failing health checks, or returning errors on requests that
used to work. For "an event did not arrive", use [`dead-letter-recovery.md`](dead-letter-recovery.md)
instead — a healthy service with a stalled drainer looks fine to every check below.

## 0. Before you touch anything: get the correlation ID

Every request carries `X-Correlation-ID` end to end, is echoed on the response, and is written to the
audit record for any state change. If a user reported this, ask for it. One correlation ID turns
"the system is broken" into a single traceable request across services, and asking later means asking
someone to reproduce a failure.

```sql
SELECT occurred_at, actor_id, action, resource_type, resource_id, site_scope
  FROM facilities.facility_audit_records
 WHERE audit_correlation_id = '<id>'
 ORDER BY sequence_no;
```

## 1. Is it up?

```
GET http://<host>:<port>/actuator/health
```

Ports: facilities 8091, fleet-logistics 8093, safety-security 8094, emergency 8095,
asset-visibility 8096.

- **Connection refused** → the process is not running. Go to §2.
- **`DOWN` with a `db` component failure** → go to §3.
- **`UP` but requests fail** → go to §4.

## 2. The service will not start

Read the first exception, not the last — Spring wraps failures several deep and the last one is
usually `ApplicationContextException`, which tells you nothing.

The three failures this platform has actually had, in the order they are worth checking:

**Flyway validation failed.** A migration was edited after it was applied, or the schema was created
by something other than Flyway. `ddl-auto: validate` means the service refuses to start rather than
run against a schema it does not recognise, which is correct and is not the bug — the edited migration
is.

```sql
SELECT installed_rank, version, description, success, checksum
  FROM facilities.flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;
```

**Hibernate validation failed on a column type.** The message names the column. `CHAR(n)` is the known
cause: Hibernate maps `String(length=n)` to `VARCHAR(n)` and refuses `CHAR(n)`. Both facilities (V5)
and fleet (V9_1) have been bitten; the fix is always a corrective migration to `VARCHAR(n)` with a
length `CHECK`, never a change to the entity.

**Two unannotated constructors on a Spring bean.** Spring considers non-public constructors as
candidates and, finding two with no `@Autowired`, looks for a no-arg one and fails. This kept
`sfl-facilities-service` from starting at all for an entire build pass while 135 unit tests were green.

**The issuer is unreachable.** New since A1, and the likeliest new cause of a service that will not
start. `sfl.security.enabled` now defaults to `true`, so the resource server tries to resolve
`${SFL_IAM_ISSUER}` — and if Keycloak is not up, or is up without the `sfl` realm imported, the
context fails. Check `http://<keycloak>:8080/realms/sfl` returns 200 before suspecting the service.
In compose, Keycloak is health-gated on exactly that URL for this reason. A developer laptop with no
Keycloak should set `SFL_SECURITY_ENABLED=false` explicitly; the startup log says so loudly when it
takes that path.

**A misconfigured transport.** Since the IFIMP drainer landed, selecting
`sfl.facilities.messaging.transport=rabbitmq` without a `RabbitTemplate` fails at startup **on
purpose** — the alternative is a service that runs and silently drops every integration event. The
message names the property. Either configure `spring.rabbitmq.*` or set the transport to `local`
deliberately.

## 3. The database is unreachable

```bash
docker ps --filter name=sfl- --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'
docker exec sfl-facilities-postgres pg_isready -U sfl
```

Each service has its own PostgreSQL. **A service whose database is down must not be pointed at another
service's database** — schemas are not interchangeable and Flyway will attempt to migrate it.

If the database is up but connections are refused, check the pool: a leaked connection shows as
requests hanging rather than failing, and `HikariPool-1 - Connection is not available` in the log.

## 4. It is up but refusing requests

**403 on everything.** Almost always authorisation, and the denial is audited — which is how you tell
a permission problem from a bug:

```sql
SELECT occurred_at, actor_id, action, resource_type, before_value, after_value
  FROM facilities.facility_audit_records
 WHERE action = 'AUTHORIZATION_DENIED' AND occurred_at > now() - interval '1 hour'
 ORDER BY sequence_no DESC LIMIT 50;
```

The denial record carries the required permission and the actor's roles and site scopes. Compare
against the service's permission matrix. Two known-surprising cases, both correct:
`FACILITIES_AUDIT_INTEGRITY_CHECK` is **not** held by `FACILITIES_DIRECTOR` (compliance runs the
integrity check, because an integrity failure escalates *to* compliance), and a `VENDOR_TECHNICIAN`
sees only work assigned to them personally — a vendor firm with three technicians gets three disjoint
queues.

**403 for a driver on a record that looks like theirs.** Since 31 July 2026, a `FLEET_DRIVER` is scoped
per record: their own trips (by the driver's `staffReference`) and their own logbooks (by
`created_by`). A driver whose actor id does not match their driver register `staffReference` will be
refused everything. That is a provisioning mismatch, not a defect.

**500 with `could not determine data type of parameter`.** The `(:p is null or column = :p)` idiom with
a null `Instant` on PostgreSQL. It has caused an outage twice here — every S159 booking search, and the
S152 audit search. The fix is a `Specification` or an explicit cast, never a retry.

**409 on a booking.** `BOOKING_CONFLICT` is the GIST exclusion constraint doing its job. The message
names the booking holding the slot. This is not an incident.

## 5. Check the audit chain before declaring an all-clear

If the incident involved a database restore, a manual `UPDATE`, or anything that wrote rows outside the
application, verify the hash chain before telling anyone the system is trustworthy:

```
GET /api/v1/facilities/audit/verify     # requires FACILITIES_AUDIT_INTEGRITY_CHECK
```

A chain that reports tampered after a restore usually means the restore was partial — the audit table
came back at a different point than the chain-head row. Treat it as a failed restore, not a security
event, and re-restore both together. If the chain is broken with no restore in the timeline, escalate
to Compliance immediately.

## 6. Escalate when

- The audit chain reports tampered and nothing explains it.
- Any life-safety or emergency path is affected — SFL is never in the certified actuation path, so a
  fault here does not stop a fire panel, but it does stop the *record* of what happened.
- A fix requires writing to a table directly. Get a second pair of eyes and record what you ran.
