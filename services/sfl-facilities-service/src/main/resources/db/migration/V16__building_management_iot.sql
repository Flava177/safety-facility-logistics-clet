-- =============================================================================================
-- S156 Building Management System (BMS) / IoT - SRS-SFL-S156-01..05 (Phase 2, Buy and Integrate).
--
-- Eight new tables on the S152 estate. Nothing existing is altered. Every table carries site_code and
-- is covered by row-level security from this, its first migration (CORR-06, NFR-SEC3) - the last
-- statement of the file applies the policies.
--
-- What the schema itself guarantees, rather than leaving to Java:
--   * one S156 device per AVAMP asset, ever (ux_bms_devices_avamp)            - S156-04, S157-04
--   * one active device per gateway device code per site (ux_bms_devices_code) - S156-04
--   * a retired device says who, when and why (ck_bms_devices_retired)         - retire, never delete
--   * exactly one current version per rule (ux_bms_rules_current)             - S156-02 versioning
--   * a disabled rule names a reason and an accountable owner (ck_bms_rules_override) - S156-02
--   * a replayed vendor message cannot store a reading or quarantine row twice - S156-01
--
-- Cross-module and cross-service ids are held by value: the AVAMP asset id (FTLMP), the S153 work order,
-- the vendor inbox message. The only foreign keys are to this module's own tables and to S152's rooms.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- bms_avamp_asset_projection - S156's copy of AVAMP-Lite assets, fed by sfl.avamp.asset-* events.
-- Device registration is checked against this, so it holds while FTLMP is unreachable.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bms_avamp_asset_projection (
    id                 UUID PRIMARY KEY,
    avamp_asset_id     VARCHAR(120) NOT NULL,
    site_code          VARCHAR(40)  NOT NULL,
    asset_code         VARCHAR(120),
    name               VARCHAR(200),
    category           VARCHAR(60),
    asset_status       VARCHAR(40),
    location_type      VARCHAR(40),
    location_reference VARCHAR(200),
    last_event_type    VARCHAR(120),
    avamp_updated_at   TIMESTAMPTZ,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    record_version     BIGINT       NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)  NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ux_bms_avamp_asset UNIQUE (avamp_asset_id)
);

-- ---------------------------------------------------------------------------------------------
-- bms_devices - the IoT device inventory (S156-04). Lifecycle facts (install, firmware, warranty,
-- calibration) live here because AVAMP-Lite carries none; see the S156 gap report.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bms_devices (
    id                        UUID PRIMARY KEY,
    site_code                 VARCHAR(40)  NOT NULL,
    device_code               VARCHAR(120) NOT NULL,
    avamp_asset_id            VARCHAR(120) NOT NULL,
    name                      VARCHAR(200) NOT NULL,
    system_type               VARCHAR(30)  NOT NULL,
    device_kind               VARCHAR(20)  NOT NULL,
    building_code             VARCHAR(80)  NOT NULL,
    room_id                   UUID REFERENCES facilities.facility_rooms (id),
    room_code                 VARCHAR(80),
    expected_interval_seconds INTEGER      NOT NULL,
    installed_on              DATE,
    firmware_version          VARCHAR(80),
    firmware_review_due_on    DATE,
    warranty_expires_on       DATE,
    calibration_due_on        DATE,
    status                    VARCHAR(20)  NOT NULL,
    retired_at                TIMESTAMPTZ,
    retired_by                VARCHAR(160),
    retirement_reason         VARCHAR(1000),
    calibration_reminded_for  DATE,
    firmware_reminded_for     DATE,
    warranty_reminded_for     DATE,
    created_by                VARCHAR(160) NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL,
    last_modified_by          VARCHAR(160) NOT NULL,
    last_modified_at          TIMESTAMPTZ  NOT NULL,
    record_version            BIGINT       NOT NULL DEFAULT 0,
    source_channel            VARCHAR(40)  NOT NULL,
    correlation_id            VARCHAR(120),
    CONSTRAINT ck_bms_devices_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_bms_devices_system CHECK (system_type IN
        ('HVAC', 'ELECTRICAL', 'LIGHTING', 'WATER', 'LIFT', 'GENERATOR', 'ENVIRONMENTAL', 'OCCUPANCY')),
    CONSTRAINT ck_bms_devices_kind CHECK (device_kind IN ('SENSOR', 'GATEWAY', 'CONTROLLER', 'METER')),
    CONSTRAINT ck_bms_devices_interval CHECK (expected_interval_seconds > 0),
    CONSTRAINT ck_bms_devices_retired CHECK (status <> 'RETIRED'
        OR (retired_at IS NOT NULL AND retired_by IS NOT NULL AND retirement_reason IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bms_devices_avamp ON facilities.bms_devices (avamp_asset_id);
-- Partial: a retired device releases its gateway code to its replacement.
CREATE UNIQUE INDEX IF NOT EXISTS ux_bms_devices_code
    ON facilities.bms_devices (site_code, device_code) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS ix_bms_devices_site ON facilities.bms_devices (site_code, status);

-- ---------------------------------------------------------------------------------------------
-- bms_channel_states - per device-channel watermark, latest value and debounce state. Operational,
-- changes on every reading, not audited per change.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bms_channel_states (
    id                 UUID PRIMARY KEY,
    site_code          VARCHAR(40)  NOT NULL,
    device_id          UUID         NOT NULL REFERENCES facilities.bms_devices (id),
    channel            VARCHAR(160) NOT NULL,
    quantity           VARCHAR(40)  NOT NULL,
    system_type        VARCHAR(30),
    building_code      VARCHAR(80),
    last_observed_at   TIMESTAMPTZ,
    last_received_at   TIMESTAMPTZ,
    last_value         NUMERIC(20, 6),
    last_reading_id    UUID,
    breach_rule_id     UUID,
    breach_started_at  TIMESTAMPTZ,
    breach_reading_ids TEXT,
    alert_id           UUID,
    critical_alert_id  UUID,
    created_by         VARCHAR(160) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_modified_by   VARCHAR(160) NOT NULL,
    last_modified_at   TIMESTAMPTZ  NOT NULL,
    record_version     BIGINT       NOT NULL DEFAULT 0,
    source_channel     VARCHAR(40)  NOT NULL,
    correlation_id     VARCHAR(120),
    CONSTRAINT ux_bms_channel_states UNIQUE (device_id, channel),
    CONSTRAINT ck_bms_channel_breach CHECK ((breach_rule_id IS NULL) = (breach_started_at IS NULL))
);

CREATE INDEX IF NOT EXISTS ix_bms_channel_states_building
    ON facilities.bms_channel_states (site_code, building_code, quantity);
CREATE INDEX IF NOT EXISTS ix_bms_channel_states_pending
    ON facilities.bms_channel_states (breach_started_at) WHERE breach_rule_id IS NOT NULL AND alert_id IS NULL;

-- ---------------------------------------------------------------------------------------------
-- bms_telemetry_readings - readings held as fact. Immutable but for evidence_hold, which exempts a
-- reading an alert cites from the retention purge.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bms_telemetry_readings (
    id                          UUID PRIMARY KEY,
    site_code                   VARCHAR(40)    NOT NULL,
    device_id                   UUID           NOT NULL REFERENCES facilities.bms_devices (id),
    avamp_asset_id              VARCHAR(120)   NOT NULL,
    device_code                 VARCHAR(120)   NOT NULL,
    building_code               VARCHAR(80)    NOT NULL,
    room_id                     UUID,
    location_code               VARCHAR(80)    NOT NULL,
    channel                     VARCHAR(160)   NOT NULL,
    quantity                    VARCHAR(40)    NOT NULL,
    reading_value               NUMERIC(20, 6) NOT NULL,
    observed_at                 TIMESTAMPTZ    NOT NULL,
    received_at                 TIMESTAMPTZ    NOT NULL,
    source_system               VARCHAR(80),
    message_format              VARCHAR(80),
    idempotency_key             VARCHAR(200),
    item_index                  INTEGER        NOT NULL DEFAULT 0,
    inbox_id                    UUID,
    released_from_quarantine_id UUID,
    evidence_hold               BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by                  VARCHAR(160)   NOT NULL,
    created_at                  TIMESTAMPTZ    NOT NULL,
    last_modified_by            VARCHAR(160)   NOT NULL,
    last_modified_at            TIMESTAMPTZ    NOT NULL,
    record_version              BIGINT         NOT NULL DEFAULT 0,
    source_channel              VARCHAR(40)    NOT NULL,
    correlation_id              VARCHAR(120)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bms_readings_message
    ON facilities.bms_telemetry_readings (source_system, idempotency_key, item_index)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_bms_readings_device
    ON facilities.bms_telemetry_readings (device_id, channel, observed_at);
CREATE INDEX IF NOT EXISTS ix_bms_readings_site_time
    ON facilities.bms_telemetry_readings (site_code, observed_at);

-- ---------------------------------------------------------------------------------------------
-- bms_quarantined_readings - authenticated readings held for review (S156-01, S156-04).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bms_quarantined_readings (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(40)    NOT NULL,
    source_system       VARCHAR(80),
    message_format      VARCHAR(80),
    idempotency_key     VARCHAR(200),
    item_index          INTEGER        NOT NULL DEFAULT 0,
    inbox_id            UUID,
    device_code         VARCHAR(120)   NOT NULL,
    channel             VARCHAR(160)   NOT NULL,
    quantity            VARCHAR(40)    NOT NULL,
    reading_value       NUMERIC(20, 6) NOT NULL,
    observed_at         TIMESTAMPTZ    NOT NULL,
    received_at         TIMESTAMPTZ    NOT NULL,
    reason              VARCHAR(40)    NOT NULL,
    detail              VARCHAR(1000),
    device_id           UUID REFERENCES facilities.bms_devices (id),
    status              VARCHAR(20)    NOT NULL,
    resolved_by         VARCHAR(160),
    resolved_at         TIMESTAMPTZ,
    resolution_note     VARCHAR(1000),
    released_reading_id UUID,
    created_by          VARCHAR(160)   NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    last_modified_by    VARCHAR(160)   NOT NULL,
    last_modified_at    TIMESTAMPTZ    NOT NULL,
    record_version      BIGINT         NOT NULL DEFAULT 0,
    source_channel      VARCHAR(40)    NOT NULL,
    correlation_id      VARCHAR(120),
    CONSTRAINT ck_bms_quarantine_reason CHECK (reason IN
        ('DEVICE_UNREGISTERED', 'LOCATION_UNRESOLVABLE', 'IMPLAUSIBLE_VALUE', 'OUT_OF_ORDER', 'CLOCK_SKEW')),
    CONSTRAINT ck_bms_quarantine_status CHECK (status IN ('PENDING', 'RELEASED', 'DISCARDED')),
    -- A discard must say why; a release must say what it became.
    CONSTRAINT ck_bms_quarantine_discard CHECK (status <> 'DISCARDED' OR resolution_note IS NOT NULL),
    CONSTRAINT ck_bms_quarantine_release CHECK (status <> 'RELEASED' OR released_reading_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bms_quarantine_message
    ON facilities.bms_quarantined_readings (source_system, idempotency_key, item_index)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_bms_quarantine_site
    ON facilities.bms_quarantined_readings (site_code, status);

-- ---------------------------------------------------------------------------------------------
-- bms_threshold_rules - one row per rule VERSION (S156-02: "rule changes are versioned").
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bms_threshold_rules (
    id                UUID PRIMARY KEY,
    rule_id           UUID           NOT NULL,
    rule_version      INTEGER        NOT NULL,
    site_code         VARCHAR(40)    NOT NULL,
    name              VARCHAR(200)   NOT NULL,
    quantity          VARCHAR(40)    NOT NULL,
    system_type       VARCHAR(30),
    device_id         UUID REFERENCES facilities.bms_devices (id),
    building_code     VARCHAR(80),
    room_id           UUID REFERENCES facilities.facility_rooms (id),
    rule_condition    VARCHAR(20)    NOT NULL,
    lower_limit       NUMERIC(20, 6),
    upper_limit       NUMERIC(20, 6),
    codes             VARCHAR(500),
    debounce_seconds  BIGINT         NOT NULL,
    priority          VARCHAR(20)    NOT NULL,
    enabled           BOOLEAN        NOT NULL,
    change_reason     VARCHAR(1000),
    override_reason   VARCHAR(1000),
    accountable_owner VARCHAR(160),
    superseded_at     TIMESTAMPTZ,
    created_by        VARCHAR(160)   NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    last_modified_by  VARCHAR(160)   NOT NULL,
    last_modified_at  TIMESTAMPTZ    NOT NULL,
    record_version    BIGINT         NOT NULL DEFAULT 0,
    source_channel    VARCHAR(40)    NOT NULL,
    correlation_id    VARCHAR(120),
    CONSTRAINT ux_bms_rules_version UNIQUE (rule_id, rule_version),
    CONSTRAINT ck_bms_rules_condition CHECK (rule_condition IN ('ABOVE', 'BELOW', 'OUTSIDE_BAND', 'CODE_MATCH')),
    CONSTRAINT ck_bms_rules_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_bms_rules_debounce CHECK (debounce_seconds >= 0),
    -- "A rule cannot be silently disabled without an audited override and a named accountable owner."
    CONSTRAINT ck_bms_rules_override CHECK (enabled OR (override_reason IS NOT NULL AND accountable_owner IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bms_rules_current
    ON facilities.bms_threshold_rules (rule_id) WHERE superseded_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_bms_rules_site_current
    ON facilities.bms_threshold_rules (site_code, quantity) WHERE superseded_at IS NULL;

-- ---------------------------------------------------------------------------------------------
-- bms_rule_conflicts - the Rule Conflict error state, logged for review.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bms_rule_conflicts (
    id                  UUID PRIMARY KEY,
    site_code           VARCHAR(40)  NOT NULL,
    rule_id             UUID         NOT NULL,
    conflicting_rule_id UUID         NOT NULL,
    winning_rule_id     UUID         NOT NULL,
    quantity            VARCHAR(40)  NOT NULL,
    detail              VARCHAR(1000),
    detected_at         TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(160) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    last_modified_by    VARCHAR(160) NOT NULL,
    last_modified_at    TIMESTAMPTZ  NOT NULL,
    record_version      BIGINT       NOT NULL DEFAULT 0,
    source_channel      VARCHAR(40)  NOT NULL,
    correlation_id      VARCHAR(120),
    CONSTRAINT ux_bms_rule_conflicts UNIQUE (rule_id, conflicting_rule_id, winning_rule_id)
);

CREATE INDEX IF NOT EXISTS ix_bms_rule_conflicts_site ON facilities.bms_rule_conflicts (site_code, detected_at);

-- ---------------------------------------------------------------------------------------------
-- bms_alerts - automated alerts (S156-02, -03). work_order_id is S153's, held by value.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS facilities.bms_alerts (
    id                   UUID PRIMARY KEY,
    site_code            VARCHAR(40)   NOT NULL,
    device_id            UUID          NOT NULL REFERENCES facilities.bms_devices (id),
    avamp_asset_id       VARCHAR(120)  NOT NULL,
    device_code          VARCHAR(120)  NOT NULL,
    building_code        VARCHAR(80)   NOT NULL,
    room_id              UUID,
    location_code        VARCHAR(80)   NOT NULL,
    system_type          VARCHAR(30),
    alert_type           VARCHAR(30)   NOT NULL,
    critical_fault       VARCHAR(50),
    rule_id              UUID,
    rule_version         INTEGER,
    priority             VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    summary              VARCHAR(1000),
    evidence_reading_ids TEXT,
    occurrences          INTEGER       NOT NULL DEFAULT 1,
    raised_at            TIMESTAMPTZ   NOT NULL,
    last_occurred_at     TIMESTAMPTZ,
    cleared_at           TIMESTAMPTZ,
    work_order_id        UUID,
    work_order_number    VARCHAR(60),
    created_by           VARCHAR(160)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    last_modified_by     VARCHAR(160)  NOT NULL,
    last_modified_at     TIMESTAMPTZ   NOT NULL,
    record_version       BIGINT        NOT NULL DEFAULT 0,
    source_channel       VARCHAR(40)   NOT NULL,
    correlation_id       VARCHAR(120),
    CONSTRAINT ck_bms_alerts_type CHECK (alert_type IN
        ('THRESHOLD_BREACH', 'FAULT_CODE', 'CRITICAL_FAULT', 'SENSOR_OFFLINE')),
    CONSTRAINT ck_bms_alerts_critical CHECK ((alert_type = 'CRITICAL_FAULT') = (critical_fault IS NOT NULL)),
    CONSTRAINT ck_bms_alerts_critical_kind CHECK (critical_fault IS NULL OR critical_fault IN
        ('TOTAL_POWER_LOSS', 'LIFT_ENTRAPMENT', 'GENERATOR_FAILED_START_DURING_OUTAGE')),
    CONSTRAINT ck_bms_alerts_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_bms_alerts_status CHECK (status IN ('ACTIVE', 'CLEARED'))
);

CREATE INDEX IF NOT EXISTS ix_bms_alerts_device ON facilities.bms_alerts (device_id, status);
CREATE INDEX IF NOT EXISTS ix_bms_alerts_site ON facilities.bms_alerts (site_code, status, raised_at);

-- ---------------------------------------------------------------------------------------------
-- Default S156 runtime configuration (Configuration Without Code). Site-scoped values override
-- these; every key is listed in docs/runbooks/s156-building-management-iot.md.
-- ---------------------------------------------------------------------------------------------
INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, version,
     updated_by, updated_at)
SELECT gen_random_uuid(), seed.config_key, NULL, seed.config_value, seed.value_type, seed.description,
       NOW(), 0, 'system', NOW()
FROM (VALUES
    ('bms.validation.clock-skew-tolerance', 'PT2M', 'DURATION',
     'How far a reading may be out of order, or ahead of the receive time, before it is quarantined.'),
    ('bms.rules.default-debounce', 'PT5M', 'DURATION',
     'Debounce window for a threshold rule whose author did not set one.'),
    ('bms.offline.window', 'PT30M', 'DURATION',
     'A device silent for longer than this raises a SENSOR_OFFLINE alert.'),
    ('bms.health.stale-after-intervals', '2', 'INTEGER',
     'Missed expected reporting intervals after which a device shows UNKNOWN (stale), never NORMAL.'),
    ('bms.critical.lift-entrapment-codes', '3', 'STRING',
     'Normalised LIFT_STATUS codes that mean a passenger is trapped (critical fault).'),
    ('bms.critical.outage-window', 'PT15M', 'DURATION',
     'How recent a total-power-loss signal must be for a generator failed-start to be critical.'),
    ('bms.lifecycle.reminder-lead-days', '30', 'INTEGER',
     'Days before a calibration, firmware-review or warranty date that a reminder is raised.'),
    ('bms.retention.readings-days', '400', 'INTEGER',
     'Telemetry readings older than this are purged, unless an alert cites them as evidence.'),
    ('bms.sweep.batch', '500', 'INTEGER', 'Rows processed per sustained-breach sweep.'),
    ('bms.plausibility.temperature-c.min', '-50', 'DECIMAL', 'Physically plausible minimum, degrees C.'),
    ('bms.plausibility.temperature-c.max', '150', 'DECIMAL', 'Physically plausible maximum, degrees C.'),
    ('bms.plausibility.relative-humidity-pct.min', '0', 'DECIMAL', 'Plausible minimum relative humidity.'),
    ('bms.plausibility.relative-humidity-pct.max', '100', 'DECIMAL', 'Plausible maximum relative humidity.'),
    ('bms.plausibility.co2-ppm.min', '0', 'DECIMAL', 'Plausible minimum CO2, ppm.'),
    ('bms.plausibility.co2-ppm.max', '20000', 'DECIMAL', 'Plausible maximum CO2, ppm.'),
    ('bms.plausibility.power-state.min', '0', 'DECIMAL', 'Power state is 0 or 1.'),
    ('bms.plausibility.power-state.max', '1', 'DECIMAL', 'Power state is 0 or 1.'),
    ('bms.plausibility.lift-status.min', '0', 'DECIMAL', 'Normalised lift codes start at 0.'),
    ('bms.plausibility.lift-status.max', '9', 'DECIMAL', 'Normalised lift codes end at 9.'),
    ('bms.plausibility.generator-run-state.min', '-1', 'DECIMAL', 'Generator run state is -1, 0 or 1.'),
    ('bms.plausibility.generator-run-state.max', '1', 'DECIMAL', 'Generator run state is -1, 0 or 1.'),
    ('bms.plausibility.fuel-tank-level-pct.min', '0', 'DECIMAL', 'Plausible minimum tank level.'),
    ('bms.plausibility.fuel-tank-level-pct.max', '100', 'DECIMAL', 'Plausible maximum tank level.')
) AS seed(config_key, config_value, value_type, description)
WHERE NOT EXISTS (
    SELECT 1 FROM facilities.facility_runtime_configuration existing
    WHERE existing.config_key = seed.config_key
      AND existing.site_code IS NULL
      AND existing.effective_to IS NULL
);

SELECT facilities.apply_site_scope_policies();
