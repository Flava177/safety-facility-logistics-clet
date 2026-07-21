package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * How a record reached the system. Part of the SRS-SFL-S166-01 system-managed field set and of the
 * SRS-SFL-S166-03 audit record.
 */
public enum SourceChannel {
    /** Operator using the fleet console or another authenticated web client. */
    WEB,
    /** Mobile browser capture (inspections, trip closure at the vehicle). */
    MOBILE,
    /** Another SFL service or an enterprise system calling the API with a service principal. */
    API,
    /** An authenticated vendor webhook or device callback processed through the integration inbox. */
    INTEGRATION,
    /** A scheduled job inside this service (SLA evaluation, expiry sweeps, projections). */
    SCHEDULER,
    /** Data migration or bulk load. */
    MIGRATION
}
