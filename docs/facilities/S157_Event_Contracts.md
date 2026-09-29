# S157 Energy & Sustainability Monitoring - event contracts

All names follow the catalogue rule `sfl.{platform}.{event-name}.v{version}` (CORR-04), enforced at the
write path by `ServiceEventType`. Every payload carries references, classifications and numbers - never
a person's name or free text somebody typed (a rejection reason, a verification note): a consumer that
needs who did something reads the audit trail (S204), which is access-controlled; a broker message is
not. This is a build-time addition to `docs/integration/event-catalog.md`'s IFIMP block, not a
replacement for it - the catalog itself is merged later, per the shared build boundary.

## Published

| Event | Trigger | Aggregate | Payload (`EnergyEvents` record) |
| --- | --- | --- | --- |
| `sfl.ifimp.energy-meter-registered.v1` | A meter is registered (S157-01) | `EnergyMeter` | `MeterRegistered`: meter id, code, site/building/room, utility, canonical unit, source, AVAMP asset id, S156 device id (`BMS_STREAM` only) |
| `sfl.ifimp.energy-reading-held.v1` | A manual reading falls outside the plausibility band (S157-01) | `EnergyReading` | `ReadingHeld`: reading id, meter id, site, utility, observed-at, register value, implied consumption, trailing daily average, band low/high, hold code (always `ENERGY_READING_IMPLAUSIBLE`) |
| `sfl.ifimp.energy-reading-verified.v1` | A held reading is approved into the consumption record (S157-01) | `EnergyReading` | `ReadingVerified`: reading id, meter id, site, utility, observed-at, posted consumption, verified-at |
| `sfl.ifimp.energy-variance-alert-raised.v1` | Actual exceeds budget beyond the configured threshold, named at period close (S157-02) | `EnergyAlert` | `AlertRaised` (`alertType=VARIANCE`): alert id/key, site, utility, period, observed consumption, budget, deviation %, threshold % |
| `sfl.ifimp.energy-anomaly-flagged.v1` | A day's consumption spikes beyond the trailing baseline, independent of budget (S157-02) | `EnergyAlert` | `AlertRaised` (`alertType=ANOMALY`): alert id/key, site, utility, building, meter, day, observed consumption, trailing baseline, deviation %, threshold % |
| `sfl.ifimp.energy-tariff-missing.v1` | A closed period has consumption and no applicable tariff (S157-02) | `EnergyAlert` | `AlertRaised` (`alertType=TARIFF_MISSING`): alert id/key, site, utility, period, observed consumption; `referenceValue`/`deviationPct` are `null` |
| `sfl.ifimp.sustainability-kpi-published.v1` | A KPI is computed on schedule and is new or has changed (S157-03) | `SustainabilityKpi` | `KpiPublished`: kpi id/key, scope (`SITE`\|`BUILDING`\|`CLUSTER`), site (`*` for the cluster row), building, utility, unit, period type/start/end, `periodClosed`, consumption, previous consumption, trend %, carbon kg CO2e (nullable), emission-factor status, **expected and received readings, completeness %, completeness flag** (always present, never omitted for an incomplete period), minimum-completeness threshold, revision, computed-at |

`sustainability-kpi-published` is the S225 Analytics contract in full: the persisted read model (`GET
/api/v1/facilities/energy/kpis`) plus this event, not a one-off export. A consumer that rebuilds state
from the read model and stays current from the event needs nothing else. **S225 has no consumer today**
(see the gap report) - the event is published and durable in the outbox regardless.

Every event above also produces an `AuditAction` in the same transaction (`ENERGY_METER_REGISTERED`,
`ENERGY_READING_HELD`, `ENERGY_READING_VERIFIED`, `ENERGY_VARIANCE_ALERT_RAISED`,
`ENERGY_ANOMALY_FLAGGED`, `ENERGY_TARIFF_MISSING_FLAGGED`, `SUSTAINABILITY_KPI_PUBLISHED`) plus three
more not tied to a single published event (`ENERGY_METER_UPDATED`, `ENERGY_METER_RETIRED`,
`ENERGY_READING_ENTERED`, `ENERGY_READING_REJECTED`, `ENERGY_BUDGET_VERSION_CREATED`,
`ENERGY_TARIFF_VERSION_CREATED`, `ENERGY_EMISSION_FACTOR_VERSION_CREATED`, `ENERGY_TELEMETRY_REJECTED`)
- the seeded S157 block in `AuditAction.java`, plus `ENERGY_READING_INGESTED`, `ENERGY_PERIOD_CLOSED`
and `ENERGY_READING_RETENTION_PURGED`, appended at the end of that block for this build (see the final
report for why).

## Consumed

| Event / call | Source | How S157 uses it |
| --- | --- | --- |
| `BuildingTelemetryObserver.readingAccepted(NormalisedTelemetryReading)` | S156, in-process (same deployable), same transaction | `BmsEnergyTelemetryObserver` filters to `energyRelevant()` kinds and hands them to `EnergyReadingService.consumeStream`, which posts a consumption reading for the matching `BMS_STREAM` meter - S157-04's "one ingestion boundary, not two" |
| `BuildingDeviceDirectory.findByAvampAssetId(String)` | S156, in-process | Asked at meter registration (is this AVAMP id already an S156 device?) and at AMI ingestion (has this AMI meter's device since been enrolled in S156?) - both S157-04 checks |

Neither is a broker event: S156 and S157 share a deployable, so this is a real in-process call and a
same-transaction observer callback, per the house rule that only a cross-*service* reference is
simulated. `sfl-facilities-service`'s inbound integration queue (`IntegrationEventHandler`, binding
`ftlmp.#`, `ssemp.#`, `avamp.#`) carries nothing S157 needs today.

## Vendor inbound (not a catalog event - NFR-SEC2)

The AMI gateway message itself is not a catalogue event; it is the signed envelope
`VendorMessageVerifier` authenticates on `VendorChannel.ENERGY_METERING` before `EnergyReadingService`
ever sees it. Its rejection is: audited (`VENDOR_MESSAGE_REJECTED`), forwarded to SIEM (S208), and
published as `sfl.integration.vendor-message-rejected.v1` by `VendorRejectionRecorder` - shared
platform behaviour, not S157's own event.
