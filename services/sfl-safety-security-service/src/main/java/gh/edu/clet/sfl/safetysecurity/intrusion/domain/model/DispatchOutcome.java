package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

/** SRS-SFL-S162-05: the outcome recorded once armed response has attended a confirmed alarm.
 * {@link #FALSE_ALARM} is what the false-alarm trend counts. */
public enum DispatchOutcome {
    PENDING,
    CONFIRMED_INTRUSION,
    FALSE_ALARM,
    NO_RESPONSE,
    CANCELLED
}
