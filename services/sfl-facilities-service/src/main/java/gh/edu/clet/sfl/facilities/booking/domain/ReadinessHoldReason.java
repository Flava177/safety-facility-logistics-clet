package gh.edu.clet.sfl.facilities.booking.domain;

/**
 * Why a confirmed booking is at risk from the state of its space.
 *
 * <p>A hold does not cancel the booking. It marks it, so a hall blocked on Tuesday surfaces every
 * booking it has for the rest of the week rather than failing silently at the door on Friday — which
 * is the whole reason SRS-SFL-S159-01 lists "readiness hold" as a record of its own.
 */
public enum ReadinessHoldReason {
    /** The space is BLOCKED: a critical readiness blocker is open on it. */
    SPACE_BLOCKED,
    /** The space is DEGRADED and this booking is an examination, which needs READY. */
    NOT_EXAMINATION_READY,
    /** The space has been locked for examination use and this booking is not one. */
    LOCKED_FOR_EXAMINATION,
    /** The space left ACTIVE lifecycle — suspended, archived. */
    SPACE_WITHDRAWN
}
