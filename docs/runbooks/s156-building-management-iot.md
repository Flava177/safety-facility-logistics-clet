# Runbook - S156 Building Management System (BMS) / IoT

**Scope.** Building-systems telemetry ingestion, threshold rules, automated work-order generation, the
health dashboard and device inventory, in `sfl-facilities-service` (`gh.edu.clet.sfl.facilities.buildingsystems`,
schema `facilities`, migration `V16__building_management_iot.sql`). For "the service will not start" or
general connectivity, use [`incident-response.md`](incident-response.md) first - this runbook assumes the
service is up.

## What S156 owns

Every BMS/IoT device and gateway registered against an AVAMP asset; every telemetry reading accepted,
quarantined or purged; every threshold/fault-condition rule and its version history; every automated
alert and the S153 work order it raised or correlated to; the per-site/building/system health rollup.

S156 does **not** own: the S152 room/building register (asked, never written to), the S153 fault/work-order
lifecycle past the point of raising it (S156 raises through `AutomatedWorkOrderIntake` and then only reads
back whether the order is still open), or AVAMP's own asset record (S156 keeps a read-only projection fed
by events - see §7).

## Health checks

```
GET /actuator/health
GET /api/v1/facilities/vendor-integrations          -- FACILITIES_VENDOR_INTEGRATION_READ
GET /api/v1/facilities/building-systems/health?siteCode=MAIN   -- FACILITIES_BMS_READ
```

The vendor-integrations endpoint is the procurement-gate truth (CORR-07): `bms-iot` reads
`SIMULATED_ADAPTER_ONLY` until an SRS §5.2 evidence reference is set in
`SFL_S156_PROCUREMENT_GATE_EVIDENCE`. Nothing in this build reports the feed as integrated, and no
runbook step here should either.

The building-systems health endpoint's `state` at every level (device, system, building, site) is one of
`NORMAL`, `DEGRADED`, `OFFLINE`, `FAULT`, `UNKNOWN`. **`UNKNOWN` is not an error** - it is the honest
answer for a device that has never reported or has gone stale, and the platform never shows a stale
device as `NORMAL`. A dashboard reading "all green" while several devices are `UNKNOWN` is a dashboard
bug, not this service's.

## Ingesting telemetry (S156-01)

`POST /api/v1/facilities/building-systems/telemetry`, permission `FACILITIES_BMS_TELEMETRY_INGEST` (held
only by the `INTEGRATION_ENGINEER` and `SERVICE_INTEGRATION` roles - never a person). The body is read raw
and signed over exactly as received; see `shared.application.vendor.VendorMessageVerifier`. Required
headers: `X-SFL-Source`, `X-SFL-Signature` (hex HMAC-SHA256), `X-SFL-Signed-At` (ISO instant). Required
envelope fields in the JSON body: `messageType`, `idempotencyKey`, `siteCode`, `format`.

**Registered formats** (an adapter is selected by this field; ask `TelemetryIngestionService.supportedFormats()`
or check the two shipped adapters):

| Format | Adapter | Shape |
|---|---|---|
| `sfl-bms-sim/v1` | `SimulatedBmsTelemetryAdapter` | SFL's own simulator - already in the common model (`kind` is a `MeasurementKind` name, `value` already in that kind's unit) |
| `bacnet-mqtt-bridge/v2` | `BacnetBridgeTelemetryAdapter` | A BACnet/IP-over-MQTT-bridge shape: `engineeringUnits` (Fahrenheit, Wh, US gallons converted), `pointClass` for binary/multistate points (mains supply, lift state, generator run state) |

Both are **simulated** - see the procurement gate above. Adding a third vendor is one new
`BmsTelemetryTranslatorPort` implementation registered as a Spring bean plus a `sfl.facilities.vendor-inbox.sources`
entry; nothing in this module, S152 or S153 changes (`BuildingSystemsArchitectureTest` and the S156-05
scenario test enforce this).

Dev source: `BMS-SIM`, channel `BMS_TELEMETRY`, secret `dev-only-not-a-real-secret` (override
`SFL_VENDOR_BMS_SIM_SECRET` outside development). Sign with `VendorMessageVerifier.sign(secret, signedAt, rawBody)`.

### What happens to one reading

1. **Rejected** (never stored, never actioned) - failed authentication, allowlist, schema or replay
   check. Mapped `401 VENDOR_MESSAGE_REJECTED`. Same sentence for every reason (NFR-SEC2); the detail is
   only in the audit trail and the SIEM forward.
2. **Quarantined** (held for review, HTTP `202`) - device not registered
   (`BMS_DEVICE_UNREGISTERED`), location does not resolve through S152 (`BMS_LOCATION_UNRESOLVABLE`), an
   implausible value (`IMPLAUSIBLE_VALUE`), or out of order beyond the clock-skew tolerance
   (`OUT_OF_ORDER` / `CLOCK_SKEW`). See §5.
3. **Accepted** - normalised, resolved to an S152 location, stored, offered to every
   `BuildingTelemetryObserver` bean (S157 consumes here), then evaluated against threshold rules.

### Replaying a message

A message with an idempotency key already accepted answers exactly as the first delivery did - no new
row, no re-evaluation. Safe to retry from the vendor side indefinitely.

## Threshold rules and work orders (S156-02)

```
POST   /api/v1/facilities/building-systems/rules                       FACILITIES_BMS_RULE_MANAGE
PATCH  /api/v1/facilities/building-systems/rules/{ruleId}               FACILITIES_BMS_RULE_MANAGE
PATCH  /api/v1/facilities/building-systems/rules/{ruleId}/disablement   FACILITIES_BMS_RULE_OVERRIDE
PATCH  /api/v1/facilities/building-systems/rules/{ruleId}/enablement    FACILITIES_BMS_RULE_MANAGE or _OVERRIDE
GET    /api/v1/facilities/building-systems/rules?siteCode=              FACILITIES_BMS_READ
GET    /api/v1/facilities/building-systems/rules/{ruleId}/history       FACILITIES_BMS_READ
GET    /api/v1/facilities/building-systems/rules/conflicts?siteCode=    FACILITIES_BMS_READ
```

**Separation of duty, by design**: the `FACILITIES_ENGINEER` role writes rules and does not hold
`FACILITIES_BMS_RULE_OVERRIDE`. Disabling a rule always needs that permission, a reason and a named
accountable owner - see `FacilitiesPermissionMatrix.grantPhaseTwo`. If a site genuinely needs the
engineer to also hold override, that is a role-grant decision for whoever owns the matrix, not a code
change here.

**Debounce**: a breach must persist for the rule's `debounce` (default from
`bms.rules.default-debounce`, per rule if set explicitly) before an alert and work order are raised. A
breach that clears first raises nothing - no alert row, no event, no work order. If a breach outlasts its
debounce with no further reading to notice it (a slow-reporting sensor), the sustained-breach sweep
catches it (§8).

**Correlation**: a further sustained breach on the same device while its earlier alert's work order is
still open in S153 is linked to that alert (`bms-alert-correlated`), not duplicated. The check is live
(`AutomatedWorkOrderIntake.find`), not cached - a technician closing the work order between breaches means
the next breach earns a fresh alert and order.

**Rule Conflict**: creating or revising a rule checks it against every other active rule at the site for
the same quantity. An overlap with a different limit is logged (`GET .../rules/conflicts`) and the
stricter limit applies at evaluation time regardless of which rule was written first or last.

## Health and critical-fault escalation (S156-03)

`GET /api/v1/facilities/building-systems/health?siteCode=` rolls up device → system → building → site,
worst-state-wins. A device is `UNKNOWN` (not `NORMAL`) until it has reported within
`expectedIntervalSeconds × bms.health.stale-after-intervals` (default 2 intervals). A device silent longer
than `bms.offline.window` (default 30 minutes) additionally raises its own `SENSOR_OFFLINE` alert, distinct
from a telemetry fault - the sweep in §8 catches this, and a reading arriving afterward clears it
automatically.

**Critical faults bypass debounce entirely** and escalate on the reading that shows them:

| Fault | Recognised as |
|---|---|
| Total power loss | `POWER_STATE = 0` from an `ELECTRICAL`-system device |
| Lift entrapment | `LIFT_STATUS` matching a configured entrapment code (default `3`) |
| Generator failed-start during an outage | `GENERATOR_RUN_STATE = -1` while the same building has a total-power-loss signal within `bms.critical.outage-window` (default 15 minutes) |

Each one: raises a `CRITICAL` work order immediately, publishes `sfl.ifimp.building-critical-fault-detected.v1`,
audits `BMS_CRITICAL_FAULT_ESCALATED`, and forwards to the SIEM. **Read the gap report before treating this
as delivered end to end** - the publish side is real; the SSEMP fast-lane consumer is not built (§9).

## Device inventory and lifecycle (S156-04)

```
POST   /api/v1/facilities/building-systems/devices                     FACILITIES_BMS_DEVICE_MANAGE
PATCH  /api/v1/facilities/building-systems/devices/{deviceId}           FACILITIES_BMS_DEVICE_MANAGE
PATCH  /api/v1/facilities/building-systems/devices/{deviceId}/retirement FACILITIES_BMS_DEVICE_MANAGE
GET    /api/v1/facilities/building-systems/devices?siteCode=&status=    FACILITIES_BMS_READ
GET    /api/v1/facilities/building-systems/devices/{deviceId}           FACILITIES_BMS_READ
```

Registration refuses without an **active** AVAMP asset already in S156's local projection (fed by FTLMP's
`sfl.avamp.asset-registered.v1` / `sfl.avamp.asset-location-changed.v1` events - see §7), and refuses a
second device on the same AVAMP asset id (`ux_bms_devices_avamp`, global, not per-site).

**Retirement retires, never deletes.** `retiredBy`/`retiredAt`/`retirementReason` are set, every active
alert on the device is cleared, and the record plus every reading, alert and work-order link it produced
stays queryable. A replacement device is a new registration.

**Lifecycle data lives here, not in AVAMP** - install date, firmware version, warranty expiry, calibration
due date. AVAMP-Lite publishes none of them; see the gap report (§9).

### Quarantine review

```
GET     /api/v1/facilities/building-systems/quarantine?siteCode=&status=   FACILITIES_BMS_READ
PATCH   .../quarantine/{id}/release                                        FACILITIES_BMS_QUARANTINE_RESOLVE
PATCH   .../quarantine/{id}/discard                                        FACILITIES_BMS_QUARANTINE_RESOLVE
```

**Release** re-checks the device and its S152 location at the moment of release - it does not trust the
reviewer's word that the mapping is fixed. Register the device (or fix its location) first, then release.
A release is only ever possible for `DEVICE_UNREGISTERED` or `LOCATION_UNRESOLVABLE` - a filing problem.
`IMPLAUSIBLE_VALUE`, `OUT_OF_ORDER` and `CLOCK_SKEW` readings can only be **discarded** with a reason: the
value itself was never trustworthy, and releasing it would turn a flagged reading into the very "fact"
S156-01 forbids. A released reading is stored as history but is **never evaluated against threshold rules**
- it arrived late, and raising a work order today for a breach that ended hours ago sends a technician to a
condition that may no longer exist.

## Events published (all `sfl.ifimp.*.v1`; see `docs/facilities/S156_Event_Contracts.md` for payload shapes)

`reading-quarantined`, `bms-alert-raised`, `bms-alert-correlated`, `bms-alert-cleared`, `bms-sensor-offline`,
`building-critical-fault-detected`, `bms-rule-changed`, `bms-device-registered`, `bms-device-retired`,
`bms-device-lifecycle-due`.

Recorded to the outbox in the same transaction as the state change; drained by
`FacilitiesOutboxDrainer` under `sfl.facilities.messaging.*` (see the platform runbooks for drainer
recovery - [`dead-letter-recovery.md`](dead-letter-recovery.md)).

## Scheduled jobs (`BuildingSystemsScheduledJobs`)

All run on a platform thread (RLS scope `*`, actor `system.buildingsystems`), all idempotent - running two
instances wastes a query, never double-acts. `sfl.buildingsystems.scheduling.enabled=false` (default
`true`) removes the bean entirely.

| Job | Property | Default interval | What it does |
|---|---|---|---|
| Sustained breaches | `sfl.buildingsystems.debounce.interval-ms` | 30 s | Raises breaches whose debounce elapsed with no further reading to notice it |
| Offline devices | `sfl.buildingsystems.offline.interval-ms` | 60 s | Raises `SENSOR_OFFLINE` for every active device silent past its offline window |
| Lifecycle reminders | `sfl.buildingsystems.lifecycle.interval-ms` | 3 600 000 ms (1 h) | Raises calibration/firmware/warranty reminders due within the lead time |
| Retention purge | `sfl.buildingsystems.retention.interval-ms` | 86 400 000 ms (24 h) | Deletes readings past the site's configured retention, sparing evidence |

A sweep failure is logged and retried on the next run - it never cancels the schedule (a `RuntimeException`
from a `@Scheduled fixedDelay` method would otherwise silently stop that job forever; see the class Javadoc).

## Configuration (Configuration Without Code - `facility_runtime_configuration`, seeded by V16)

| Key | Default | Meaning |
|---|---|---|
| `bms.validation.clock-skew-tolerance` | `PT2M` | How far a reading may be out of order, or ahead of receipt, before quarantine |
| `bms.rules.default-debounce` | `PT5M` | Debounce for a rule whose author did not set one |
| `bms.offline.window` | `PT30M` | Silence after which a device is offline |
| `bms.health.stale-after-intervals` | `2` | Missed expected intervals after which a device is `UNKNOWN` |
| `bms.critical.lift-entrapment-codes` | `3` | Normalised `LIFT_STATUS` codes meaning entrapment (comma-separated) |
| `bms.critical.outage-window` | `PT15M` | How recent a power-loss signal must be for a failed generator start to be critical |
| `bms.lifecycle.reminder-lead-days` | `30` | Days ahead of a due date that a reminder is raised |
| `bms.retention.readings-days` | `400` | Readings older than this are purged, unless cited as evidence |
| `bms.sweep.batch` | `500` | Rows per sustained-breach sweep |
| `bms.plausibility.<quantity>.min` / `.max` | per-quantity (see V16) | Physically plausible band for each `MeasuredQuantity` |

Every value is site-overridable (`site_code` column) and versioned - a change is a new row, the prior one
superseded, never an overwrite. `PATCH` through `RuntimeConfigurationPort` / the shared configuration
endpoint (see the S152 runbook for the general mechanism); this module adds no configuration endpoint of
its own.

## Failure modes and what to do

**A vendor's message keeps being rejected.** Check `facilities.vendor_inbox_messages` for the
`rejection_reason` (never told to the vendor, by design - NFR-SEC2). `SIGNATURE_INVALID` is almost always
a clock or secret mismatch; `SITE_NOT_PERMITTED` means the source's `sites` list in
`sfl.facilities.vendor-inbox.sources.<id>.sites` does not include the site it is signing for.

**A device is stuck `UNKNOWN` after it should be reporting.** Check the device's `expected_interval_seconds`
against its actual reporting cadence - a five-minute meter registered with a sixty-second expectation is
falsely stale by design. Revise the device (`PATCH .../devices/{id}`), not the configuration.

**An alert never raised a work order.** Check `facilities.bms_alerts.work_order_id` - if null, the alert
predates a rule's promotion logic change or `AutomatedWorkOrderIntake.raise` failed inside the same
transaction as the alert (both roll back together; check the application log around the alert's `raised_at`).

**Quarantine is filling up with `DEVICE_UNREGISTERED`.** A vendor gateway is reporting device ids nobody
registered yet - register them (§ "Device inventory") and release the held readings, or the vendor's device
naming has drifted from what was provisioned.

**The retention purge removed nothing for days.** Check `bms.retention.readings-days` - a very long
retention (or a clock that has not advanced past the cutoff) is doing exactly what it says.

## Replay and repair

- A rejected vendor message cannot be replayed automatically (it was never trusted); fix the signing side
  and have the vendor resend with the same idempotency key.
- A quarantined reading is repaired through the quarantine endpoints (§ "Quarantine review"), never by
  editing a row directly.
- An alert or work order is never hand-edited here - close or reassign the work order in S153; S156 only
  asks S153 whether it is still open.

## RLS notes

Every S156 table carries `site_code NOT NULL` and is covered by `facilities.apply_site_scope_policies()`
(V16's last statement) - proven for this module by
`gh.edu.clet.sfl.facilities.buildingsystems.BuildingSystemsRowLevelSecurityTest` and, platform-wide, by
`Phase2RowLevelSecurityCoverageTest`. The application connects as the schema owner (bypasses RLS by
design - see ADR 0007); only the `sfl_app` role is scoped. Scheduled jobs run on platform threads and
scope to `*` (see `PlatformThreads`), matching the `system.buildingsystems` service account.

## Cross-service dependencies that do not yet consume or publish

- **SSEMP has no inbound event consumer today** (verified: no `@RabbitListener` / `IntegrationEventHandler`
  in `sfl-safety-security-service`). `sfl.ifimp.building-critical-fault-detected.v1` is recorded to the
  outbox and drained to the broker, but nothing on the S162a/S174 side reads it. **This is a go-live blocker
  for the fast-lane acceptance criterion** - see `docs/facilities/S156_Gap_And_Conflict_Report.md`.
- **S157 (Energy & Sustainability)** consumes S156's normalised stream through
  `BuildingTelemetryObserver` and asks `BuildingDeviceDirectory` before registering a meter, both provided
  for real by this build. If S157 is not yet deployed in an environment, `List<BuildingTelemetryObserver>`
  is simply empty and nothing here changes.
- **AVAMP (S168/FTLMP)** publishes no retirement, firmware or calibration facts - S156 holds that data
  itself (§ "Device inventory").
