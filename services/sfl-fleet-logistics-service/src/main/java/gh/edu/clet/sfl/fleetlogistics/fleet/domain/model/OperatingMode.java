package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Operating mode of a trip or workflow item. SRS-SFL-S166-02 names operating mode as an input to
 * SLA rules, and SRS-SFL-S166-05 as a dashboard filter.
 *
 * <p>{@link #EMERGENCY} is the in-scope expression of emergency logistics for S166; a dedicated
 * emergency-logistics endpoint is deliberately not implemented (gap report C-12).
 */
public enum OperatingMode {
    /** Business-as-usual campus and inter-site movement. */
    ROUTINE,
    /** Examination-period logistics with tighter SLA targets. */
    EXAMINATION,
    /** Life-safety or incident response movement; highest SLA priority. */
    EMERGENCY,
    /** Maintenance movement (workshop transfer, road test). */
    MAINTENANCE
}
