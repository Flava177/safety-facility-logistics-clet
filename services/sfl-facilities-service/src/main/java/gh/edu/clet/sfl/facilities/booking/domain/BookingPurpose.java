package gh.edu.clet.sfl.facilities.booking.domain;

/**
 * What a space is being booked for.
 *
 * <p>Not decoration: {@link #EXAMINATION} is the one that changes the rules. An examination booking
 * is tested against {@code availableForExamination} rather than {@code availableForBooking}, which is
 * the stricter of the two — S152 requires readiness {@code READY} outright for an examination, where
 * an ordinary booking tolerates {@code DEGRADED}.
 */
public enum BookingPurpose {

    /** Teaching. The ordinary case. */
    LECTURE,
    /** A moot, a hearing or a rehearsal of one. */
    MOOT,
    /** An examination. Held to the stricter readiness test, and to examination mode. */
    EXAMINATION,
    MEETING,
    EVENT,
    /** Held deliberately empty — maintenance access, a survey, a decant. */
    RESERVED,
    OTHER;

    /** {@code true} when this booking must be held to the examination readiness standard. */
    public boolean requiresExaminationReadiness() {
        return this == EXAMINATION;
    }
}
