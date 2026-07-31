package gh.edu.clet.sfl.facilities.maintenance.domain;

/**
 * How urgent a fault or work order is, and therefore what SLA it earns.
 *
 * <p>The ordering is meaningful and relied upon. SRS-SFL-S153-02 makes priority an input to the SLA
 * calculation, and two configurable thresholds — the priority at which closure evidence becomes
 * mandatory, and the priority at which a fault raises a readiness blocker — are both expressed as
 * "at least this". {@link #atLeast} is that comparison, in one place, so the two rules cannot drift
 * into disagreeing about what "high or above" means.
 */
public enum FaultPriority {

    /** Cosmetic or deferrable. A scuffed wall, a noticeboard light. */
    LOW,
    /** Ordinary maintenance. Reduces comfort or convenience, not use. */
    MEDIUM,
    /** Impairs use of the space and needs attention within the working day. */
    HIGH,
    /** Stops the space being used at all. Fire egress, power, water ingress. */
    CRITICAL;

    /** {@code true} when this priority is at or above {@code threshold}. */
    public boolean atLeast(FaultPriority threshold) {
        return threshold != null && ordinal() >= threshold.ordinal();
    }
}
