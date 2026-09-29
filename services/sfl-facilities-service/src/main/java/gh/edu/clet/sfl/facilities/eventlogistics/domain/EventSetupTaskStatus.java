package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The set-up task's own state - SRS-SFL-S173-01, -03, -04.
 *
 * <p>{@code OPEN -> CONFIRMED -> COMPLETED}, with {@code CANCELLED} reachable from either live state
 * when S078 cancels the event. Two backward moves are allowed and both are deliberate:
 * {@code CONFIRMED -> OPEN} when S078 changes something the confirmation depended on (the window, the
 * venue, the attendance, a risk flag), so a higher-risk re-plan is re-checked rather than riding on a
 * confirmation given for a different event.
 */
public enum EventSetupTaskStatus {

    /** Created from a confirmed S078 hand-off; being decomposed and resourced. */
    OPEN,
    /** Confirmed by the coordinator; for a higher-risk event, only with a current S165 assessment. */
    CONFIRMED,
    /** Set-up complete. Refused while any request is unresolved without an escalation record. */
    COMPLETED,
    /** S078 cancelled the event. */
    CANCELLED;

    private static final Map<EventSetupTaskStatus, Set<EventSetupTaskStatus>> ALLOWED =
            new java.util.EnumMap<>(EventSetupTaskStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(CONFIRMED, CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(OPEN, COMPLETED, CANCELLED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(EventSetupTaskStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(EventSetupTaskStatus.class));
    }

    public boolean canTransitionTo(EventSetupTaskStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isLive() {
        return this == OPEN || this == CONFIRMED;
    }
}
