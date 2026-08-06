-- =====================================================================================
-- SRS-SFL-S166-03 - Evidence file store
--
-- Until this migration the platform held evidence *metadata* only: a storage reference, a file name
-- and a SHA-256, with the bytes living in a document store that Release 1 does not have. The
-- reference scheme said so honestly - every row carried a `local-demo://` key that resolved nowhere.
--
-- That was defensible while nothing needed to read a file back. It stopped being defensible the
-- moment three workflows needed exactly that: an uploaded compliance certificate, a fuel receipt and
-- a photograph of a pump meter are all worthless if nobody can open them, and the fuel controls in
-- particular depend on a reviewer being able to look at the receipt beside the amount claimed.
--
-- The bytes therefore live here, in the owning service's own schema, behind an application port so a
-- real object store can take over without any caller changing. That is a deliberate trade:
--
--   * BYTEA, not large objects. `lo_*` needs its own handles, its own vacuum and a transaction open
--     for the whole read; BYTEA is one column that TOASTs itself and travels with the row's backup.
--   * A cap of 10 MB, enforced in the scanner before anything reaches this table. An uncapped upload
--     is an uncapped row, and a database is a bad place to discover that.
--   * A separate table from `fleet_evidence_references`, not a column on it. Every existing read of
--     that table selects metadata, and widening the row would drag megabytes through queries that
--     want a file name. This way a metadata read never touches a byte of content.
--
-- The row is deleted with its reference, because content outliving its metadata is unreachable and
-- unauditable - the retention rules are expressed against the reference.
-- =====================================================================================

CREATE TABLE fleet_logistics.fleet_evidence_files (
    evidence_id UUID PRIMARY KEY
        REFERENCES fleet_logistics.fleet_evidence_references (id) ON DELETE CASCADE,
    content BYTEA NOT NULL,
    byte_size BIGINT NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    -- The verdict the scanner reached, kept so an audit can ask what was checked rather than trusting
    -- that something was. A file is only ever inserted after it passes, so ACCEPTED is the only value
    -- written today; the column exists because a future ICAP or ClamAV sidecar will want QUARANTINED
    -- and re-scanning on retention review will want RESCAN_FAILED, and adding it now costs nothing.
    scan_status VARCHAR(30) NOT NULL,
    scan_detail VARCHAR(500),
    scanned_at TIMESTAMPTZ NOT NULL,
    uploaded_by VARCHAR(160) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_fleet_evidence_file_size CHECK (byte_size > 0 AND byte_size <= 10485760),
    CONSTRAINT ck_fleet_evidence_file_type CHECK (content_type IN ('application/pdf', 'image/jpeg')),
    CONSTRAINT ck_fleet_evidence_file_scan CHECK (scan_status <> '')
);

COMMENT ON TABLE fleet_logistics.fleet_evidence_files IS
    'Scanned evidence bytes. PDF and JPEG only, enforced by magic-byte inspection before insert.';

-- Finding a file by its digest is what makes "this receipt has been submitted before" answerable, and
-- that question is the cheapest fuel-fraud control the platform has. The digest lives on the metadata
-- table, so the index goes there.
CREATE INDEX ix_fleet_evidence_hash_lookup
    ON fleet_logistics.fleet_evidence_references (site_code, sha256_hash);
