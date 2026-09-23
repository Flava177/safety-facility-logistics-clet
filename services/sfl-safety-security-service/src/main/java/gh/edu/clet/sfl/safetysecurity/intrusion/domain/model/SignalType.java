package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

/** SRS-SFL-S162-01: the raw signal kinds panels emit. {@link #RESTORATION} closes a matching open
 * {@link IntrusionAlarm} rather than raising a new one - see {@code IntrusionIngestionService}. */
public enum SignalType {
    ZONE_ALARM,
    TAMPER,
    FAULT,
    RESTORATION
}
