package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/** Processing state for an inbound integration message (SRS-SFL-S166-04). */
public enum IntegrationMessageStatus {
    ACCEPTED,
    PROCESSED,
    REJECTED,
    DEAD_LETTER
}
