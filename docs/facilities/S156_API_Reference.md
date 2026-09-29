# S156 Building Management System / IoT - API reference

Base path `/api/v1/facilities/building-systems`. Every response is the platform envelope `{data, error}`.
`X-Correlation-ID` is honoured and echoed. Actor headers in development (`SFL_SECURITY_ENABLED=false`):
`X-SFL-User`, `X-SFL-Roles`, `X-SFL-Sites`, `X-SFL-Source-Channel`.

---

## Telemetry - `/telemetry`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/telemetry` | `FACILITIES_BMS_TELEMETRY_INGEST` | Raw JSON body, signed and verified before parsing. See headers below. |

### Headers

| Header | Required | Meaning |
|---|---|---|
| `X-SFL-Source` | yes | The allowlisted vendor source id, e.g. `BMS-SIM` |
| `X-SFL-Signature` | yes | Hex HMAC-SHA256 over `signedAt + "." + rawBody` |
| `X-SFL-Signed-At` | yes | ISO-8601 instant the message was signed |

### Body envelope

```json
{
  "messageType": "bms.telemetry",
  "idempotencyKey": "sim-000123",
  "siteCode": "MAIN",
  "format": "sfl-bms-sim/v1",
  "readings": [
    {"deviceId": "AHU-01", "channel": "supply-air-temp", "kind": "TEMPERATURE_C",
     "value": 21.5, "observedAt": "2026-09-28T07:59:00Z"}
  ]
}
```

`format` selects the adapter; see the runbook for the shipped formats and their shapes. The response is
always `200` (never an exception for a per-item quarantine outcome) unless the whole message is rejected
(`401 VENDOR_MESSAGE_REJECTED`) or malformed (`400`, adapter could not read it).

### Response

```json
{
  "data": {
    "inboxId": "…", "siteCode": "MAIN", "duplicate": false,
    "items": [
      {"index": 0, "deviceCode": "AHU-01", "channel": "supply-air-temp", "outcome": "ACCEPTED",
       "readingId": "…", "quarantineId": null, "code": null}
    ]
  },
  "error": null
}
```

`outcome` is `ACCEPTED` or `QUARANTINED`. For a quarantined item, `code` is one of
`BMS_DEVICE_UNREGISTERED`, `BMS_LOCATION_UNRESOLVABLE`, `IMPLAUSIBLE_VALUE`, `OUT_OF_ORDER`,
`CLOCK_SKEW`.

---

## Readings - `/readings`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/readings?siteCode=&deviceId=&channel=&from=&to=&limit=` | `FACILITIES_BMS_READ` | Newest first, capped at 1000. |

---

## Devices - `/devices`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/devices` | `FACILITIES_BMS_DEVICE_MANAGE` | Requires an active AVAMP asset already projected. |
| `PATCH` | `/devices/{deviceId}` | `FACILITIES_BMS_DEVICE_MANAGE` | Lifecycle facts and/or S152 location. |
| `PATCH` | `/devices/{deviceId}/retirement` | `FACILITIES_BMS_DEVICE_MANAGE` | Retires, never deletes. |
| `GET` | `/devices?siteCode=&status=` | `FACILITIES_BMS_READ` | `status` is `ACTIVE` or `RETIRED`. |
| `GET` | `/devices/{deviceId}` | `FACILITIES_BMS_READ` | |

### `POST /devices`

```json
{
  "siteCode": "MAIN", "deviceCode": "AHU-01", "avampAssetId": "AVAMP-1",
  "name": "AHU 1", "systemType": "HVAC", "kind": "SENSOR",
  "buildingCode": "LAW", "roomId": null, "expectedIntervalSeconds": 300,
  "installedOn": "2026-01-15", "firmwareVersion": "2.3.1",
  "firmwareReviewDueOn": "2027-01-15", "warrantyExpiresOn": "2029-01-15",
  "calibrationDueOn": "2027-03-01"
}
```

`systemType` is one of `HVAC`, `ELECTRICAL`, `LIGHTING`, `WATER`, `LIFT`, `GENERATOR`, `ENVIRONMENTAL`,
`OCCUPANCY`. `kind` is one of `SENSOR`, `GATEWAY`, `CONTROLLER`, `METER`.

---

## Quarantine - `/quarantine`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/quarantine?siteCode=&status=` | `FACILITIES_BMS_READ` | `status` is `PENDING`, `RELEASED` or `DISCARDED`. |
| `PATCH` | `/quarantine/{id}/release` | `FACILITIES_BMS_QUARANTINE_RESOLVE` | Only for `DEVICE_UNREGISTERED` / `LOCATION_UNRESOLVABLE`; re-checks the mapping. |
| `PATCH` | `/quarantine/{id}/discard` | `FACILITIES_BMS_QUARANTINE_RESOLVE` | `reason` required. |

---

## Threshold rules - `/rules`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `POST` | `/rules` | `FACILITIES_BMS_RULE_MANAGE` | Creates version 1. |
| `PATCH` | `/rules/{ruleId}` | `FACILITIES_BMS_RULE_MANAGE` | Writes a new version; the prior one is kept, superseded. |
| `PATCH` | `/rules/{ruleId}/disablement` | `FACILITIES_BMS_RULE_OVERRIDE` | `reason` and `accountableOwner` both required. |
| `PATCH` | `/rules/{ruleId}/enablement` | `FACILITIES_BMS_RULE_MANAGE` or `_OVERRIDE` | |
| `GET` | `/rules?siteCode=` | `FACILITIES_BMS_READ` | Current versions only. |
| `GET` | `/rules/{ruleId}/history` | `FACILITIES_BMS_READ` | Every version, oldest first. |
| `GET` | `/rules/conflicts?siteCode=` | `FACILITIES_BMS_READ` | Logged Rule Conflicts (S156-02). |

### `POST /rules`

```json
{
  "siteCode": "MAIN", "name": "Server room hot", "quantity": "TEMPERATURE_C",
  "systemType": "HVAC", "deviceId": null, "buildingCode": "LAW", "roomId": null,
  "condition": "ABOVE", "lowerLimit": null, "upperLimit": 28, "codes": [],
  "debounce": "PT5M", "priority": "HIGH", "reason": "Server room protection threshold"
}
```

`condition` is one of `ABOVE`, `BELOW`, `OUTSIDE_BAND`, `CODE_MATCH`. `quantity` is a `MeasuredQuantity`
name (`TEMPERATURE_C`, `RELATIVE_HUMIDITY_PCT`, `CO2_PPM`, `POWER_STATE`, `ELECTRICAL_ENERGY_KWH`,
`WATER_VOLUME_M3`, `GENERATOR_FUEL_LITRES`, `FUEL_TANK_LEVEL_PCT`, `LIFT_STATUS`, `GENERATOR_RUN_STATE`,
`FAULT_CODE`). `priority` (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`) is the suggested S153 work-order priority.

### `PATCH /rules/{ruleId}/disablement`

```json
{"reason": "chiller replacement in progress", "accountableOwner": "maintenance.supervisor"}
```

---

## Alerts - `/alerts`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/alerts?siteCode=&status=` | `FACILITIES_BMS_READ` | `status` is `ACTIVE` or `CLEARED`. |
| `GET` | `/alerts/{alertId}` | `FACILITIES_BMS_READ` | |

---

## Health - `/health`

| Method | Path | Permission | Notes |
|---|---|---|---|
| `GET` | `/health?siteCode=` | `FACILITIES_BMS_READ` | Site → building → system → device rollup. |

```json
{
  "siteCode": "MAIN", "state": "DEGRADED", "generatedAt": "2026-09-28T08:15:00Z",
  "activeAlerts": 2, "linkedWorkOrders": 1, "procurementGate": "SIMULATED_ADAPTER_ONLY",
  "buildings": [
    {"buildingCode": "LAW", "state": "DEGRADED", "systems": [
      {"systemType": "HVAC", "state": "DEGRADED", "devices": [
        {"deviceId": "…", "deviceCode": "AHU-01", "avampAssetId": "AVAMP-1",
         "locationCode": "HALL-A", "state": "DEGRADED", "reason": "Threshold breach inside its debounce window",
         "lastObservedAt": "2026-09-28T08:14:30Z", "activeAlertIds": ["…"], "workOrderNumbers": []}
      ]}
    ]}
  ]
}
```

`state` at every level is `NORMAL`, `DEGRADED`, `OFFLINE`, `FAULT` or `UNKNOWN` - never a bare boolean, and
never defaulted to healthy for a device with no data.

---

## Error codes this module maps

| Code | HTTP | Meaning |
|---|---|---|
| `VENDOR_MESSAGE_REJECTED` | 401 | Forged, unauthenticated or malformed vendor message (shared) |
| `BMS_TELEMETRY_REJECTED` | 401 | A module-level validation failure after verification |
| `BMS_LOCATION_UNRESOLVABLE` | 202 | Quarantined pending an S152 mapping |
| `BMS_DEVICE_UNREGISTERED` | 202 | Quarantined pending device registration |
| `BMS_RULE_OVERRIDE_REQUIRED` | 422 | Disabling a rule without a reason and a named owner |
| `BMS_RULE_CONFLICT` | - | Not thrown; logged and queryable via `/rules/conflicts` |
| `BMS_DATA_STALE` | - | Not thrown; surfaced as `UNKNOWN` in the health response |

Everything else falls back to the platform's generic mapping (`VALIDATION_FAILED` 400,
`UNAUTHORIZED_SCOPE` 403, `RECORD_NOT_FOUND` 404, `VERSION_CONFLICT` 409, `INVALID_STATE_TRANSITION` 422).
