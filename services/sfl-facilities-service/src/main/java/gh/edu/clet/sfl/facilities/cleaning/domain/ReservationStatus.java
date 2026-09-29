package gh.edu.clet.sfl.facilities.cleaning.domain;

/**
 * What is stored for an S173 capacity request. {@code FULFILLED} is deliberately not here: it is
 * derived from the reserved task having completed, so it cannot fall out of step with the task.
 */
public enum ReservationStatus {
    RESERVED,
    CONFLICT,
    RELEASED
}
