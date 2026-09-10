package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Result of replaying the audit hash chain (SRS-SFL-S166-03 acceptance criterion 3).
 *
 * <p>When {@link #intact()} is {@code false} the caller must raise the critical compliance alert; the
 * divergence detail is deliberately specific so compliance can locate the affected record.
 *
 * <p>{@code complete} distinguishes "replayed to the current head, intact" from "stopped early, within
 * its per-call bound, having found no break so far" - see {@code JpaAuditAdapter.verifyChain}, which
 * caps how much of a long chain one call replays rather than materialising the whole table.
 * {@code resumeFromSequence} names where this call stopped, as a diagnostic; {@code verifyChain()}
 * always restarts from genesis rather than persisting a checkpoint across calls.
 */
public record AuditChainVerification(
        boolean intact,
        boolean complete,
        int recordsChecked,
        Long firstDivergentSequence,
        String expectedValue,
        String actualValue,
        String reason,
        String headHash,
        Long resumeFromSequence) {

    public static AuditChainVerification intact(int recordsChecked, String headHash) {
        return new AuditChainVerification(true, true, recordsChecked, null, null, null, null, headHash, null);
    }

    /** No break found in the records replayed so far, but the chain continues past this call's bound. */
    public static AuditChainVerification incomplete(int recordsChecked, String headHash, long resumeFromSequence) {
        return new AuditChainVerification(true, false, recordsChecked, null, null, null, null, headHash,
                resumeFromSequence);
    }

    static AuditChainVerification broken(long expectedSequence, long actualSequence, String reason,
            int recordsChecked) {
        return new AuditChainVerification(false, true, recordsChecked, expectedSequence,
                Long.toString(expectedSequence), Long.toString(actualSequence), reason, null, null);
    }

    static AuditChainVerification broken(long sequenceNo, String expectedValue, String actualValue, String reason,
            int recordsChecked) {
        return new AuditChainVerification(false, true, recordsChecked, sequenceNo, expectedValue, actualValue,
                reason, null, null);
    }
}
