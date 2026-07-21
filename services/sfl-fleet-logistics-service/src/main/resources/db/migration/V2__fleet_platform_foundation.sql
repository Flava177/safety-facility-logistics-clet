-- =====================================================================================
-- S166 Fleet and Vehicle Management — cross-cutting platform foundation.
--
-- SRS-SFL-S166-02 runtime SLA/threshold configuration and notification intents,
-- SRS-SFL-S166-03 append-only hash-chained audit log,
-- SRS-SFL-S166-04 outbox delivery state (retry/backoff/dead-letter),
-- plus the idempotency store used by state-creating commands.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Append-only guard. Attached to every table the SRS requires to be immutable.
-- "Audit records cannot be modified or deleted by normal application roles."
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fleet_logistics.fleet_append_only_guard()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table %.% is append-only; % is not permitted',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S166-03 — audit trail.
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.fleet_audit_records (
    id UUID PRIMARY KEY,
    sequence_no BIGINT NOT NULL UNIQUE,
    site_scope VARCHAR(40) NOT NULL,
    actor_id VARCHAR(160) NOT NULL,
    actor_display_name VARCHAR(200) NOT NULL,
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(160) NOT NULL,
    before_value JSONB,
    after_value JSONB,
    correlation_id VARCHAR(120),
    source_channel VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    previous_hash CHAR(64) NOT NULL,
    record_hash CHAR(64) NOT NULL
);

CREATE INDEX ix_fleet_audit_site_occurred
    ON fleet_logistics.fleet_audit_records (site_scope, occurred_at DESC);
CREATE INDEX ix_fleet_audit_resource
    ON fleet_logistics.fleet_audit_records (resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_fleet_audit_actor
    ON fleet_logistics.fleet_audit_records (actor_id, occurred_at DESC);
CREATE INDEX ix_fleet_audit_action
    ON fleet_logistics.fleet_audit_records (action, occurred_at DESC);

CREATE TRIGGER trg_fleet_audit_records_append_only
    BEFORE UPDATE OR DELETE ON fleet_logistics.fleet_audit_records
    FOR EACH ROW EXECUTE FUNCTION fleet_logistics.fleet_append_only_guard();

-- Single-row chain head. Writers take a row lock here so concurrent appends cannot
-- interleave and break the hash chain.
CREATE TABLE fleet_logistics.fleet_audit_chain_state (
    id SMALLINT PRIMARY KEY,
    head_hash CHAR(64) NOT NULL,
    next_sequence BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fleet_audit_chain_singleton CHECK (id = 1),
    CONSTRAINT ck_fleet_audit_chain_sequence CHECK (next_sequence >= 0)
);

INSERT INTO fleet_logistics.fleet_audit_chain_state (id, head_hash, next_sequence)
VALUES (1, repeat('0', 64), 0)
ON CONFLICT (id) DO NOTHING;

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S166-02 — config-without-code. Read at evaluation time, never cached across runs.
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.fleet_runtime_configuration (
    id UUID PRIMARY KEY,
    config_key VARCHAR(120) NOT NULL,
    site_code VARCHAR(40),
    config_value TEXT NOT NULL,
    value_type VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(160) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_fleet_runtime_config_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- One active value per key per scope. site_code IS NULL means "platform default".
CREATE UNIQUE INDEX ux_fleet_runtime_config_site
    ON fleet_logistics.fleet_runtime_configuration (config_key, site_code)
    WHERE effective_to IS NULL AND site_code IS NOT NULL;
CREATE UNIQUE INDEX ux_fleet_runtime_config_default
    ON fleet_logistics.fleet_runtime_configuration (config_key)
    WHERE effective_to IS NULL AND site_code IS NULL;

INSERT INTO fleet_logistics.fleet_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, updated_by, updated_at)
VALUES
    (gen_random_uuid(), 'fleet.compliance.expiry-warning-window', NULL, 'P30D', 'DURATION',
     'How long before expiry a compliance document is reported as expiring.', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.inspection.validity-window', NULL, 'P1D', 'DURATION',
     'How long a passed inspection remains valid before a new one is required.', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.service.due-warning-window', NULL, 'P14D', 'DURATION',
     'How far ahead of the due date a service is reported as due.', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.odometer.staleness-threshold', NULL, 'P30D', 'DURATION',
     'Age at which the last odometer reading is treated as stale provenance.', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.telematics.staleness-threshold', NULL, 'PT6H', 'DURATION',
     'Age at which telematics data is reported as stale.', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.dashboard.freshness-threshold', NULL, 'PT15M', 'DURATION',
     'Snapshot age at which the dashboard must display a stale-data warning.', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.integration.signature-window', NULL, 'PT5M', 'DURATION',
     'Maximum accepted age of an inbound signed message (HMAC replay guard).', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.outbound.max-attempts', NULL, '8', 'INTEGER',
     'Delivery attempts before an outbound message is dead-lettered.', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.outbound.retry-base-seconds', NULL, '10', 'INTEGER',
     'Base seconds for the exponential outbound retry backoff.', now(), 'system', now()),
    (gen_random_uuid(), 'fleet.outbound.retry-max-seconds', NULL, '3600', 'INTEGER',
     'Upper bound on the exponential outbound retry backoff.', now(), 'system', now());

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S166-02 — notification intents.
-- The Phase 1 adapter records the intent rather than reporting a delivery it did not make.
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.fleet_notification_intents (
    id UUID PRIMARY KEY,
    site_code VARCHAR(40) NOT NULL,
    recipient_type VARCHAR(20) NOT NULL,
    recipient VARCHAR(160) NOT NULL,
    notification_kind VARCHAR(60) NOT NULL,
    subject_reference VARCHAR(160) NOT NULL,
    context JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    failure_reason VARCHAR(1000),
    CONSTRAINT ck_fleet_notification_recipient_type CHECK (recipient_type IN ('USER', 'ROLE'))
);

CREATE INDEX ix_fleet_notification_status_created
    ON fleet_logistics.fleet_notification_intents (status, created_at);
CREATE INDEX ix_fleet_notification_subject
    ON fleet_logistics.fleet_notification_intents (subject_reference, created_at DESC);

-- -------------------------------------------------------------------------------------
-- Idempotency store for retried state-creating commands (Idempotency-Key header).
-- -------------------------------------------------------------------------------------
CREATE TABLE fleet_logistics.fleet_idempotency_keys (
    id UUID PRIMARY KEY,
    operation VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    result_id UUID NOT NULL,
    site_code VARCHAR(40),
    actor_id VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_fleet_idempotency_operation_key
    ON fleet_logistics.fleet_idempotency_keys (operation, idempotency_key);
CREATE INDEX ix_fleet_idempotency_created
    ON fleet_logistics.fleet_idempotency_keys (created_at);

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S166-04 — outbox delivery state for retry, backoff and dead-lettering.
-- -------------------------------------------------------------------------------------
ALTER TABLE fleet_logistics.outbox_messages
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ,
    ADD COLUMN actor_id VARCHAR(160),
    ADD COLUMN trace_parent VARCHAR(120),
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX ix_fleet_outbox_claimable
    ON fleet_logistics.outbox_messages (next_attempt_at)
    WHERE status = 'PENDING';
