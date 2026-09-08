-- AuditAdapter previously derived sequence_no as SELECT MAX(sequence_no)+1 then inserted, with no
-- lock and no atomicity between the read and the insert. Under concurrent audit writes from
-- different subdomains sharing this one table (e.g. visitor check-in racing incident triage), two
-- transactions could compute the same "next" value; the UNIQUE(sequence_no) constraint below then
-- aborted whichever transaction inserted second, taking an otherwise-valid business transaction down
-- with it. A real sequence hands out a distinct value to each caller atomically without a shared
-- read-then-write window, so concurrent callers never collide.
--
-- audit_log has no seed data (see V10), but this migration must still be safe to run against an
-- environment that already has rows (any deployment that has been live and auditing for a while, or
-- a persistent local/CI database that earlier test runs have written to) - so the sequence is primed
-- from the current max rather than assumed to start at 1. OWNED BY ties its lifecycle to the column
-- it backs.
CREATE SEQUENCE safety_security.audit_log_sequence_no_seq
    OWNED BY safety_security.audit_log.sequence_no;

-- setval rejects 0 - a sequence's MINVALUE is 1, so "no rows yet" cannot be primed the same way "some
-- rows already" is. A brand-new database (every CI run; a freshly created local/dev database) hits
-- this exact branch, and CREATE SEQUENCE has already left the sequence at the correct starting point
-- for that case, so there is nothing to do.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM safety_security.audit_log) THEN
        PERFORM setval('safety_security.audit_log_sequence_no_seq',
            (SELECT MAX(sequence_no) FROM safety_security.audit_log), true);
    END IF;
END $$;
