package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/** The action recorded on an immutable workflow transition (SRS-SFL-S166-02 transition history). */
public enum WorkflowAction {
    CREATED,
    ASSIGNED,
    REASSIGNED,
    STARTED,
    HELD,
    RESUMED,
    ESCALATED,
    CANCELLED,
    CLOSED,
    REOPENED,
    COMMENTED
}
