package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

/** SRS-SFL-S162-02/03/04: the SOC-queue lifecycle of an {@link IntrusionAlarm}. {@link #COALESCED}
 * means a flapping panel's repeated signals are being absorbed into this one alarm rather than
 * flooding the queue with duplicates - see {@code IntrusionIngestionService}. */
public enum AlarmStatus {
    RAISED,
    ACKNOWLEDGED,
    ESCALATED,
    COALESCED,
    RESOLVED
}
