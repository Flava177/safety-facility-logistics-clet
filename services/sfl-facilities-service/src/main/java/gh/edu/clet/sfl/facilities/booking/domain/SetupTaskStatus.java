package gh.edu.clet.sfl.facilities.booking.domain;

/** Whether the room has been made ready. Three states, because "nobody got to it" is not "done". */
public enum SetupTaskStatus {
    PENDING,
    DONE,
    /** Deliberately not done, with a reason. Distinguishes a decision from a lapse. */
    SKIPPED
}
