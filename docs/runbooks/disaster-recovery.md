# Runbook — disaster recovery

**Scope.** The primary site is gone or unreachable. This is the order things come back in, what is lost,
and what must be checked before the platform is declared usable.

## The honest position first

**This procedure has never been rehearsed.** Workplan W7 requires a DR drill before go-live and it has
not happened. What follows is derived from how the services are actually built — the dependency order
is real, the checks are real — but the timings are estimates, not measurements, and the first execution
should be a drill, not an incident.

## Recovery order, and why

Services do not call each other synchronously, so this order is about **data dependency**, not startup
dependency. Each service will start regardless; starting them in the wrong order produces a platform
that works but shows wrong answers until the estate is back.

| Order | Service | Why here |
|---|---|---|
| 1 | PostgreSQL instances (all five) | Nothing starts without its own schema |
| 2 | RabbitMQ, if the deployment uses it | Drainers back off cleanly if it is late, but events queue in the outbox meanwhile |
| 3 | `sfl-asset-visibility-service` | AVAMP-Lite is the reference layer every other service points at |
| 4 | `sfl-facilities-service` | S152 is the estate register S153, S159 and readiness all hang off |
| 5 | `sfl-fleet-logistics-service` | Self-contained; the dispatch↔fleet link is an optional soft reference |
| 6 | `sfl-emergency-notification-service` | Independent deployable (ADR 0004) |
| 7 | `frontend/sfl-operations-ui` | Last: it fails open on permissions, so bringing it up before the services answer makes every gated control **disappear** rather than error |

Point 7 is not cosmetic. `actorPermissions.ts` fails open per-*set*, not per-service: once one service
answers, anything absent from the merged set reads as denied. A UI brought up against a half-recovered
back end shows a dashboard that looks healthy and silently hides most of itself.

## Bring each service back

For each, in order:

1. Restore the schema from the most recent dump — [`backup-and-restore.md`](backup-and-restore.md).
2. **Start with the drainer disabled.** `SFL_FACILITIES_DRAINER_ENABLED=false` (and the fleet and
   emergency equivalents). Restored `PENDING` rows will otherwise republish the moment the service
   starts, before you have looked at how far back the restore went.
3. Confirm the service is `UP` and the audit chain verifies.
4. Only then enable the drainer, and watch the first drain.

## What is lost

Be precise about this with the business, because the honest answer is not "nothing".

- **Anything committed after the last dump.** Per-service, so the loss window differs per service.
- **In-flight requests.** No loss of integrity — the outbox row and the business change commit
  together, so there are no half-applied changes — but a user who pressed submit gets nothing.
- **Events published but not yet consumed**, if the *consuming* service restored to an earlier point
  than the publisher. The publisher's outbox still has them; replay the window.
- **Nothing from the object store**, unless it was also lost. Evidence is by reference with a SHA-256,
  so a database restored to an earlier point still points at valid objects — and the hash detects it if
  it does not.

## Edge survivability, and what it does not cover

The architecture provides for a centre continuing through a WAN loss: a local outbox, offline token
validation against cached JWKS, and a permission snapshot, reconciling on restore (PLAT-04, CT-17).
**That is a design provision, not a tested capability** — no edge deployment exists and no reconcile
has been exercised. Do not plan a DR response around it.

## Before declaring the platform usable

All five, per service:

1. `/actuator/health` is `UP`.
2. The audit chain verifies `intact: true`. A chain that reports tampered after a restore is a **failed
   restore** until proven otherwise — the usual cause is the chain-head row and the audit table coming
   from different points.
3. Outbox counts are understood: how many `PENDING`, how old, how many `DEAD_LETTERED`.
4. Flyway history matches the deployed version.
5. One real workflow driven end to end as a real actor — not a health check. For IFIMP: report a fault,
   raise a work order, watch the space go `BLOCKED`, close it with evidence, watch it return to
   `READY`. That single path exercises the estate, readiness, maintenance, the audit chain and the
   outbox in one pass, which no combination of component checks does.

## Post-incident

- Record the restore point per service and the replay windows used. Six weeks later, "which events did
  we replay?" is unanswerable without it.
- Re-verify the audit chain **after** the first day of normal traffic, not only immediately after the
  restore.
- If any evidence reference resolved to a missing object, raise it with Compliance the same day. The
  SHA-256 makes this detectable; nothing makes it self-healing.
