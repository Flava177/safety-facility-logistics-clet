package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * A resource request's state - SRS-SFL-S173-02 ("requested, confirmed, conflicted, fulfilled") plus the
 * manual-coordination state its validation rule demands and a cancelled state for requests the event
 * no longer needs.
 *
 * <h2>Why MANUAL_COORDINATION can never become FULFILLED</h2>
 *
 * <p>S173-02's acceptance criterion is that catering requested before S172 exists "is tracked as a
 * manual coordination item rather than appearing as a fulfilled system request". A transition from
 * manual coordination to fulfilled would be exactly that appearance, so it is not in the table below.
 * What a manual item can gain is an <em>acceptance</em> by a named person (see
 * {@link EventResourceRequest#acceptManualCoordination}), which is a different fact: somebody took
 * responsibility off-system, not a system confirming delivery. Post-event reconciliation records what
 * actually arrived without touching this status.
 */
public enum ResourceRequestStatus {

    /** Raised, and either routed and awaiting the owning system's answer, or waiting to be routable. */
    REQUESTED,
    /** The owning system has committed the capacity. */
    CONFIRMED,
    /** The owning system refused; the competing commitment is carried on the request. */
    CONFLICTED,
    /** The owning system reports the work done. */
    FULFILLED,
    /** The owning system is not built or not available; coordinated off-system and clearly marked. */
    MANUAL_COORDINATION,
    /** No longer needed - the request was withdrawn or the event was cancelled. */
    CANCELLED;

    private static final java.util.Map<ResourceRequestStatus, Set<ResourceRequestStatus>> ALLOWED =
            new java.util.EnumMap<>(ResourceRequestStatus.class);

    static {
        ALLOWED.put(REQUESTED, EnumSet.of(REQUESTED, CONFIRMED, CONFLICTED, FULFILLED, MANUAL_COORDINATION,
                CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(REQUESTED, CONFLICTED, FULFILLED, CANCELLED));
        ALLOWED.put(CONFLICTED, EnumSet.of(REQUESTED, CONFIRMED, CONFLICTED, MANUAL_COORDINATION, CANCELLED));
        ALLOWED.put(FULFILLED, EnumSet.noneOf(ResourceRequestStatus.class));
        ALLOWED.put(MANUAL_COORDINATION, EnumSet.of(REQUESTED, MANUAL_COORDINATION, CANCELLED));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(ResourceRequestStatus.class));
    }

    public boolean canTransitionTo(ResourceRequestStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return this == FULFILLED || this == CANCELLED;
    }

    /** Still part of the event's plan. A cancelled request is history, not a line to resource. */
    public boolean isLive() {
        return this != CANCELLED;
    }
}
