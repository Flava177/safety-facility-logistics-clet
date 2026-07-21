package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Vehicle readiness (SRS-SFL-S166-05 "ready, conditionally ready and unavailable vehicles").
 *
 * <p>Always derived from the current blocker set, never stored as an editable flag.
 */
public enum ReadinessStatus {
    READY,
    CONDITIONALLY_READY,
    NOT_READY;

    public boolean permitsAssignment() {
        return this != NOT_READY;
    }
}
