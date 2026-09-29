package gh.edu.clet.sfl.facilities.eventlogistics.domain;

/**
 * The system that owns fulfilment of a resource request - SRS-SFL-S173-02.
 *
 * <p>S173 never fulfils anything itself. It asks the system that holds the capacity and tracks the
 * answer, which is why every {@link EventResourceType} names one of these rather than carrying a
 * workflow of its own. Whether a given owning system is actually <em>available</em> in a deployment is
 * runtime configuration read by {@code EventOwningSystemRegistry}, not a property of this enum: S172
 * is Phase 3 today and flips by configuration, not by a code change, on the day it exists.
 */
public enum OwningSystem {

    /** Room &amp; Resource Booking - venue, AV, staging, security and signage as bookable resources. */
    S159("Room & Resource Booking"),
    /** CMMS - maintenance that must be done before the event. */
    S153("CMMS"),
    /** Cleaning &amp; Janitorial Schedule Management - event cleaning capacity. */
    S169("Cleaning & Janitorial Schedule Management"),
    /** Catering &amp; Cafeteria Management - Phase 3, not built (SRS 3.6). */
    S172("Catering & Cafeteria Management");

    private final String displayName;

    OwningSystem(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
