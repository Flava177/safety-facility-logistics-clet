# S156 Building Management System / IoT - gap and conflict report

What is not built, what the SRS asks for that the platform cannot yet do, and what running it found.
Companion to `docs/runbooks/s156-building-management-iot.md`.

## 1. The go-live blocker: SSEMP has no inbound consumer

SRS-SFL-S156-03 requires critical faults to "escalate immediately to the fast lane shared with
S162a/S174" - the emergency dashboard's own escalation path in `sfl-safety-security-service`.

**What is built:** total power loss, lift entrapment and generator-failed-start-during-outage are
recognised on the accepted reading that shows them (no debounce), raise a `CRITICAL` work order, audit
`BMS_CRITICAL_FAULT_ESCALATED`, forward to the SIEM, and publish
`sfl.ifimp.building-critical-fault-detected.v1` through the shared outbox, which the drainer ships to the
broker.

**What is not built, and cannot be from this side:** verified directly against
`sfl-safety-security-service` - there is no `@RabbitListener` and no `IntegrationEventHandler`
implementation for any `sfl.ifimp.*` event anywhere in that service. The message is published and
delivered to the broker; nothing on the S162a/S174 side reads it, so it never reaches the fast lane it is
named for. This is **recorded, not delivered**, exactly the distinction CORR-07 draws for vendor
integrations, applied here to a cross-service event instead.

**Why this matters more than an ordinary "not consumed yet" gap:** every other S156 event can wait for a
consumer without harm - a rule change or a device registration nobody is listening for is a missed
dashboard update. A critical fault nobody is listening for is a missed emergency escalation, on the
system's own emergency-fault requirement. **This is a go-live blocker for SRS-SFL-S156-03's fast-lane
acceptance criterion** and should be treated as one in any readiness review, not filed as routine
integration debt.

**NFR-PERF1** ("BMS/IoT critical-fault escalation reaches the emergency fast lane within the same
latency target as the existing S162a/S174 fast lane") names its owner as DTI Architecture + Emergency
Coordinator and its target as "to be confirmed jointly with the Phase 1 fast-lane latency target still
open under G-28". That target cannot be measured, let alone met, until a consumer exists on the SSEMP
side to receive the event and something to time the delivery against. This build changes nothing about
that ownership or that open item; it is recorded here so the dependency is visible from the S156 side as
well as wherever G-28 itself is tracked.

## 2. AVAMP-Lite carries no lifecycle data

SRS-SFL-S156-04's user story asks to track "install date, firmware version, warranty and calibration
status" as part of a device's AVAMP asset record. AVAMP-Lite, as built in `sfl-fleet-logistics-service`
(`AssetReference`), carries `id`, `assetCode`, `name`, `category`, `status`, `siteCode`, `locationType`,
`locationReference`, `custodianReference`, `externalReference`, `evidenceReference` and timestamps - no
retirement, firmware or lifecycle-date fields at all.

S156 holds install date, firmware version, firmware-review-due date, warranty expiry and calibration due
date on `bms_devices` itself rather than leaving them unrepresented. This is the pragmatic choice, not the
SRS's literal one: the SRS's picture is that AVAMP is the single source of this data across every module
that touches a physical asset, and S156 holding its own copy means a second Buy-and-Integrate/Hybrid
module (S157, S168's own movable-asset core) that also wants lifecycle data on the same physical device
would either duplicate the same fields again or need to read S156's copy specifically - neither of which
the SRS's platform-identity ambition for AVAMP intends. If the full S168 (CORR-12's "SFL platform-design
choice") lands with these fields, S156's copies should be superseded, not merged - AVAMP would then be
authoritative and S156's local fields become a migration to retire.

## 3. Location resolution has one behaviour not stated explicitly in the SRS

SRS-SFL-S156-01 says an unresolvable-location reading is "quarantined pending mapping" and S156-04 says
device-to-location mapping "is resolved through S152", but neither states what happens to telemetry that
arrives **after** a device's mapped room is archived in S152 (S152 rooms can be archived independently of
S156 knowing about it - masterdata and buildingsystems have no dependency on each other, by
`BuildingSystemsArchitectureTest`'s own rule). This build re-resolves the location on every reading rather
than trusting the device's stored `buildingCode`/`roomId`, so a room archived after mapping produces the
same `BMS_LOCATION_UNRESOLVABLE` quarantine as a device that was never mapped - one error state handling
both cases, which is consistent with the SRS's spirit even though the SRS does not name this specific
timing.

## 4. A released quarantined reading is never evaluated against rules

Not stated in the SRS either way. The choice here: a reading released from quarantine (its device now
registered, or its location now resolved) is stored as historical fact and offered to `BuildingTelemetryObserver`
consumers, but is **never** run through threshold-rule evaluation, even if its value would have breached
a currently-active rule. The reasoning: it arrived late, sometimes hours or days late, and raising a
`CRITICAL` work order today for a breach that (if real) ended when the reading was originally taken sends
a technician to investigate a condition that may no longer exist - worse than the gap it closes. A site
that wants late-arriving breaches evaluated retroactively would need a deliberate design decision (a
"backdated evaluation" mode with its own debounce semantics), not a default. Flagged here so a reviewer
can override it explicitly rather than discover it by absence.

## 5. Rule Conflict scope test is "could overlap", not "does overlap"

SRS-SFL-S156-02's error state names "overlapping active rules for the same sensor". This build's conflict
detector (`RuleEvaluationPolicy.scopesOverlap`) treats a `null` scope field on either rule as matching
anything - so a site-wide rule and a building-scoped rule are reported as a conflict the moment both are
active, even before either has actually evaluated a shared reading. This is deliberately the more
cautious reading: "for the same sensor" is a statement about evaluation-time behaviour (which rule a given
reading is checked against), and a reviewer wants to know about an overlap before the first reading that
would expose it, not after. The SRS text alone does not settle this either way.

## 6. Health rollup is computed on read, not stored

Not a gap so much as a documented choice worth flagging: `BuildingHealthService` computes every level of
the site/building/system/device rollup fresh on each `GET /health` call, from live channel states and
active alerts. There is no `bms_health_snapshot` table and no scheduled job that writes one. This keeps
the "no green default" guarantee airtight - a stored snapshot can go stale exactly the way a stale sensor
does - at the cost of the endpoint doing real work per request rather than a cheap read. For a very large
estate this should be watched; nothing in this build's scenario testing suggested it is a problem at the
scale of MAIN and KSI, and no NFR gives a numeric target to test against (see NFR-PERF3, still open under
G-32).

## 7. Deferred to whichever service builds S158/S168 for real

- **S157 (Energy & Sustainability)** is the intended first consumer of `BuildingDeviceDirectory` and
  `BuildingTelemetryObserver`. Both are implemented for real by this build (the `Unbuilt*` scaffolds are
  deleted); if S157 is built in a sibling worktree that has not yet merged, its own `List<BuildingTelemetryObserver>`
  binding is empty until the merge, which is the documented, valid "no consumer yet" state.
- **S168's cross-module device-identity layer** (CORR-12: S168-04/-05/-06, an SFL platform-architecture
  proposal, not the mapped S168 scope) would be the natural home for the lifecycle fields §2 describes.
  This build does not assume it exists.

## 8. What was not tested end to end, and why

- **A real BACnet or MQTT broker.** `BacnetBridgeTelemetryAdapter` is unit- and scenario-tested against
  JSON payloads shaped like a gateway's already-decoded output (per the SRS's own integration posture -
  "SFL integrates rather than builds the sensing and control layer"). No BACnet/IP or MQTT client library
  is used or referenced anywhere in this module (`BuildingSystemsArchitectureTest` enforces that nothing
  outside `infrastructure.integration` can depend on one either).
- **A real vendor's procurement-gate evidence.** Per CORR-07/§5.2, none exists; `SimulatedBmsTelemetryAdapter`
  and `BacnetBridgeTelemetryAdapter` are both simulators, and `GET /api/v1/facilities/vendor-integrations`
  says so (`SIMULATED_ADAPTER_ONLY` for `bms-iot`).
- **Multi-site incomer topology for "total power loss".** The critical-fault policy treats any
  `ELECTRICAL`-system device reporting `POWER_STATE = 0` as a total loss. A site with multiple independent
  incomers (so that one incomer failing is not a building-wide outage) is not modelled - S152 does not
  hold electrical supply topology, and adding it was out of this build's scope. Flagged as a false-positive
  risk worth a second look before a site with redundant supply goes live on this feature.

## 9. Permissions and roles used, none added

Every permission this module needs (`FACILITIES_BMS_READ`, `FACILITIES_BMS_TELEMETRY_INGEST`,
`FACILITIES_BMS_RULE_MANAGE`, `FACILITIES_BMS_RULE_OVERRIDE`, `FACILITIES_BMS_DEVICE_MANAGE`,
`FACILITIES_BMS_QUARANTINE_RESOLVE`) and every role grant it relies on
(`FacilitiesPermissionMatrix.grantPhaseTwo`) already existed in the foundation commit. Nothing was added
to `SflPermission`, `SflRole` or the matrix by this build.

## 10. New error codes and audit actions

Both blocks were pre-seeded for S156 in the foundation commit with every code this build needed
(`BMS_TELEMETRY_REJECTED`, `BMS_LOCATION_UNRESOLVABLE`, `BMS_DEVICE_UNREGISTERED`, `BMS_RULE_CONFLICT`,
`BMS_RULE_OVERRIDE_REQUIRED`, `BMS_DATA_STALE`; the full `AuditAction` list in `AuditAction.java`'s S156
block). This build appended exactly two audit actions at the end of that block, needed for work this
module does that was not yet named: `BMS_AVAMP_ASSET_PROJECTED` (the AVAMP projection update, §"Consumed"
in the event contracts) and `BMS_TELEMETRY_PURGED` (the retention sweep). No new `FacilitiesErrorCode` was
needed - every quarantine reason and rule-management refusal maps onto a pre-seeded code.
