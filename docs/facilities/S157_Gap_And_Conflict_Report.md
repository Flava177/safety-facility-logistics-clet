# S157 Energy & Sustainability Monitoring - gap and conflict report

What the SRS is silent on, what it conflicts with elsewhere in the platform, and what depends on
something not yet built. Flagged, not silently resolved, per the build brief.

## 1. The SRS is silent on cumulative-register versus interval consumption - decided and documented

SRS-SFL-S157-01 says readings are "ingested", "normalised" and "aggregated"; it never says whether a
reading is the number on a meter's dial (a cumulative register, ever-increasing) or the quantity
consumed in an interval. Getting this wrong is silent and large in either direction: summing register
reads as if they were interval consumption would report a year's usage every month; differencing
interval figures would report noise.

**Decision, made per source and documented in `MeterSource`'s Javadoc:**

- `MANUAL` - a person reads the **register**. Consumption is the delta from the previous posted
  register read on the same meter. The first read on a meter is a baseline and posts no consumption.
- `AMI` - the vendor gateway/billing feed sends **interval consumption** directly (quantity over
  `intervalStart..intervalEnd`), which is how AMI head-ends and billing exports conventionally publish.
  A vendor product that only exposes registers would need differencing in its own adapter, before the
  reading reaches this module.
- `BMS_STREAM` - S156's contract documents `GENERATOR_FUEL_LITRES` as "consumed over the reading
  interval"; the same is **assumed**, not confirmed, for `ELECTRICAL_ENERGY_KWH` and
  `WATER_VOLUME_M3`, because the S156 contract (`MeasurementKind`) does not state it for those two
  kinds. If S156 ships either as a cumulative register, S157 will double-count on every reading. This
  needs closing with the S156 build, not guessed at here.

## 2. S157-02: overspend-only variance alerting, underspend not flagged

The acceptance criterion is "consumption exceeds budget by more than the configured threshold" -
overspend. An equally large *underspend* (a meter gone quiet, a wing shut early) is visible in the
stored result (a negative `variancePct`) but raises no alert. Whether a large underspend should also
alert - it looks exactly like a meter failure from the numbers alone - is not stated in the SRS and is
left as an open design question; the completeness indicator (S157-03) and the anomaly flag are the
tools that would actually catch a meter gone silent, and both exist.

## 3. S157-02: one tariff per period, not a mid-month split

`EnergyTariff.applicable` prices a whole period at the highest tariff version whose validity covers the
period's *first day*. A tariff that changes mid-month is costed from the following month. Splitting a
month's cost across two tariff versions by day would need daily costing the SRS does not ask for and
no acceptance criterion names; flagged rather than half-built.

## 4. S157-03: KPI publication has no consumer, and the S149 mapping is not built

The mapping used to classify Phase 2 systems names S225 Analytics as S157's publication target; the
Integrations table in this SRS also names the Annual Report module (S149 IFRS/IPSAS Reporting &
Consolidation) via the mapping. What exists: the full contract on S157's side - a persisted read model
(`sustainability_kpis`, `GET /api/v1/facilities/energy/kpis`) and the event
`sfl.ifimp.sustainability-kpi-published.v1`, published on the standard outbox, per S157-03's own
validation ("not a one-off export"). **S225 has no consumer today** - nothing in this codebase reads
the queue this event would route to. **S149 is not built at all** in this phase, and nothing named
"Annual Report integration" exists to hand data to. Both are recorded here rather than stubbed with a
fake consumer that would misreport the platform's actual integration state (CORR-07's spirit applied
beyond vendor gates).

## 5. S157-02 cost currency: a budget and its tariff must agree, or cost variance is withheld

`PeriodVarianceResult.costVariancePct` is computed only when the budget's currency and the applicable
tariff's currency are the same string. The SRS gives no currency-conversion rule, and CLET's own
utilities are priced in GHS throughout, so this has never been exercised with a mismatch; a
multi-currency estate would need an exchange-rate policy this build does not invent. Consumption
variance (unit-based, currency-free) is unaffected and always computed.

## 6. S157-03 completeness for a manual (monthly-walk) meter

`CompletenessPolicy.expected` treats every meter's expected-reading count the same way: period length
divided by the meter's expected interval, floored at one. For a `MANUAL` meter on a monthly walk
(the seeded default, `energy.expected-interval-minutes.MANUAL=43200`), a daily KPI's completeness is
necessarily low for every day that is not the walk day - which is an honest answer (a manual meter
genuinely does not report daily), but it means a site with only manual meters will show `LOW`
completeness on every daily KPI it ever publishes. A monthly KPI does not have this problem. This is
recorded as a characteristic of the mechanism, not a defect, but it means the KPI screen should default
to monthly for sites without smart metering - a UI decision outside this backend build's scope.

## 7. S157-04: the reverse conflict (AMI first, S156 enrolment later) is repair-only

Registration refuses an AMI or MANUAL registration over an AVAMP id S156 already ingests (the common
direction). The **reverse** - a meter registered as AMI first, whose physical device is enrolled into
S156 *afterwards* - cannot be refused at registration, because it was a legitimate registration at the
time. It is instead:

- refused at ingestion (`EnergyReadingService` rejects any further AMI message for that meter once
  S156 has claimed the AVAMP id, via `VendorMessageVerifier.reject`, so no double-counted reading is
  ever posted), and
- surfaced on the health view (`GET /api/v1/facilities/energy/health` lists it under
  `deviceConflicts`), with `PATCH /meters/{id}` (moving the source to `BMS_STREAM`) as the documented
  repair.

This is a real gap in the sense that nothing *automatically* migrates the meter - an operator has to
notice the health view and act. Automating it would mean S157 reaching into S156's enrolment workflow,
which the module boundary (S157 is a consumer of S156, never the reverse) does not allow.

## 8. Vendor integration: procurement gate is open, as SRS §5.2/CORR-07 require it to be reported

No metering vendor has been selected. `SimulatedMeteringAdapter` behind `MeteringVendorPort` is the
whole of the "integration" today; `GET /api/v1/facilities/energy/health` and
`docs/integration/procurement-gate/S157-energy-metering.md` (landed with the shared foundation) both
report `SIMULATED_ADAPTER_ONLY`, and nothing in this build claims otherwise. This is the required state
per CORR-07, not a gap to close in this pass.

## 9. What was not touched, and why it is correct that it was not

- **S156's tables, package, or contract implementation.** S157 consumes `BuildingDeviceDirectory` and
  `BuildingTelemetryObserver` through its own port/adapter (`EnergyDeviceDirectoryPort`,
  `BuildingDeviceDirectoryAdapter`, `BmsEnergyTelemetryObserver`) and touches nothing else in
  `buildingsystems`, enforced by `EnergyArchitectureTest`.
- **S153's notification port.** `NotificationPort.NotificationKind` has no energy-specific kind, and
  S157-02's "Facilities Director notified with drill-down" is met today by the `EnergyAlert` record
  (drill-down) and its outbox event, not by a person-directed notification. Adding an energy kind to a
  sibling module's shared enum was out of this module's file boundary; flagged rather than done.
- **S168_fuel Fuel Management reconciliation** (SRS §5.1: "Consumption reconciliation with S157 and
  S167 utilisation analytics"). S168_fuel is a Phase 1 Fast-Track system outside this build's scope;
  S157's `GENERATOR_FUEL` utility and its litres readings are the S157 half of that reconciliation, and
  nothing on the S168_fuel side exists yet to reconcile against.

## 10. New audit actions and error codes appended (for the merge)

Appended at the end of the pre-seeded S157 blocks only, per the build brief:

- `AuditAction`: `ENERGY_READING_INGESTED`, `ENERGY_PERIOD_CLOSED`, `ENERGY_READING_RETENTION_PURGED`.
- `FacilitiesErrorCode`: none beyond the pre-seeded S157 block (`ENERGY_READING_IMPLAUSIBLE`,
  `ENERGY_TARIFF_MISSING`, `ENERGY_PERIOD_INCOMPLETE`, `ENERGY_DEVICE_DOUBLE_REGISTERED`,
  `ENERGY_SELF_VERIFICATION`) was sufficient; `ENERGY_PERIOD_INCOMPLETE` was seeded but is not raised
  as an exception - S157-03 requires a low-completeness KPI to be *published*, not refused, so the
  seeded code is unused by design and its wording is instead carried on the KPI's own
  `completenessFlag`/`completenessPct` fields. Recorded here so it is not mistaken for an oversight.
