-- =====================================================================================
-- Corrective migration: hash and fingerprint columns move from CHAR(64) to VARCHAR(64).
--
-- Two reasons.
--
-- 1. Hibernate's `ddl-auto: validate` maps a String field with `length = 64` to VARCHAR(64)
--    and rejects a CHAR(64) column, so the service could not start against a real PostgreSQL.
--    The end-to-end suite caught this the first time it ran against Postgres rather than an
--    in-memory double.
--
-- 2. CHAR blank-pads its values. Comparing a padded hash against an unpadded one is a latent
--    correctness hazard in exactly the place we can least afford it — the tamper-evident audit
--    chain and the idempotency fingerprint. VARCHAR stores what was written.
--
-- Numbered V9.1 rather than V10 so the documented allocation holds: S166 owns V2 through V9,
-- and S168_fuel and S171 still start at V10 (see docs/fleet/S166_Migration_Plan.md).
-- Applied as a new migration rather than by editing V2 so any database that already ran V2
-- keeps a valid Flyway checksum.
-- =====================================================================================

ALTER TABLE fleet_logistics.fleet_audit_records
    ALTER COLUMN previous_hash TYPE VARCHAR(64),
    ALTER COLUMN record_hash TYPE VARCHAR(64);

ALTER TABLE fleet_logistics.fleet_audit_chain_state
    ALTER COLUMN head_hash TYPE VARCHAR(64);

ALTER TABLE fleet_logistics.fleet_idempotency_keys
    ALTER COLUMN request_fingerprint TYPE VARCHAR(64);

-- The values are fixed-length hex digests; keep that guarantee now the type no longer implies it.
ALTER TABLE fleet_logistics.fleet_audit_records
    ADD CONSTRAINT ck_fleet_audit_hash_length
        CHECK (length(previous_hash) = 64 AND length(record_hash) = 64);

ALTER TABLE fleet_logistics.fleet_audit_chain_state
    ADD CONSTRAINT ck_fleet_audit_chain_head_length CHECK (length(head_hash) = 64);

ALTER TABLE fleet_logistics.fleet_idempotency_keys
    ADD CONSTRAINT ck_fleet_idempotency_fingerprint_length CHECK (length(request_fingerprint) = 64);
