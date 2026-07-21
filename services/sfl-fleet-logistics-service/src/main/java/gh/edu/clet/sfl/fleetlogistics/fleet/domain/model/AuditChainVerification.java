package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Result of replaying the audit hash chain (SRS-SFL-S166-03 acceptance criterion 3).
 *
 * <p>When {@link #intact()} is {@code false} the caller must raise the critical compliance alert; the
 * divergence detail is deliberately specific so compliance can locate the affected record.
 */
public record AuditChainVerification(
        boolean intact,
        int recordsChecked,
        Long firstDivergentSequence,
        String expectedValue,
        String actualValue,
        String reason,
        String headHash) {

    public static AuditChainVerification intact(int recordsChecked, String headHash) {
        return new AuditChainVerification(true, recordsChecked, null, null, null, null, headHash);
    }

    static AuditChainVerification broken(long expectedSequence, long actualSequence, String reason,
            int recordsChecked) {
        return new AuditChainVerification(false, recordsChecked, expectedSequence,
                Long.toString(expectedSequence), Long.toString(actualSequence), reason, null);
    }

    static AuditChainVerification broken(long sequenceNo, String expectedValue, String actualValue, String reason,
            int recordsChecked) {
        return new AuditChainVerification(false, recordsChecked, sequenceNo, expectedValue, actualValue, reason, null);
    }
}
