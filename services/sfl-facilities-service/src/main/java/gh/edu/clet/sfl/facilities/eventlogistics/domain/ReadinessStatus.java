package gh.edu.clet.sfl.facilities.eventlogistics.domain;

/**
 * One event's consolidated readiness - SRS-SFL-S173-02 ("rolls up to one consolidated event-readiness
 * view") and the S173 dashboard's "upcoming events by readiness status".
 *
 * <p>Ordered worst first, so the dashboard can sort on it.
 */
public enum ReadinessStatus {
    /** Nothing has been requested yet - the "unresourced tasks" the dashboard names. */
    UNRESOURCED,
    /** At least one owning system refused; somebody must choose another room or time. */
    CONFLICTED,
    /** Requests are outstanding - awaiting an answer, or manual items nobody has accepted. */
    AWAITING,
    /** Every live request is confirmed, fulfilled or an accepted manual coordination item. */
    READY,
    COMPLETED,
    CANCELLED
}
