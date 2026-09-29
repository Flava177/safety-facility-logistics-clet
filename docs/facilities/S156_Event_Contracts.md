# S156 Building Management System / IoT - Event Contracts

- Service: `sfl-facilities-service`
- Outbox: `facilities.outbox_messages`, drained by `FacilitiesOutboxDrainer`
  (`sfl.facilities.messaging.transport`; `local` records delivery without a broker, `rabbitmq` requires
  `SFL_FACILITIES_EVENT_TRANSPORT=rabbitmq`)
- Requirements: `SRS-SFL-S156-01..05`

## Status

Every event below is **recorded and drained** by the platform's shared outbox mechanism - unlike S152's
original status, this module inherits a working drainer from the Phase 2 foundation commit. What is
**not** true yet is that anything on the consuming side listens: SSEMP has no inbound
`IntegrationEventHandler`/`@RabbitListener` for any of these names (verified against
`sfl-safety-security-service`), so `building-critical-fault-detected` is published and drained to the
broker but arrives nowhere. See `S156_Gap_And_Conflict_Report.md`.

## Envelope

Written by `BuildingSystemsEvents.publish` → `ServiceOutbox.record`:

| Field | Source |
|---|---|
| `eventType` | the names below, matching `^sfl\.[a-z0-9]+\.[a-z0-9-]+\.v\d+$` |
| `eventVersion` | `1` for every event in this document |
| `aggregateType` | `BmsQuarantine`, `BmsAlert`, `BmsThresholdRule`, `BmsDevice` |
| `aggregateId` | the aggregate's UUID (or `ruleId` for rule events, stable across versions) |
| `siteScope` | the record's site code |
| `correlationId` | the acting actor's correlation id |
| `causationId` | the acting actor's id |
| `payload` | a purpose-shaped map, references and classifications only - never a vendor payload or free text |

## Events

### `sfl.ifimp.reading-quarantined.v1`

Published for every reading held for review - SRS-SFL-S156-01, -04.

```json
{
  "quarantineId": "…", "siteCode": "MAIN", "sourceId": "BMS-SIM", "deviceCode": "AHU-01",
  "deviceId": null, "channel": "supply-air-temp", "kind": "TEMPERATURE_C",
  "reason": "DEVICE_UNREGISTERED", "flaggedForRegistration": true,
  "observedAt": "2026-09-28T07:59:00Z"
}
```

`deviceId` is `null` when the reason is `DEVICE_UNREGISTERED` (there is no device to reference).
`flaggedForRegistration` is `true` only for that reason - S156-04's "flagged for registration".

### `sfl.ifimp.bms-alert-raised.v1`

A new alert, with or without a linked work order (critical faults always carry one; a rule-driven breach
does once `AutomatedWorkOrderIntake.raise` returns in the same transaction).

```json
{
  "alertId": "…", "siteCode": "MAIN", "buildingCode": "LAW", "roomId": "…", "locationCode": "HALL-A",
  "deviceId": "…", "avampAssetId": "AVAMP-1", "systemType": "HVAC", "alertType": "THRESHOLD_BREACH",
  "criticalFault": null, "ruleId": "…", "ruleVersion": 2, "priority": "HIGH", "status": "ACTIVE",
  "occurrences": 1, "evidenceReadingIds": ["…", "…"],
  "workOrderId": "…", "workOrderNumber": "WO-MAIN-000045",
  "raisedAt": "2026-09-28T08:05:00Z", "lastOccurredAt": "2026-09-28T08:05:00Z", "clearedAt": null
}
```

### `sfl.ifimp.bms-alert-correlated.v1`

A repeated breach linked to an existing alert whose work order is still open in S153 - SRS-SFL-S156-02
("repeated breaches ... correlated rather than raising duplicate work orders"). Same shape as
`bms-alert-raised`, plus `correlatedRuleId` / `correlatedRuleVersion` naming the rule version that
triggered this occurrence (which may differ from the alert's original rule if it was revised in between).

### `sfl.ifimp.bms-alert-cleared.v1`

The condition an alert recorded is no longer present - a reading back in range, a device reporting again
after an offline alert, or a critical-fault signal that stopped repeating. Same shape as
`bms-alert-raised`, with `status: "CLEARED"` and `clearedAt` set. The linked work order is **not**
implied closed - S153 tracks that independently.

### `sfl.ifimp.bms-sensor-offline.v1`

SRS-SFL-S156-03: "a sensor/gateway offline for longer than a configured window raises its own alert
distinct from a telemetry fault." Same shape as `bms-alert-raised` (`alertType: "SENSOR_OFFLINE"`), plus
`lastHeardAt` and `deviceKind`.

### `sfl.ifimp.building-critical-fault-detected.v1`

The cross-service fast-lane signal for S162a/S174 (SSEMP) - SRS-SFL-S156-03, NFR-PERF1. Same shape as
`bms-alert-raised` (`alertType: "CRITICAL_FAULT"`, `criticalFault` one of `TOTAL_POWER_LOSS`,
`LIFT_ENTRAPMENT`, `GENERATOR_FAILED_START_DURING_OUTAGE`), plus `readingId`, `observedAt` and
`fastLane: "S162a/S174"`. **Recorded and drained to the broker; not consumed by SSEMP today** - see the
gap report. NFR-PERF1's latency target cannot be measured end to end until a consumer exists; the owner
named in the SRS (DTI Architecture + Emergency Coordinator) is unchanged by this build.

### `sfl.ifimp.bms-rule-changed.v1`

Every create, revise, disable or enable - SRS-SFL-S156-02 ("rule changes are versioned and audited").

```json
{
  "ruleId": "…", "ruleVersion": 3, "versionId": "…", "siteCode": "MAIN",
  "change": "BMS_RULE_DISABLED_BY_OVERRIDE", "kind": "TEMPERATURE_C", "condition": "ABOVE",
  "enabled": false, "accountableOwner": "maintenance.supervisor"
}
```

`change` is the `AuditAction` name, so a consumer can tell a routine revision from an override without a
second lookup.

### `sfl.ifimp.bms-device-registered.v1`

```json
{
  "deviceId": "…", "avampAssetId": "AVAMP-1", "deviceCode": "AHU-01", "siteCode": "MAIN",
  "buildingCode": "LAW", "roomId": null, "systemType": "HVAC", "kind": "SENSOR", "status": "ACTIVE"
}
```

### `sfl.ifimp.bms-device-retired.v1`

Same shape as `bms-device-registered` (`status: "RETIRED"`), plus `retiredAt`. Never a delete - the
history stays queryable (SRS-SFL-S156-04).

### `sfl.ifimp.bms-device-lifecycle-due.v1`

```json
{
  "deviceId": "…", "avampAssetId": "AVAMP-1", "deviceCode": "AHU-01", "siteCode": "MAIN",
  "item": "CALIBRATION", "dueOn": "2027-03-01", "overdue": false
}
```

`item` is one of `CALIBRATION`, `FIRMWARE_REVIEW`, `WARRANTY_EXPIRY`. Raised once per item per due date -
moving the due date earns a fresh reminder.

## Consumed

### `sfl.avamp.asset-registered.v1` / `sfl.avamp.asset-location-changed.v1`

Published by `sfl-fleet-logistics-service` (`AssetVisibilityService`). Consumed by
`AvampAssetEventHandler` → `AvampAssetProjectionService`, which upserts
`facilities.bms_avamp_asset_projection` keyed on the asset id, ordered by the event's own `updatedAt` (an
older redelivered event does not move the projection backwards). Payload fields read: `id` (falls back to
the envelope's aggregate id), `siteCode`, `assetCode`, `name`, `category`, `status`, `locationType`,
`locationReference`, `updatedAt`. AVAMP-Lite publishes no retirement, firmware or calibration fields - see
the gap report.
