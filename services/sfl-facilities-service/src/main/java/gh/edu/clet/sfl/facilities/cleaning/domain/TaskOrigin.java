package gh.edu.clet.sfl.facilities.cleaning.domain;

/**
 * What raised a cleaning task. Stored, because every S169 rule asks it: the SLA response clock runs
 * only on a reactive request, the booking back-reference is mandatory only on a booking-origin task,
 * and the dashboard splits scheduled from reactive work.
 */
public enum TaskOrigin {

    /** Materialised by the generation sweep from a routine schedule. */
    ROUTINE,
    /** Raised from an S159 booking that asked for cleaning before use. */
    BOOKING_SETUP,
    /** Raised from an S159 booking that asked for cleaning after use. */
    BOOKING_TEARDOWN,
    /** Raised directly by an occupant - the unscheduled request of SRS-SFL-S169-01. */
    REACTIVE,
    /** Reserved by S173 event logistics against this system's capacity - SRS-SFL-S169-04. */
    EVENT,
    /** Raised by a supervisor for a one-off clean that no schedule covers. */
    ADHOC;

    /** {@code true} for the two origins SRS-SFL-S169-01 requires to carry an S159 back-reference. */
    public boolean claimsBooking() {
        return this == BOOKING_SETUP || this == BOOKING_TEARDOWN;
    }
}
