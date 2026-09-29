# Runbook - S157 Energy & Sustainability Monitoring

**Scope.** The `energy` module inside `sfl-facilities-service`: the meter register, AMI/manual/S156-
stream ingestion, the daily/monthly consumption record, budgets, tariffs, period close and variance
alerting, anomaly flags, and the sustainability KPI read model published toward S225 Analytics. For "the
service will not start" or "database unreachable" in general, use
[`incident-response.md`](incident-response.md) first - this runbook assumes the service is up.

## 1. What it owns

- Schema `facilities`, tables `energy_meters`, `energy_readings`, `energy_daily_consumption`,
  `energy_budgets`, `energy_tariffs`, `energy_emission_factors`, `energy_period_results`,
  `energy_alerts`, `sustainability_kpis` (migration `V17__energy_sustainability.sql`).
- REST under `/api/v1/facilities/energy/**` - see
  [`S157_API_Reference.md`](../facilities/S157_API_Reference.md).
- Events `sfl.ifimp.energy-*` and `sfl.ifimp.sustainability-kpi-published.v1` - see
  [`S157_Event_Contracts.md`](../facilities/S157_Event_Contracts.md).
- Row-level security from its first migration (`V17` ends with
  `SELECT facilities.apply_site_scope_policies();`), verified by `Phase2RowLevelSecurityCoverageTest`
  and this module's own `EnergyRowLevelSecurityTest`.

## 2. Health check

```
GET /actuator/health                                        # is the service up at all
GET /api/v1/facilities/energy/health?siteCode=<SITE>         # this module's own view
```

The energy health response carries:

- `gateStatus` - `SIMULATED_ADAPTER_ONLY` today (see §7); never expect `GATE_EVIDENCE_ON_FILE` until
  the procurement-gate register at `docs/integration/procurement-gate/S157-energy-metering.md` has
  every row closed.
- `activeMetersBySource` - a count per `MANUAL` / `AMI` / `BMS_STREAM`. A `BMS_STREAM` count of zero
  while S156 is deployed and reporting energy-relevant readings means the observer wiring or the meter
  registrations are missing, not that nothing is wrong.
- `heldReadings` - the manual-entry verification queue size at that site. Growing without anyone
  clearing it is a process problem (nobody is checking), not a service problem.
- `latestReadingAt` - the most recent *posted* reading. Compare against wall-clock time per §5.
- `deviceConflicts` - meters registered `AMI` or `MANUAL` whose AVAMP id S156 has since enrolled as a
  device. Each is refusing AMI messages (§7). The fix is `PATCH /meters/{id}` with
  `{"source":"BMS_STREAM"}`.

## 3. Scheduled jobs

All four gated by `sfl.energy.scheduling.enabled` (default `true`, `matchIfMissing`), running as the
platform account `system.energy` (`SiteScopedPrincipal`, `SFL_ADMIN`, scope `*`) so RLS sees every site.
Each swallows its own exceptions and logs, so one bad row does not cancel the schedule for the process's
life - check the log for `... sweep failed; it will be retried on the next run` rather than assuming
silence means health.

| Job | Property (default) | What it does |
| --- | --- | --- |
| Period close | `sfl.energy.close.interval-ms` (3 600 000 = 1h), initial delay `sfl.energy.close.initial-delay-ms` (300 000 = 5m) | Closes every ended month, per site and utility with consumption or a budget, not yet closed, over the last `energy.close.lookback-periods` months (config key, default 3). Idempotent - a closed period is returned unchanged, never recomputed |
| Anomaly | `sfl.energy.anomaly.interval-ms` (3 600 000), initial delay `sfl.energy.anomaly.initial-delay-ms` (360 000 = 6m) | Judges *yesterday* for every active `AMI`/`BMS_STREAM` meter (manual meters are skipped - see §6). Idempotent on `(meter, day)` |
| KPI publication | `sfl.energy.kpi.interval-ms` (3 600 000), initial delay `sfl.energy.kpi.initial-delay-ms` (420 000 = 7m) | Computes the current and previous period's KPIs (site, building, cluster) for every site the platform account can see - i.e. every site - and publishes only the new or changed ones |
| Retention purge | `sfl.energy.retention.interval-ms` (86 400 000 = 24h), initial delay `sfl.energy.retention.initial-delay-ms` (900 000 = 15m) | Deletes posted/rejected readings observed before `energy.retention.reading-days` days ago (config key, default 2555 ≈ 7 years, floor 366). **Never purges a `HELD` reading** - an unresolved question is not retention debris |

## 4. Configuration keys (Configuration Without Code - `RuntimeConfigurationPort`)

Seeded by `V17`, site-overridable, versioned. Read `GET
/api/v1/facilities/runtime-configuration?siteCode=<SITE>` for the values actually in force; the table
below is what ships.

| Key | Default | Meaning |
| --- | --- | --- |
| `energy.plausibility.band-pct` | `50` | S157-01: percent either side of the trailing daily average a manual reading may fall before it is held |
| `energy.plausibility.trailing-days` | `90` | Window the trailing daily average is computed over |
| `energy.plausibility.min-history` | `2` | Posted readings needed before the band applies; below this, posts unchecked |
| `energy.variance.threshold-pct` | `10` | S157-02: overspend beyond this raises a variance alert at close |
| `energy.anomaly.spike-pct` | `50` | S157-02: a day this far above the trailing baseline raises an anomaly flag |
| `energy.anomaly.baseline-days` | `14` | Trailing window for the anomaly baseline |
| `energy.anomaly.min-baseline-days` | `7` | Days of baseline needed before a spike can be judged |
| `energy.kpi.period` | `MONTH` | S157-03 computation period (`DAY` or `MONTH`); platform-wide, not per site - the cluster total needs one period type for every site |
| `energy.kpi.min-completeness-pct` | `80` | Below this, a KPI publishes with the `LOW` completeness flag; platform-wide |
| `energy.expected-interval-minutes.MANUAL` | `43200` (30 days) | Default expected reading interval for a newly registered manual meter |
| `energy.expected-interval-minutes.AMI` | `60` | Same, for AMI |
| `energy.expected-interval-minutes.BMS_STREAM` | `60` | Same, for a meter on the S156 stream |
| `energy.close.lookback-periods` | `3` | Months the close sweep revisits |
| `energy.retention.reading-days` | `2555` | SRS §4.2 raw-reading retention |

## 5. Failure modes and what to do

**A held reading nobody is verifying.** `heldReadings` on the health view grows. Query the queue:

```sql
SELECT id, meter_id, site_code, observed_at, register_value, hold_reason, entered_by
  FROM facilities.energy_readings
 WHERE status = 'HELD'
 ORDER BY observed_at;
```

Route to the site's Facilities Manager or Director - `FACILITIES_ENERGY_READING_VERIFY`, held by
neither the Energy & Sustainability Officer role nor the person who entered the reading
(`ENERGY_SELF_VERIFICATION` refuses that combination even for an administrator). This is a process gap,
not a service defect: nothing here can verify a reading for a human.

**AMI readings have stopped arriving for a site.** Check `latestReadingAt` on the health view, then the
vendor inbox for rejections:

```sql
SELECT received_at, source_system, rejection_reason, rejection_detail
  FROM facilities.vendor_inbox_messages
 WHERE channel = 'ENERGY_METERING' AND outcome = 'REJECTED'
 ORDER BY received_at DESC LIMIT 50;
```

`SOURCE_NOT_ALLOWED` / `SIGNATURE_INVALID` - a secret rotated on one side and not the other; check
`sfl.facilities.vendor-inbox.sources.AMI-SIM.secret` (or the real source's key once one is configured).
`SITE_NOT_PERMITTED` - the source is not registered for that site in
`sfl.facilities.vendor-inbox.sources.*.sites`. `SCHEMA_INVALID` after signature checks pass is a module-
level rejection - most often an unrecognised vendor unit; check the detail against
`SimulatedMeteringAdapter`'s spelling table (`Wh`, `kWh`, `MWh`; `m3`, `kL`, `L`, `USgal`/`gal`,
`impgal`), or `ENERGY_DEVICE_DOUBLE_REGISTERED` in the detail, meaning the meter's device has since been
enrolled in S156 - see the device-conflict fix in §2.

**A variance alert did not fire when it should have.** Confirm the period actually closed (an unclosed
period computes a *provisional* variance on every `GET /variance` call and never stores or alerts on
it):

```sql
SELECT site_code, utility, period_start, variance_pct, threshold_pct, variance_alert, budget_version
  FROM facilities.energy_period_results
 WHERE site_code = '<SITE>' AND utility = '<UTILITY>'
 ORDER BY period_start DESC LIMIT 6;
```

If there is no row for the month, either nothing closed it yet (the sweep runs hourly with a five-minute
delay after startup - wait, or call `POST /periods/close` directly) or the month has not ended. A row
with `variance_alert = false` under threshold is correct, not a bug - check `threshold_pct` and the
actual percentage before assuming otherwise.

**A budget was corrected after close and the alert still shows the old number.** That is by design
(SRS-SFL-S157-02: "a change does not retroactively alter historical variance calculations"). The closed
result keeps the budget *version* it used (`budget_version` above); a new budget version is for the
*next* close, or for the next time someone deliberately re-derives a report from raw consumption. There
is no "recompute this closed period" operation, on purpose.

**A KPI shows `LOW` completeness and someone asks why.** That is the mechanism working, not failing
(S157-03: "never withheld, never presented as complete"). Compare `expectedReadings` against
`receivedReadings` on the KPI row; for a manual-metered site this is expected on every `DAY`-granularity
KPI (see the gap report, §6) - use the `MONTH` KPI for a site without smart metering.

**KPIs are not appearing for a site an actor should see.** The cluster row (`siteCode = "*"`) is visible
only to a caller whose own site scope includes `*` - a single-site energy officer correctly gets
`403 UNAUTHORIZED_SCOPE` asking for it. Per-site and per-building rows follow ordinary site scoping.

**The retention sweep purged a reading somebody needed.** It never purges a `HELD` reading, and never
purges `energy_daily_consumption`, `energy_period_results` or `sustainability_kpis` - only raw
`energy_readings` rows older than the configured retention window, and only `POSTED`/`REJECTED` ones.
If a raw reading beyond that window is needed for audit, it is the aggregated daily/monthly figures and
the audit trail (S204) that remain the record of truth; the raw reading was never the only copy of what
happened.

## 6. What is simulated, not integrated (CORR-07, SRS §5.2)

**The AMI/vendor billing feed is simulated end to end.** `SimulatedMeteringAdapter` is the shipped
`MeteringVendorPort`; no vendor product has been selected, and
`docs/integration/procurement-gate/S157-energy-metering.md` lists every §5.2 evidence item as open. `GET
/api/v1/facilities/energy/health` and the vendor-integrations endpoint both report
`SIMULATED_ADAPTER_ONLY`. **Do not describe metering as "integrated" in any status report** until that
register is complete and `SFL_S157_PROCUREMENT_GATE_EVIDENCE` is set.

**S156 (BMS/IoT) is a real in-process dependency, not a stub** - `BuildingDeviceDirectoryAdapter` and
`BmsEnergyTelemetryObserver` call the real S156 module in this deployable. Until S156 ships,
`UnbuiltBuildingDeviceDirectory` answers "no such device" for every lookup, which is correct - it means
every meter behaves as if no S156 device exists, `BMS_STREAM` registration is unreachable, and the
shared-telemetry acceptance criterion (S157-04) cannot be exercised end-to-end until both modules are
deployed together.

**S225 Analytics has no consumer today.** `sfl.ifimp.sustainability-kpi-published.v1` is published to
the outbox on every schedule run; nothing downstream reads it yet. The read model
(`GET /api/v1/facilities/energy/kpis`) is the only way to see published KPIs until a consumer exists.

**S149 (Annual Report / IFRS-IPSAS) is not built.** The mapping names it as an eventual consumer of
sustainability figures; nothing in this phase implements it.

## 7. RLS notes

Every S157 table carries `site_code NOT NULL` and the `site_scope_read` policy from `V17`'s final
statement. The one row that is *not* an ordinary site is `sustainability_kpis.site_code = '*'` for a
cluster-wide KPI - `facilities.site_in_scope('*')` is true only for a session whose own
`app.site_scopes` includes `*`, so a single-site session genuinely cannot read the cluster row even
though the table's own RLS predicate is satisfied at the database level the same way as any other row;
the application layer refuses the same request earlier, with a clearer message, but the database is the
backstop either way.

## 8. Escalate when

- `VENDOR_MESSAGE_REJECTED` volume spikes on `ENERGY_METERING` outside a known secret rotation - that
  is either a forged-message attempt or a vendor-side defect, and both go to Security (S208 already has
  the SIEM forward; confirm it landed).
- The audit chain reports tampered after any direct database intervention here - see
  `incident-response.md` §5, same procedure, no S157-specific variant.
- A fix requires writing to `energy_period_results`, `energy_budgets` or `energy_tariffs` directly.
  Those tables are insert-only by design (a new version, never an edit); a direct `UPDATE` breaks the
  "closed periods never change" guarantee the whole variance workflow depends on. Get a second person
  and record exactly what ran.
