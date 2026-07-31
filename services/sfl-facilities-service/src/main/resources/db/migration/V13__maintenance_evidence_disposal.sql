-- SRS-SFL-S153-03: the retention half of closure evidence.
--
-- Retention classes have been recorded and disposalEligibleFrom computed since S153 shipped, and
-- `ix_maintenance_evidence_retention` was added for exactly this query. Nothing ever ran it. The gap
-- report was explicit that a sweep which deletes evidence should not ship in the same round that first
-- defines what the retention classes mean — that round is past, so this is that sweep.
--
-- **Disposal removes the pointer, never the record.** file_reference becomes nullable and is cleared;
-- content_hash, retention_class, uploaded_by and uploaded_at stay, and disposed_at and disposal_reason
-- are added. A retention policy has to be able to prove two different things — that a thing was
-- destroyed when it should have been, and that it existed and was destroyed for a stated reason — and
-- deleting the row proves neither. An auditor asking "what happened to the closure photograph for
-- WO-000123?" gets an answer instead of a silence indistinguishable from it never having existed.
--
-- The object itself is removed by whatever owns the object store; this service holds references and
-- hashes, never bytes (solution.md, "evidence by reference"). Clearing the reference is this service's
-- whole share of the act, and disposed_at records when it did its part.

ALTER TABLE facilities.maintenance_evidence
    ALTER COLUMN file_reference DROP NOT NULL,
    ADD COLUMN disposed_at TIMESTAMPTZ,
    ADD COLUMN disposal_reason VARCHAR(500);

-- Disposed means: no pointer, and a reason on the record. Neither half alone is a disposal, and a row
-- with a reference *and* a disposal date is a bug rather than a state.
ALTER TABLE facilities.maintenance_evidence
    ADD CONSTRAINT ck_maintenance_evidence_disposal CHECK (
        (disposed_at IS NULL AND file_reference IS NOT NULL AND disposal_reason IS NULL)
        OR (disposed_at IS NOT NULL AND file_reference IS NULL AND disposal_reason IS NOT NULL));

-- The sweep's query. Partial, because eligible-and-not-yet-disposed is a small slice of the table and
-- stays small precisely because the sweep runs.
CREATE INDEX ix_maintenance_evidence_disposal_due
    ON facilities.maintenance_evidence (retention_class, uploaded_at)
    WHERE disposed_at IS NULL AND legal_hold = FALSE;
