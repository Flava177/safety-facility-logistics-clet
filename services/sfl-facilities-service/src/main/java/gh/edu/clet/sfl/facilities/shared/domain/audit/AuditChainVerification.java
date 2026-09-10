package gh.edu.clet.sfl.facilities.shared.domain.audit;

/**
 * The outcome of replaying a segment of the audit chain (SRS-SFL-S152-03).
 *
 * <p>A broken result names the record it broke at and what was expected against what was found, so
 * the "Audit Chain Failure - escalate to compliance and security" alert carries something an
 * investigator can act on rather than a bare boolean.
 *
 * <p>{@code complete} distinguishes "the whole chain, to its current head, was replayed and is intact"
 * from "verification stopped early, within its bound, having found no break so far". The latter is not
 * a weaker guarantee for the records it covers - every one of {@code recordsVerified} is genuinely
 * verified - it is an honest statement that {@code JpaAuditAdapter.verifyChain} caps how much of a very
 * long chain one call replays, rather than holding a read transaction open for however long the full
 * table takes. {@code resumeFromSequence} names the first sequence number this call did not reach, as a
 * diagnostic for an operator or a follow-up offline job - {@code verifyChain()} itself always starts
 * from genesis and does not persist a checkpoint between calls, so a chain that permanently exceeds the
 * configured bound needs either a larger bound for an administrative full pass or a dedicated batch job
 * built on the same page-at-a-time query, not repeated calls to this method.
 */
public record AuditChainVerification(
        boolean intact,
        boolean complete,
        long recordsVerified,
        Long brokenAtSequence,
        String expected,
        String found,
        String reason,
        String headHash,
        Long resumeFromSequence) {

    public static AuditChainVerification intact(long recordsVerified, String headHash) {
        return new AuditChainVerification(true, true, recordsVerified, null, null, null, null, headHash, null);
    }

    /**
     * The chain has no break in the {@code recordsVerified} replayed so far, but there is more of it
     * beyond the per-call bound. Not a failure: a caller (or an offline job) resumes from
     * {@code resumeFromSequence} to keep verifying the rest.
     */
    public static AuditChainVerification incomplete(long recordsVerified, String headHash,
            long resumeFromSequence) {
        return new AuditChainVerification(true, false, recordsVerified, null, null, null, null, headHash,
                resumeFromSequence);
    }

    public static AuditChainVerification broken(long atSequence, String expected, String found, String reason,
            long recordsVerified) {
        return new AuditChainVerification(false, true, recordsVerified, atSequence, expected, found, reason, null,
                null);
    }

    /** A sequence-contiguity break, where the mismatch is between numbers rather than hashes. */
    public static AuditChainVerification brokenSequence(long expectedSequence, long foundSequence, String reason,
            long recordsVerified) {
        return new AuditChainVerification(false, true, recordsVerified, foundSequence,
                Long.toString(expectedSequence), Long.toString(foundSequence), reason, null, null);
    }
}
