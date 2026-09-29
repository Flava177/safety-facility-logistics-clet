# Runbook - S173 Event Logistics & Set-Up Workflow

**Scope.** The `eventlogistics` module of `sfl-facilities-service`: CCP Events (S078) hand-off intake,
decomposing a hand-off into typed resource requests routed to S159/S153/S169/S172, the S173-03
higher-risk-event gate, and S173-04 escalation and post-event reconciliation. For a service that will
not start or is refusing every request, use [`incident-response.md`](incident-response.md) first - it
covers the platform-wide causes (Flyway validation, OIDC issuer, the transport property) before you
get here.

## What this module owns

| Owns | Does not own |
|---|---|
| `event_setup_tasks`, `event_resource_requests`, `event_handoffs`, `event_readiness_escalations`, `event_reconciliation_lines`, `event_template_lines`, `event_risk_assessments` (all `facilities` schema, V20) | S159's bookings/allocations, S153's faults/work orders, S169's cleaning tasks, S165's risk assessments - S173 only holds references to these by value |
| Decomposition, routing, the higher-risk confirmation gate, escalation, reconciliation | Whether S159/S153/S169 themselves are healthy - see their own runbooks/health checks |

## Health check

```
GET http://localhost:8091/actuator/health
GET /api/v1/facilities/event-logistics/integration?siteCode=MAIN   (permission FACILITIES_EVENT_READ)
```

The `integration` endpoint is the honest status line for this module's dependencies: S078's position
(no query API - see below), each owning system's availability flag, and S165's projection state. It
never reports something as integrated that is not (SRS CORR-07's discipline, applied here even though
S173 has no procurement gate of its own).

## Scheduled jobs

| Job | Property | Default | What it does |
|---|---|---|---|
| Escalation sweep | `sfl.event-logistics.escalation.interval-ms` | 300000 (5 min) | Escalates every request still `REQUESTED`/`CONFLICTED`/unaccepted-manual whose event's escalation window has opened - SRS-SFL-S173-04. |
| Synchronisation sweep | `sfl.event-logistics.synchronise.interval-ms` | 600000 (10 min) | Pulls current state from S159/S153/S169 for every routed live request - the safety net under the S159 observer (`EventBookingObserver`), and the only path by which S153 completion and S169 fulfilment reach S173. |

Both are gated by `sfl.event-logistics.scheduling.enabled` (default `true`) and run as the platform
account `system.event-logistics-scheduler` (site scope `*`), matching `BookingScheduledJobs`'s
pattern. Initial delays: `sfl.event-logistics.escalation.initial-delay-ms` (150000),
`sfl.event-logistics.synchronise.initial-delay-ms` (180000).

Both sweeps swallow their own `RuntimeException` and log it, on purpose: an uncaught exception from a
`fixedDelay` task cancels the schedule for the life of the process, and a silently dead escalation
sweep is precisely the failure S173-04 exists to prevent.

## Configuration (Configuration Without Code, `facilities.facility_runtime_configuration`)

| Key | Default | What it controls |
|---|---|---|
| `event-logistics.risk.attendance-threshold` | 500 | Expected attendance at or above this is higher-risk. `0` switches the marker off. |
| `event-logistics.risk.external-contractors` | true | External contractors make an event higher-risk. |
| `event-logistics.risk.temporary-structures` | true | Temporary structures make an event higher-risk. |
| `event-logistics.risk.categories` | `CONCERT,EXHIBITION,GRADUATION,SPORTS` | S078 event categories that are higher-risk whatever their size. |
| `event-logistics.escalation.window` | `PT48H` | How long before an event starts an unresolved request escalates. |
| `event-logistics.template.gap-threshold` | 2 | Reconciliation gaps before a template line pre-populates future decompositions. |
| `event-logistics.readiness.upcoming-days` | 30 | Default horizon for the upcoming-events view. |
| `event-logistics.sweep.batch` | 200 | Rows processed per sweep. |
| `event-logistics.owning-system.S159.available` | true | S159 takes venue/AV/staging/security/signage requests. |
| `event-logistics.owning-system.S153.available` | true | S153 takes pre-event maintenance requests. |
| `event-logistics.owning-system.S169.available` | true | S169 takes cleaning reservations. |
| `event-logistics.owning-system.S172.available` | **false** | S172 is Phase 3 and not built. Flip this the day it is. |

Change any key with `RuntimeConfigurationPort.put` (site-scoped or platform default); it applies to
the next evaluation, no redeploy. `PUT /api/v1/facilities/event-logistics/risk-criteria` covers the
four risk keys specifically, audited (`EVENT_RISK_CATEGORY_CONFIGURED`) and held to
`FACILITIES_EVENT_RISK_CATEGORY_MANAGE`.

## Failure modes and what to do

**A hand-off is rejected with `EVENT_REFERENCE_UNRESOLVABLE`.** Expected for an event S078 has not
confirmed, or a reference S173 has never accepted a hand-off for before (cancellations and updates
resolve only against what S173 already holds - see the gap report on why). Check:

```sql
SELECT s078_event_reference, s078_status, outcome, rejection_detail, received_at
  FROM facilities.event_handoffs
 WHERE outcome = 'REJECTED'
 ORDER BY received_at DESC LIMIT 20;
```

If the reference genuinely is confirmed in S078 and this refuses anyway, the hand-off's `status`
field is probably not `CONFIRMED` in S078's own vocabulary - see the gap report's note that S078's
status values are not otherwise specified. Confirm what CCP Events actually sends and adjust the
simulator/mapping, not this module's refusal.

**A hand-off is rejected as a forged vendor message.** Same authenticated-inbox procedure as every
Phase 2 vendor channel - see `incident-response.md` §4 and:

```sql
SELECT source_system, rejection_reason, rejection_detail, received_at
  FROM facilities.vendor_inbox_messages
 WHERE channel = 'CCP_EVENTS' AND outcome = 'REJECTED'
 ORDER BY received_at DESC LIMIT 20;
```

**Every higher-risk event confirmation is refused with `EVENT_RISK_ASSESSMENT_NOT_CURRENT`, reason
`NONE_LINKED`.** This is not a bug. S165 (Risk Assessment Library) is not built; nothing publishes
`sfl.ssemp.risk-assessment-*.v1`; `facilities.event_risk_assessments` is empty:

```sql
SELECT count(*) FROM facilities.event_risk_assessments;   -- 0 until S165 ships
```

The correct response is not to work around it - it is to route the event through whatever manual HSE
sign-off process exists outside SFL until S165 ships, and to raise the S165 dependency if it is
blocking a real event. Routine events (below every configured trigger) are unaffected.

**A resource request sits `MANUAL_COORDINATION` and never resolves via the sweep.** Correct if its
owning system is switched off (check `GET .../integration`) or is S172 (always true today). The
coordinator must accept it explicitly (`PATCH .../manual-coordination/accept`) - only an accepted
manual item counts as resolved for S173-04's completion and escalation rules. An unaccepted one is
exactly what the escalation sweep is for.

**A `VENUE` request is `CONFLICTED` and the coordinator wants to see why.** The `competingCommitment`
field on the request (and the readiness view's `conflicts` list) names the S159 booking or S169
reservation holding the slot - S169-04's acceptance criterion, surfaced here. Route the request again
(`PATCH .../route`) once the coordinator has chosen a different room, time, or cancelled the
competing booking.

**`AV`/`STAGING`/`SECURITY`/`SIGNAGE` stays `REQUESTED` with no `externalReference` indefinitely.**
It is waiting for its `VENUE` line to hold an S159 booking - check the VENUE request's status first.
Once the venue is booked (whether or not S159 has approved it yet), routing it again
(`PATCH .../route` on the waiting line, or simply routing/re-decomposing the venue) allocates it.

**Completion is refused with `EVENT_UNRESOLVED_RESOURCE_REQUEST`.** By design: a requested-status
line with no escalation record blocks completion. If the event genuinely ran without that resource,
record the reconciliation line as `NOT_DELIVERED` first (reconciliation does not require the task to
be `COMPLETED`, only `CONFIRMED` or later) - or wait for the escalation sweep, which records the
escalation the completion check looks for.

## Replay and repair

- **Re-deliver a hand-off**: the same `X-SFL-Source` + `Idempotency-Key` is a duplicate and answers as
  before; a genuinely new attempt needs a new idempotency key from S078's side, or `UPDATED`/
  `CANCELLED` status values against the same reference.
- **Re-route a request**: `PATCH .../resource-requests/{id}/route`. Safe to call repeatedly; a request
  already holding a live external commitment refuses with a clear message rather than double-booking.
- **Force a sweep now** (rather than waiting for the interval): there is no manual-trigger endpoint;
  restart the process with a shorter `sfl.event-logistics.escalation.interval-ms` for a test
  environment, or call the application service directly from a REPL/test harness in development.

## What is simulated or not integrated

- **CCP Events (S078)** is external and has no query API integrated with SFL. `CcpEventsDirectoryPort`
  resolves a reference against S173's own register of accepted hand-offs, not against S078 itself -
  see `RecordedCcpEventsDirectory`'s Javadoc and the gap report for exactly what this can and cannot
  catch.
- **S172 Catering & Cafeteria Management** is Phase 3 (SRS 3.6) and not built. Every catering request
  is `MANUAL_COORDINATION`, permanently, until it exists.
- **S165 Risk Assessment Library** is not built (it is in SSEMP, a different deployable being staged
  for a later pass). The projection this module keeps is fed by four reserved event names nothing
  publishes yet - see `docs/facilities/S173_Event_Contracts.md`.
- **Security staffing via S159**: the SRS routes `SECURITY` resource requests to S159 as a bookable
  resource. In the Phase 1 platform, security staffing is an SSEMP concern (SOC rostering), not an
  S159 concept. S173 follows the SRS literally and books a named "security team/post" as an S159
  resource; it does not reach into SSEMP for rostering. Flagged, not silently resolved - see the gap
  report.
- **No notification provider for IFIMP.** `event-readiness-escalated` records who should be told
  (`notifiedTo`) and that nothing has confirmed delivery to them; nothing sends an SMS, email or push.
  An operator reading the escalation table or the outbox event is the delivery mechanism today.

## RLS notes

Every table in V20 carries `site_code NOT NULL` and V20's last statement is
`SELECT facilities.apply_site_scope_policies()`. `EventLogisticsRowLevelSecurityTest` proves an
unscoped `sfl_app` session sees nothing, a scoped one sees only its site, `*` sees across, and a write
outside scope fails with `42501`. `event_risk_assessments` is keyed by `(assessment_id, version)`, not
a surrogate id, but still carries `site_code` and is covered by the same policy.

## Escalate when

- `facilities.event_risk_assessments` is non-empty and a higher-risk confirmation is still refused for
  a reason that does not match what S165 (once it exists) says - that is a projection/event-handling
  bug, not the expected fail-closed state.
- A set-up task is `CONFIRMED` for a higher-risk event with no `risk_assessment_id` at all. That
  should be structurally impossible (`EventSetupTaskService.confirm` checks before every confirmation)
  and is worth a second pair of eyes before assuming the data is merely stale.
