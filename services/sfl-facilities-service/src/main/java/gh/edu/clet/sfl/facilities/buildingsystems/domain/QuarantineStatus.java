package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/** A quarantined reading's review state. {@code PENDING} is the only non-terminal state. */
public enum QuarantineStatus {
    PENDING,
    /** Mapping fixed and the reading stored as fact. */
    RELEASED,
    /** Judged not to be fact, with a recorded reason. Kept, not deleted. */
    DISCARDED
}
