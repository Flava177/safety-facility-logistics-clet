package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Fleet workflow item lifecycle (SRS-SFL-S166-02).
 *
 * <p>{@code ESCALATED} is a status rather than a flag so the queue can be filtered on it and so the
 * transition history records the moment escalation happened.
 */
public enum FleetWorkflowStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    ON_HOLD,
    ESCALATED,
    CLOSED,
    CANCELLED,
    REOPENED;

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /** Whether the SLA clock is still running on this item. */
    public boolean isLive() {
        return this != CLOSED && this != CANCELLED;
    }
}
