-- =====================================================================================
-- S152 CAFM / IWMS — cross-cutting platform foundation.
--
-- This is the half of S152 that exists so its sub-systems do not each rebuild it.
-- S153 maintenance and S159 room booking inherit the audit chain, the runtime
-- configuration store and the idempotency store declared here.
--
--   SRS-SFL-S152-02  runtime SLA/threshold configuration, evaluated at read time
--   SRS-SFL-S152-03  append-only hash-chained audit log
--   SRS-SFL-S152-04  idempotency keys on state-creating commands, outbox delivery state
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Append-only guard. Attached to every table the SRS requires to be immutable:
-- "Audit records cannot be modified or deleted by normal application roles."
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION facilities.facility_append_only_guard()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table %.% is append-only; % is not permitted',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-03 — audit trail.
-- -------------------------------------------------------------------------------------
-- Hashes and fingerprints are VARCHAR(64), never CHAR(64). Two reasons, both learned the hard
-- way on S166 (see fleet V9_1, a corrective migration this one exists to avoid needing):
--
--   1. Hibernate maps a String field with length = 64 to VARCHAR(64) and rejects CHAR(64) under
--      ddl-auto: validate, so the service will not start against a real PostgreSQL.
--   2. CHAR blank-pads. Comparing a padded hash against an unpadded one is a latent correctness
--      hazard in the two places we can least afford it — the tamper-evident audit chain and the
--      idempotency fingerprint. VARCHAR stores what was written.
--
-- The fixed length is kept as an explicit CHECK, since the type no longer implies it.
CREATE TABLE facilities.facility_audit_records (
    id UUID PRIMARY KEY,
    sequence_no BIGINT NOT NULL UNIQUE,
    site_scope VARCHAR(40) NOT NULL,
    actor_id VARCHAR(160) NOT NULL,
    actor_display_name VARCHAR(200) NOT NULL,
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(160) NOT NULL,
    -- TEXT, deliberately, not JSONB. jsonb normalises what it stores: it reorders object keys and
    -- strips insignificant whitespace, so the value read back is not the value that was written. The
    -- record hash is computed over these fields, so a normalising column type makes every record
    -- replay as tampered. Audit evidence has to round-trip byte for byte, and that costs the ability
    -- to query inside the payload — a trade this requirement decides for us (SRS-SFL-S152-03).
    before_value TEXT,
    after_value TEXT,
    correlation_id VARCHAR(120),
    source_channel VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    record_hash VARCHAR(64) NOT NULL,
    CONSTRAINT ck_facility_audit_hash_length
        CHECK (length(previous_hash) = 64 AND length(record_hash) = 64)
);

CREATE INDEX ix_facility_audit_site_occurred
    ON facilities.facility_audit_records (site_scope, occurred_at DESC);
CREATE INDEX ix_facility_audit_resource
    ON facilities.facility_audit_records (resource_type, resource_id, sequence_no);
CREATE INDEX ix_facility_audit_actor
    ON facilities.facility_audit_records (actor_id, occurred_at DESC);
CREATE INDEX ix_facility_audit_action
    ON facilities.facility_audit_records (action, occurred_at DESC);

CREATE TRIGGER trg_facility_audit_records_append_only
    BEFORE UPDATE OR DELETE ON facilities.facility_audit_records
    FOR EACH ROW EXECUTE FUNCTION facilities.facility_append_only_guard();

-- Single-row chain head. Writers take a row lock here so concurrent appends cannot
-- interleave and break the hash chain.
CREATE TABLE facilities.facility_audit_chain_state (
    id SMALLINT PRIMARY KEY,
    head_hash VARCHAR(64) NOT NULL,
    next_sequence BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_facility_audit_chain_singleton CHECK (id = 1),
    CONSTRAINT ck_facility_audit_chain_sequence CHECK (next_sequence >= 0),
    CONSTRAINT ck_facility_audit_chain_head_length CHECK (length(head_hash) = 64)
);

INSERT INTO facilities.facility_audit_chain_state (id, head_hash, next_sequence)
VALUES (1, repeat('0', 64), 0)
ON CONFLICT (id) DO NOTHING;

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-02 / NFR 23.8 — config-without-code.
-- Read at evaluation time, never cached across a run. Superseding writes effective_to
-- on the old row rather than overwriting it, so a past escalation can be reconciled
-- against the threshold that was actually active when it fired.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_runtime_configuration (
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
    CONSTRAINT ck_facility_runtime_config_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- One active value per key per scope. site_code IS NULL means "platform default".
CREATE UNIQUE INDEX ux_facility_runtime_config_site
    ON facilities.facility_runtime_configuration (config_key, site_code)
    WHERE effective_to IS NULL AND site_code IS NOT NULL;
CREATE UNIQUE INDEX ux_facility_runtime_config_default
    ON facilities.facility_runtime_configuration (config_key)
    WHERE effective_to IS NULL AND site_code IS NULL;
CREATE INDEX ix_facility_runtime_config_active
    ON facilities.facility_runtime_configuration (config_key)
    WHERE effective_to IS NULL;

INSERT INTO facilities.facility_runtime_configuration
    (id, config_key, site_code, config_value, value_type, description, effective_from, updated_by, updated_at)
VALUES
    (gen_random_uuid(), 'facilities.readiness.staleness-threshold', NULL, 'P7D', 'DURATION',
     'Age at which a space readiness assessment is reported as stale.', now(), 'system', now()),
    (gen_random_uuid(), 'facilities.readiness.examination-staleness-threshold', NULL, 'PT24H', 'DURATION',
     'Age at which a readiness assessment is stale for a site in examination mode.', now(), 'system', now()),
    (gen_random_uuid(), 'facilities.dashboard.freshness-threshold', NULL, 'PT15M', 'DURATION',
     'Snapshot age at which the dashboard must display a stale-data warning.', now(), 'system', now()),
    (gen_random_uuid(), 'facilities.dashboard.default-page-size', NULL, '50', 'INTEGER',
     'Rows returned by a dashboard drilldown when the caller does not specify.', now(), 'system', now()),
    (gen_random_uuid(), 'facilities.blocker.critical-escalation-window', NULL, 'PT4H', 'DURATION',
     'How long an open critical readiness blocker may age before it is reported as escalated.',
     now(), 'system', now()),
    (gen_random_uuid(), 'facilities.asset.service-due-warning-window', NULL, 'P14D', 'DURATION',
     'How far ahead of its due date a facility asset service is reported as due.', now(), 'system', now()),
    (gen_random_uuid(), 'facilities.asset.warranty-warning-window', NULL, 'P30D', 'DURATION',
     'How long before expiry an asset warranty is reported as expiring.', now(), 'system', now()),
    (gen_random_uuid(), 'facilities.device.staleness-threshold', NULL, 'PT6H', 'DURATION',
     'Age at which a device reference status reported by a vendor feed is treated as stale.',
     now(), 'system', now()),
    (gen_random_uuid(), 'facilities.outbound.max-attempts', NULL, '8', 'INTEGER',
     'Delivery attempts before an outbound message is dead-lettered.', now(), 'system', now()),
    (gen_random_uuid(), 'facilities.outbound.retry-base-seconds', NULL, '10', 'INTEGER',
     'Base seconds for the exponential outbound retry backoff.', now(), 'system', now()),
    (gen_random_uuid(), 'facilities.outbound.retry-max-seconds', NULL, '3600', 'INTEGER',
     'Upper bound on the exponential outbound retry backoff.', now(), 'system', now());

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-04 — idempotency store for retried state-creating commands.
-- The fingerprint is what makes replay safe: same key + same payload returns the original
-- result, same key + different payload is a client error rather than a silent overwrite.
-- -------------------------------------------------------------------------------------
CREATE TABLE facilities.facility_idempotency_keys (
    id UUID PRIMARY KEY,
    operation VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_id UUID NOT NULL,
    site_code VARCHAR(40),
    actor_id VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_facility_idempotency_fingerprint_length CHECK (length(request_fingerprint) = 64)
);

CREATE UNIQUE INDEX ux_facility_idempotency_operation_key
    ON facilities.facility_idempotency_keys (operation, idempotency_key);
CREATE INDEX ix_facility_idempotency_created
    ON facilities.facility_idempotency_keys (created_at);

-- -------------------------------------------------------------------------------------
-- SRS-SFL-S152-04 — outbox delivery state for retry, backoff and dead-lettering.
-- The existing outbox is extended rather than replaced: it already carries the events
-- written by masterdata and maintenance, and a second outbox would split the trail.
-- -------------------------------------------------------------------------------------
ALTER TABLE facilities.outbox_messages
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ,
    ADD COLUMN actor_id VARCHAR(160),
    ADD COLUMN trace_parent VARCHAR(120),
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX ix_facility_outbox_claimable
    ON facilities.outbox_messages (next_attempt_at)
    WHERE status = 'PENDING';
