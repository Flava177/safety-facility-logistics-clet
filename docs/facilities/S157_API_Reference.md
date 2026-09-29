# S157 Energy & Sustainability Monitoring - API reference

**28 paths** under `/api/v1/facilities/energy`, on `sfl-facilities-service` (port 8091). Every response
is the platform envelope `{data, error}`; every error carries an SRS-worded code and a correlation ID.

Actor headers (`X-SFL-User`, `X-SFL-Roles`, `X-SFL-Sites`, `X-SFL-Source-Channel`) are resolved by
`FacilitiesActorResolver` while `sfl.security.enabled=false`; in production the same `ActorContext`
comes from the OIDC principal.

`Idempotency-Key` is honoured on the state-creating POSTs marked below and nowhere else. The AMI
ingest endpoint is idempotent on its own envelope's `idempotencyKey`, not the header - see NFR-SEC2
below.

`expectedVersion` on a PATCH body is optional; supplying it turns a lost update into
`VERSION_CONFLICT`.

---

## Meters and integration health - `/api/v1/facilities/energy`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| `POST` | `/meters` | `FACILITIES_ENERGY_METER_MANAGE` | **Idempotent.** Resolves to an S152 site/building(/room). Refused with `ENERGY_DEVICE_DOUBLE_REGISTERED` (409) if the AVAMP id is already a meter, or is an S156 device and the source is not `BMS_STREAM` (S157-04) |
| `PATCH` | `/meters/{meterId}` | `FACILITIES_ENERGY_METER_MANAGE` | Rename, re-time, or move onto `BMS_STREAM` - the S157-04 repair for an AMI meter S156 has since enrolled. No other source change is accepted |
| `PATCH` | `/meters/{meterId}/retirement` | `FACILITIES_ENERGY_METER_MANAGE` | Reason required. Identity and history are kept |
| `GET` | `/meters` | `FACILITIES_ENERGY_READ` | `siteCode`, `utility`, `activeOnly` |
| `GET` | `/meters/{meterId}` | `FACILITIES_ENERGY_READ` | |
| `GET` | `/health` | `FACILITIES_ENERGY_READ` | `VendorIntegrationRegistry.gateStatus("energy-metering")`, active meters by source, held-reading count, latest posted reading, and S157-04 device conflicts (a non-`BMS_STREAM` meter whose AVAMP id S156 has since enrolled) |

## Readings and consumption - `/api/v1/facilities/energy`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| `POST` | `/readings/ingest` | `FACILITIES_ENERGY_READING_INGEST` | **AMI gateway, signed.** NFR-SEC2: authenticated (HMAC over the raw body), source-allowlisted on `VendorChannel.ENERGY_METERING`, schema-validated, before anything is posted. Headers `X-SFL-Source`, `X-SFL-Signature`, `X-SFL-Signed-At`; envelope `messageType`, `idempotencyKey`, `siteCode`, `payload`. A forged or malformed message is rejected, logged and never posted. A duplicate `idempotencyKey` answers 200 with the original reading, not reposted |
| `POST` | `/readings/manual` | `FACILITIES_ENERGY_READING_ENTER` | **Idempotent.** Register value for a `MANUAL` meter. Outside the plausibility band of the trailing average: **202** with `ENERGY_READING_IMPLAUSIBLE`, held for verification, not posted |
| `PATCH` | `/readings/{readingId}/verification` | `FACILITIES_ENERGY_READING_VERIFY` | Approve (optionally overriding the consumption to post) or reject a held reading. Refused with `ENERGY_SELF_VERIFICATION` (403) if the caller entered it |
| `GET` | `/readings` | `FACILITIES_ENERGY_READ` | `siteCode`, `meterId`, `status` (`HELD` is the verification queue), `from`, `to`, `limit` |
| `GET` | `/readings/{readingId}` | `FACILITIES_ENERGY_READ` | Carries the full entered-by / verified-by trail |
| `GET` | `/consumption` | `FACILITIES_ENERGY_READ` | `siteCode`, `utility`, `granularity` (`DAY`\|`MONTH`), `groupBy` (`SITE`\|`BUILDING`), `from`, `to`. Posted readings only |

## Budgets, tariffs and variance - `/api/v1/facilities/energy`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| `POST` | `/budgets` | `FACILITIES_ENERGY_BUDGET_MANAGE` | New version for a site/utility/month. A consumption budget is required; the cost budget and currency are optional together |
| `GET` | `/budgets` | `FACILITIES_ENERGY_READ` | `siteCode`, `from`, `to` - every version, in period/utility/version order |
| `POST` | `/tariffs` | `FACILITIES_ENERGY_BUDGET_MANAGE` | New version. Unit rate must be more than zero - a zero rate is refused, never used to mean "no tariff" |
| `GET` | `/tariffs` | `FACILITIES_ENERGY_READ` | `siteCode`, `utility` |
| `POST` | `/periods/close` | `FACILITIES_ENERGY_BUDGET_MANAGE` | Closes an ended month for one utility or every utility with consumption or a budget. Freezes variance with the budget/tariff versions used; idempotent - re-closing returns the same frozen result |
| `GET` | `/periods` | `FACILITIES_ENERGY_READ` | `siteCode`, `from`, `to` - closed periods only |
| `GET` | `/variance` | `FACILITIES_ENERGY_READ` | `siteCode`, `utility`, `periodStart`. The frozen result once closed; otherwise a provisional figure computed on read (`closed: false`), never stored |
| `GET` | `/cost` | `FACILITIES_ENERGY_READ` | `siteCode`, `utility`, `periodStart`. Consumption priced at the applicable tariff; **422 `ENERGY_TARIFF_MISSING`** when there is consumption and no tariff - never a zero cost |
| `GET` | `/alerts` | `FACILITIES_ENERGY_READ` | `siteCode`, `type` (`VARIANCE`\|`ANOMALY`\|`TARIFF_MISSING`), `since`, `limit`. Newest first, with site/utility/building/meter drill-down |
| `POST` | `/anomalies/evaluation` | `FACILITIES_ENERGY_BUDGET_MANAGE` | Judges a completed day for spikes on demand - what the hourly sweep does. `siteCode`, `day` (optional body) |

## Sustainability KPIs and emission factors - `/api/v1/facilities/energy`

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| `GET` | `/kpis` | `FACILITIES_ENERGY_READ` | `siteCode` (`*` for the cluster rollup - visible only to a cross-site caller), `scope` (`SITE`\|`BUILDING`\|`CLUSTER`), `utility`, `periodType`, `from`, `to`, `limit`. The S225 read model |
| `POST` | `/kpis/computation` | `FACILITIES_ENERGY_BUDGET_MANAGE` | Computes and publishes now - what the hourly sweep does. Cluster rows only for a caller who may see every site |
| `POST` | `/emission-factors` | `FACILITIES_ENERGY_BUDGET_MANAGE` | New version, kg CO2e per canonical unit |
| `GET` | `/emission-factors` | `FACILITIES_ENERGY_READ` | `siteCode` - every utility's versions |

## Error codes (S157 block, `FacilitiesErrorCode`)

| Code | HTTP | Meaning |
| --- | --- | --- |
| `ENERGY_READING_IMPLAUSIBLE` | 202 | Manual entry outside the plausibility band; held, not posted (S157-01) |
| `ENERGY_TARIFF_MISSING` | 422 | Consumption with no configured tariff; cost never assumed zero (S157-02) |
| `ENERGY_DEVICE_DOUBLE_REGISTERED` | 409 | One AVAMP identity, one meter/device - registration or a conflicting AMI message refused (S157-04) |
| `ENERGY_SELF_VERIFICATION` | 403 | A held reading must be verified by someone other than the enterer (S157-01) |
| `VENDOR_MESSAGE_REJECTED` | 401 | A forged or malformed AMI message, or a module-level rejection after verification (NFR-SEC2) |
