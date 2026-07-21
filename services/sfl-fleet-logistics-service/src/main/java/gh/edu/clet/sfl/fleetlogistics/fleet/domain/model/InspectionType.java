package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * When an inspection is carried out.
 *
 * <p>{@link #PRE_TRIP} is the one that gates a trip start: SRS-SFL-S166-02 requires progress and
 * evidence to be recorded, and the pre-trip check is the evidence-bearing step that can block.
 */
public enum InspectionType {
    PRE_TRIP,
    POST_TRIP,
    PERIODIC,
    DEFECT_FOLLOW_UP
}
