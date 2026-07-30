package gh.edu.clet.sfl.facilities.shared.domain.audit;

/**
 * The outcome of replaying a segment of the audit chain (SRS-SFL-S152-03).
 *
 * <p>A broken result names the record it broke at and what was expected against what was found, so
 * the "Audit Chain Failure — escalate to compliance and security" alert carries something an
 * investigator can act on rather than a bare boolean.
 */
public record AuditChainVerification(
        boolean intact,
        long recordsVerified,
        Long brokenAtSequence,
        String expected,
        String found,
        String reason,
        String headHash) {

    public static AuditChainVerification intact(long recordsVerified, String headHash) {
        return new AuditChainVerification(true, recordsVerified, null, null, null, null, headHash);
    }

    public static AuditChainVerification broken(long atSequence, String expected, String found, String reason,
            long recordsVerified) {
        return new AuditChainVerification(false, recordsVerified, atSequence, expected, found, reason, null);
    }

    /** A sequence-contiguity break, where the mismatch is between numbers rather than hashes. */
    public static AuditChainVerification brokenSequence(long expectedSequence, long foundSequence, String reason,
            long recordsVerified) {
        return new AuditChainVerification(false, recordsVerified, foundSequence,
                Long.toString(expectedSequence), Long.toString(foundSequence), reason, null);
    }
}
