package gh.edu.clet.sfl.facilities.shared.domain.audit;

/**
 * How a state change reached the service.
 *
 * <p>{@code SRS-SFL-S152-03} requires the source channel on every audit record: "capture actor,
 * timestamp, before/after values, source channel and correlation ID for all state-changing actions".
 * It is what distinguishes an officer editing a room from a vendor feed moving an asset, and the two
 * are investigated differently.
 */
public enum SourceChannel {
    /** The operations dashboard or any browser client. */
    WEB,
    /** A field device or mobile browser capturing readiness or evidence on site. */
    MOBILE,
    /** An inbound integration message from an enterprise or vendor system. */
    INTEGRATION,
    /** A scheduled job inside this service — snapshot generation, expiry sweeps. */
    SCHEDULER,
    /** A migration, seed or operator script. */
    SYSTEM
}
