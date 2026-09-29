-- =============================================================================================
-- S157 Energy & Sustainability Monitoring (SRS CLET/DTI/CL9/SFL/SRS/2026/002, SRS-SFL-S157-01..04).
--
-- Nine new tables on the S152 estate. Nothing existing is altered.
--
-- The three things in this file that are not routine:
--
--   1. ux_energy_meters_avamp - one meter per AVAMP asset identity (S157-04 validation: "a device
--      must not be double-registered"). The application asks S156's device register first; this is
--      what holds when two registrations race.
--   2. energy_period_results is written once per closed period and never updated. It carries the
--      budget and tariff VERSION it was computed with, which is how "a change does not retroactively
--      alter historical variance calculations" (S157-02) is true by construction rather than by
--      remembering not to recompute.
--   3. sustainability_kpis holds the cluster-wide rollup under site_code '*'. See that table.
--
-- Every table carries site_code NOT NULL and the migration ends with apply_site_scope_policies()
-- (CORR-06). Foreign keys go only to energy_meters (this module) and to nothing outside it: sites and
-- buildings are held by code, as the S152 references are elsewhere in Phase 2, because a meter's
-- history must survive a building being archived in S152.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- energy_meters
--
-- source decides the ingestion path and, with it, what a reading means (see EnergyMeter / MeterSource):
--   MANUAL      a register (cumulative dial) read typed in by a person; consumption = delta.
--   AMI         interval consumption from the vendor AMI gateway / billing feed, via the signed inbox.
--   BMS_STREAM  interval consumption taken from S156's normalised stream; no vendor connection of ours.
--
-- avamp_asset_id is required for AMI and BMS_STREAM (a physical smart meter has an asset identity)
-- and optional for MANUAL (an old dial meter may not be tagged yet).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.energy_meters (
    id                        UUID PRIMARY KEY,
    site_code                 VARCHAR(40)  NOT NULL,
    building_code             VARCHAR(40)  NOT NULL,
    room_id                   UUID,
    meter_code                VARCHAR(80)  NOT NULL,
    name                      VARCHAR(200) NOT NULL,
    utility                   VARCHAR(20)  NOT NULL,
    canonical_unit            VARCHAR(10)  NOT NULL,
    source                    VARCHAR(20)  NOT NULL,
    avamp_asset_id            VARCHAR(120),
    vendor_meter_ref          VARCHAR(120),
    -- The S156 device this meter's readings arrive under, by value. Never a foreign key: S156's tables
    -- belong to another Phase 2 module (and, while S156 is unbuilt, do not exist).
    bms_device_id             UUID,
    expected_interval_minutes INTEGER      NOT NULL,
    status                    VARCHAR(20)  NOT NULL,
    retired_at                TIMESTAMPTZ,
    retired_reason            VARCHAR(1000),
    created_by                VARCHAR(160) NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL,
    last_modified_by          VARCHAR(160) NOT NULL,
    last_modified_at          TIMESTAMPTZ  NOT NULL,
    record_version            BIGINT       NOT NULL DEFAULT 0,
    source_channel            VARCHAR(40)  NOT NULL,
    correlation_id            VARCHAR(120),
    CONSTRAINT ck_energy_meters_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL')),
    -- The unit is a function of the utility. Stored so a report reads it without knowing the rule;
    -- checked so the two can never disagree.
    CONSTRAINT ck_energy_meters_unit CHECK (
        (utility = 'ELECTRICITY' AND canonical_unit = 'kWh')
        OR (utility = 'WATER' AND canonical_unit = 'm3')
        OR (utility = 'GENERATOR_FUEL' AND canonical_unit = 'litres')),
    CONSTRAINT ck_energy_meters_source CHECK (source IN ('MANUAL', 'AMI', 'BMS_STREAM')),
    CONSTRAINT ck_energy_meters_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_energy_meters_interval CHECK (expected_interval_minutes > 0),
    CONSTRAINT ck_energy_meters_asset CHECK (source = 'MANUAL' OR avamp_asset_id IS NOT NULL),
    CONSTRAINT ck_energy_meters_vendor_ref CHECK (source <> 'AMI' OR vendor_meter_ref IS NOT NULL),
    CONSTRAINT ck_energy_meters_device CHECK (source <> 'BMS_STREAM' OR bms_device_id IS NOT NULL),
    CONSTRAINT ck_energy_meters_retired CHECK ((status = 'RETIRED') = (retired_at IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_meters_code ON facilities.energy_meters (site_code, meter_code);
-- S157-04: one AVAMP identity, one meter - retired meters included, because the identity is the
-- physical device and a retired row still owns that device's history.
CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_meters_avamp
    ON facilities.energy_meters (avamp_asset_id) WHERE avamp_asset_id IS NOT NULL;
-- An AMI message names the vendor's meter reference; two meters answering to one would split a feed.
CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_meters_vendor_ref
    ON facilities.energy_meters (vendor_meter_ref) WHERE vendor_meter_ref IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_energy_meters_site ON facilities.energy_meters (site_code, utility, status);

-- ---------------------------------------------------------------------------------------------
-- energy_readings
--
-- One row per reading received, whatever its path. consumption is always in the meter's canonical
-- unit; vendor_value/vendor_unit keep what the vendor actually sent so a conversion can be audited.
-- A HELD reading is not in the consumption record (energy_daily_consumption) until verified.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.energy_readings (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(40)    NOT NULL,
    meter_id            UUID           NOT NULL REFERENCES facilities.energy_meters (id),
    building_code       VARCHAR(40)    NOT NULL,
    utility             VARCHAR(20)    NOT NULL,
    source              VARCHAR(20)    NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    observed_at         TIMESTAMPTZ    NOT NULL,
    interval_start      TIMESTAMPTZ,
    register_value      NUMERIC(20, 4),
    consumption         NUMERIC(20, 4),
    vendor_value        NUMERIC(20, 6),
    vendor_unit         VARCHAR(20),
    -- Idempotency: the S156 reading id, or "<source>:<idempotencyKey>" for an AMI message.
    source_reference    VARCHAR(200),
    plausibility_checked BOOLEAN       NOT NULL DEFAULT FALSE,
    trailing_daily_average NUMERIC(20, 4),
    band_low            NUMERIC(20, 4),
    band_high           NUMERIC(20, 4),
    hold_reason         VARCHAR(1000),
    entered_by          VARCHAR(160),
    entered_at          TIMESTAMPTZ,
    verified_by         VARCHAR(160),
    verified_at         TIMESTAMPTZ,
    verification_note   VARCHAR(1000),
    note                VARCHAR(1000),
    created_by          VARCHAR(160)   NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    last_modified_by    VARCHAR(160)   NOT NULL,
    last_modified_at    TIMESTAMPTZ    NOT NULL,
    record_version      BIGINT         NOT NULL DEFAULT 0,
    source_channel      VARCHAR(40)    NOT NULL,
    correlation_id      VARCHAR(120),
    CONSTRAINT ck_energy_readings_status CHECK (status IN ('POSTED', 'HELD', 'REJECTED')),
    CONSTRAINT ck_energy_readings_source CHECK (source IN ('MANUAL', 'AMI', 'BMS_STREAM')),
    CONSTRAINT ck_energy_readings_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL')),
    CONSTRAINT ck_energy_readings_consumption CHECK (consumption IS NULL OR consumption >= 0),
    -- A manual reading records who entered it (S157-01 "entered-by/verified-by audit trail").
    CONSTRAINT ck_energy_readings_entered CHECK (source <> 'MANUAL' OR entered_by IS NOT NULL),
    -- Verified-by is a fact about a hold being resolved; it cannot exist on a reading that never waited.
    CONSTRAINT ck_energy_readings_verified CHECK ((verified_by IS NULL) = (verified_at IS NULL)),
    -- And the verifier is somebody else. The service refuses it first; this holds under any caller.
    CONSTRAINT ck_energy_readings_four_eyes CHECK (verified_by IS NULL OR verified_by <> entered_by)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_readings_source_ref
    ON facilities.energy_readings (source_reference) WHERE source_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_energy_readings_meter ON facilities.energy_readings (meter_id, observed_at);
CREATE INDEX IF NOT EXISTS ix_energy_readings_held
    ON facilities.energy_readings (site_code, observed_at) WHERE status = 'HELD';

-- ---------------------------------------------------------------------------------------------
-- energy_daily_consumption - the consumption record (S157-01 workflow: "aggregated into consumption
-- record -> available to budget and dashboard"). One row per meter per UTC day, added to as readings
-- post. Monthly figures are sums of these rows. reading_count is the "received" half of S157-03's
-- completeness indicator.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.energy_daily_consumption (
    id               UUID PRIMARY KEY,
    site_code        VARCHAR(40)    NOT NULL,
    building_code    VARCHAR(40)    NOT NULL,
    meter_id         UUID           NOT NULL REFERENCES facilities.energy_meters (id),
    utility          VARCHAR(20)    NOT NULL,
    consumption_day  DATE           NOT NULL,
    consumption      NUMERIC(20, 4) NOT NULL,
    reading_count    INTEGER        NOT NULL,
    created_by       VARCHAR(160)   NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    last_modified_by VARCHAR(160)   NOT NULL,
    last_modified_at TIMESTAMPTZ    NOT NULL,
    record_version   BIGINT         NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)    NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ck_energy_daily_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL')),
    CONSTRAINT ck_energy_daily_values CHECK (consumption >= 0 AND reading_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_daily_meter_day
    ON facilities.energy_daily_consumption (meter_id, consumption_day);
CREATE INDEX IF NOT EXISTS ix_energy_daily_site
    ON facilities.energy_daily_consumption (site_code, utility, consumption_day);

-- ---------------------------------------------------------------------------------------------
-- energy_budgets / energy_tariffs / energy_emission_factors - versioned, never updated in place.
-- A change is a new row with version + 1. The row a closed period used stays exactly as it was.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.energy_budgets (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)    NOT NULL,
    utility            VARCHAR(20)    NOT NULL,
    period_type        VARCHAR(10)    NOT NULL,
    period_start       DATE           NOT NULL,
    version            INTEGER        NOT NULL,
    consumption_budget NUMERIC(20, 4) NOT NULL,
    cost_budget        NUMERIC(20, 2),
    currency           VARCHAR(3),
    reason             VARCHAR(1000),
    created_by         VARCHAR(160)   NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    last_modified_by   VARCHAR(160)   NOT NULL,
    last_modified_at   TIMESTAMPTZ    NOT NULL,
    record_version     BIGINT         NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)    NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ck_energy_budgets_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL')),
    CONSTRAINT ck_energy_budgets_period CHECK (period_type IN ('MONTH')),
    CONSTRAINT ck_energy_budgets_values CHECK (consumption_budget > 0 AND (cost_budget IS NULL OR cost_budget > 0)),
    CONSTRAINT ck_energy_budgets_currency CHECK ((cost_budget IS NULL) = (currency IS NULL)),
    CONSTRAINT ck_energy_budgets_version CHECK (version >= 1)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_budgets_version
    ON facilities.energy_budgets (site_code, utility, period_type, period_start, version);

CREATE TABLE IF NOT EXISTS facilities.energy_tariffs (
    id               UUID PRIMARY KEY,
    site_code        VARCHAR(40)    NOT NULL,
    utility          VARCHAR(20)    NOT NULL,
    version          INTEGER        NOT NULL,
    unit_rate        NUMERIC(20, 6) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    valid_from       DATE           NOT NULL,
    valid_to         DATE,
    reason           VARCHAR(1000),
    created_by       VARCHAR(160)   NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    last_modified_by VARCHAR(160)   NOT NULL,
    last_modified_at TIMESTAMPTZ    NOT NULL,
    record_version   BIGINT         NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)    NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ck_energy_tariffs_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL')),
    -- Zero is refused: "flagged rather than assumed zero-cost" (S157-02) means a zero rate is never
    -- the way to say "no tariff".
    CONSTRAINT ck_energy_tariffs_rate CHECK (unit_rate > 0),
    CONSTRAINT ck_energy_tariffs_window CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_energy_tariffs_version CHECK (version >= 1)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_tariffs_version
    ON facilities.energy_tariffs (site_code, utility, version);

CREATE TABLE IF NOT EXISTS facilities.energy_emission_factors (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)    NOT NULL,
    utility            VARCHAR(20)    NOT NULL,
    version            INTEGER        NOT NULL,
    -- kg CO2-equivalent per canonical unit (kWh, m3, litre).
    kg_co2e_per_unit   NUMERIC(20, 6) NOT NULL,
    valid_from         DATE           NOT NULL,
    source_reference   VARCHAR(500),
    created_by         VARCHAR(160)   NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    last_modified_by   VARCHAR(160)   NOT NULL,
    last_modified_at   TIMESTAMPTZ    NOT NULL,
    record_version     BIGINT         NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)    NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ck_energy_factors_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL')),
    CONSTRAINT ck_energy_factors_value CHECK (kg_co2e_per_unit >= 0),
    CONSTRAINT ck_energy_factors_version CHECK (version >= 1)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_factors_version
    ON facilities.energy_emission_factors (site_code, utility, version);

-- ---------------------------------------------------------------------------------------------
-- energy_period_results - one closed period's variance, with the versions it used. Insert-only.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.energy_period_results (
    id                    UUID PRIMARY KEY,
    site_code             VARCHAR(40)    NOT NULL,
    utility               VARCHAR(20)    NOT NULL,
    period_type           VARCHAR(10)    NOT NULL,
    period_start          DATE           NOT NULL,
    period_end            DATE           NOT NULL,
    consumption           NUMERIC(20, 4) NOT NULL,
    reading_count         INTEGER        NOT NULL,
    budget_id             UUID,
    budget_version        INTEGER,
    consumption_budget    NUMERIC(20, 4),
    variance_pct          NUMERIC(12, 4),
    threshold_pct         NUMERIC(12, 4) NOT NULL,
    variance_alert        BOOLEAN        NOT NULL,
    tariff_id             UUID,
    tariff_version        INTEGER,
    cost                  NUMERIC(20, 2),
    cost_budget           NUMERIC(20, 2),
    cost_variance_pct     NUMERIC(12, 4),
    currency              VARCHAR(3),
    tariff_missing        BOOLEAN        NOT NULL,
    closed_by             VARCHAR(160)   NOT NULL,
    closed_at             TIMESTAMPTZ    NOT NULL,
    created_by            VARCHAR(160)   NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL,
    last_modified_by      VARCHAR(160)   NOT NULL,
    last_modified_at      TIMESTAMPTZ    NOT NULL,
    record_version        BIGINT         NOT NULL DEFAULT 0,
    source_channel        VARCHAR(40)    NOT NULL,
    correlation_id        VARCHAR(120),
    CONSTRAINT ck_energy_results_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL')),
    CONSTRAINT ck_energy_results_period CHECK (period_type IN ('MONTH') AND period_end > period_start),
    -- A missing tariff means no cost, never a zero cost.
    CONSTRAINT ck_energy_results_tariff CHECK (NOT tariff_missing OR (cost IS NULL AND tariff_id IS NULL)),
    CONSTRAINT ck_energy_results_budget CHECK ((budget_id IS NULL) = (budget_version IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_results_period
    ON facilities.energy_period_results (site_code, utility, period_type, period_start);

-- ---------------------------------------------------------------------------------------------
-- energy_alerts - variance, anomaly and missing-tariff flags. alert_key makes every raise idempotent,
-- so a sweep that runs twice raises once.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.energy_alerts (
    id               UUID PRIMARY KEY,
    alert_key        VARCHAR(200)   NOT NULL,
    site_code        VARCHAR(40)    NOT NULL,
    alert_type       VARCHAR(20)    NOT NULL,
    utility          VARCHAR(20)    NOT NULL,
    building_code    VARCHAR(40),
    meter_id         UUID,
    period_start     DATE           NOT NULL,
    period_end       DATE           NOT NULL,
    observed         NUMERIC(20, 4),
    reference_value  NUMERIC(20, 4),
    deviation_pct    NUMERIC(12, 4),
    threshold_pct    NUMERIC(12, 4),
    message          VARCHAR(1000)  NOT NULL,
    raised_at        TIMESTAMPTZ    NOT NULL,
    created_by       VARCHAR(160)   NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    last_modified_by VARCHAR(160)   NOT NULL,
    last_modified_at TIMESTAMPTZ    NOT NULL,
    record_version   BIGINT         NOT NULL DEFAULT 0,
    source_channel   VARCHAR(40)    NOT NULL,
    correlation_id   VARCHAR(120),
    CONSTRAINT ck_energy_alerts_type CHECK (alert_type IN ('VARIANCE', 'ANOMALY', 'TARIFF_MISSING')),
    CONSTRAINT ck_energy_alerts_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL'))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_energy_alerts_key ON facilities.energy_alerts (alert_key);
CREATE INDEX IF NOT EXISTS ix_energy_alerts_site ON facilities.energy_alerts (site_code, raised_at);

-- ---------------------------------------------------------------------------------------------
-- sustainability_kpis - the read model S225 Analytics reads (S157-03 "standard event/read-model
-- contract"). One row per scope/utility/period, revised in place when a later sweep recomputes an
-- open period; the revision number and the event carry the change.
--
-- The one exception to "site_code is a real site": a CLUSTER row aggregates every site, so it is
-- stored under site_code '*'. site_in_scope('*') is true only for a '*'-scoped session, which is the
-- right answer - a cluster-wide total is built from every site's data, and a MAIN-scoped officer who
-- could read it could infer the other sites' consumption by subtraction.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.sustainability_kpis (
    id                     UUID PRIMARY KEY,
    kpi_key                VARCHAR(200)   NOT NULL,
    site_code              VARCHAR(40)    NOT NULL,
    scope_level            VARCHAR(20)    NOT NULL,
    building_code          VARCHAR(40),
    utility                VARCHAR(20)    NOT NULL,
    canonical_unit         VARCHAR(10)    NOT NULL,
    period_type            VARCHAR(10)    NOT NULL,
    period_start           DATE           NOT NULL,
    period_end             DATE           NOT NULL,
    period_closed          BOOLEAN        NOT NULL,
    consumption            NUMERIC(20, 4) NOT NULL,
    previous_consumption   NUMERIC(20, 4),
    trend_pct              NUMERIC(12, 4),
    carbon_kg_co2e         NUMERIC(20, 4),
    emission_factor_status VARCHAR(20)    NOT NULL,
    expected_readings      INTEGER        NOT NULL,
    received_readings      INTEGER        NOT NULL,
    completeness_pct       NUMERIC(7, 2)  NOT NULL,
    completeness_flag      VARCHAR(10)    NOT NULL,
    minimum_completeness_pct NUMERIC(7, 2) NOT NULL,
    revision               INTEGER        NOT NULL,
    computed_at            TIMESTAMPTZ    NOT NULL,
    created_by             VARCHAR(160)   NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL,
    last_modified_by       VARCHAR(160)   NOT NULL,
    last_modified_at       TIMESTAMPTZ    NOT NULL,
    record_version         BIGINT         NOT NULL DEFAULT 0,
    source_channel         VARCHAR(40)    NOT NULL,
    correlation_id         VARCHAR(120),
    CONSTRAINT ck_kpis_scope CHECK (scope_level IN ('SITE', 'BUILDING', 'CLUSTER')),
    CONSTRAINT ck_kpis_cluster_site CHECK ((scope_level = 'CLUSTER') = (site_code = '*')),
    CONSTRAINT ck_kpis_building CHECK ((scope_level = 'BUILDING') = (building_code IS NOT NULL)),
    CONSTRAINT ck_kpis_utility CHECK (utility IN ('ELECTRICITY', 'WATER', 'GENERATOR_FUEL')),
    CONSTRAINT ck_kpis_period CHECK (period_type IN ('DAY', 'MONTH') AND period_end > period_start),
    CONSTRAINT ck_kpis_factor CHECK (emission_factor_status IN ('APPLIED', 'NOT_CONFIGURED', 'PARTIAL')),
    CONSTRAINT ck_kpis_flag CHECK (completeness_flag IN ('COMPLETE', 'PARTIAL', 'LOW')),
    -- The S157-03 rule in the schema: a KPI below 100% can never be labelled COMPLETE.
    CONSTRAINT ck_kpis_complete CHECK (completeness_flag <> 'COMPLETE' OR completeness_pct >= 100),
    CONSTRAINT ck_kpis_counts CHECK (expected_readings >= 0 AND received_readings >= 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_kpis_key ON facilities.sustainability_kpis (kpi_key);
CREATE INDEX IF NOT EXISTS ix_kpis_site_period ON facilities.sustainability_kpis (site_code, period_start);

-- ---------------------------------------------------------------------------------------------
-- Runtime configuration defaults (Configuration Without Code; versioned by the configuration table).
-- ---------------------------------------------------------------------------------------------
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version,
     updated_by, updated_at)
SELECT gen_random_uuid(), seed.config_key, NULL, seed.config_value, seed.value_type, seed.description,
       NOW(), 0, 'system', NOW()
FROM (VALUES
    ('energy.plausibility.band-pct', '50', 'DECIMAL',
     'S157-01: a manual reading whose implied daily consumption is more than this percentage above or below the trailing daily average is held for verification.'),
    ('energy.plausibility.trailing-days', '90', 'INTEGER',
     'S157-01: how many days of posted readings form the trailing average for the plausibility band.'),
    ('energy.plausibility.min-history', '2', 'INTEGER',
     'S157-01: posted readings needed before the band is evaluated; below this a manual reading posts unchecked and says so.'),
    ('energy.variance.threshold-pct', '10', 'DECIMAL',
     'S157-02: actual-versus-budget variance above this percentage raises a variance alert at period close.'),
    ('energy.anomaly.spike-pct', '50', 'DECIMAL',
     'S157-02: a day more than this percentage above the trailing daily baseline raises an anomaly flag.'),
    ('energy.anomaly.baseline-days', '14', 'INTEGER',
     'S157-02: days in the trailing baseline for spike detection.'),
    ('energy.anomaly.min-baseline-days', '7', 'INTEGER',
     'S157-02: days of history needed before a spike can be judged.'),
    ('energy.kpi.period', 'MONTH', 'STRING',
     'S157-03: KPI computation period, DAY or MONTH.'),
    ('energy.kpi.min-completeness-pct', '80', 'DECIMAL',
     'S157-03: below this percentage of expected readings a KPI is published with a LOW completeness flag.'),
    ('energy.expected-interval-minutes.MANUAL', '43200', 'INTEGER',
     'Default expected reading interval for a manual meter (monthly walk).'),
    ('energy.expected-interval-minutes.AMI', '60', 'INTEGER',
     'Default expected reading interval for an AMI meter.'),
    ('energy.expected-interval-minutes.BMS_STREAM', '60', 'INTEGER',
     'Default expected reading interval for a meter on the S156 stream.'),
    ('energy.close.lookback-periods', '3', 'INTEGER',
     'How many ended months the close sweep revisits for a site/utility not yet closed.'),
    ('energy.retention.reading-days', '2555', 'INTEGER',
     'SRS 4.2: raw readings older than this are purged; daily consumption, period results and KPIs are kept.')
) AS seed(config_key, config_value, value_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM facilities.facility_runtime_configuration existing
    WHERE existing.config_key = seed.config_key
      AND existing.site_code IS NULL
      AND existing.effective_to IS NULL
);

SELECT facilities.apply_site_scope_policies();
