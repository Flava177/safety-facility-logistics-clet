package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** The trip state machine (SRS-SFL-S166-02). Completed and cancelled trips are terminal. */
public final class TripTransitionPolicy {

    private static final Map<TripStatus, Set<TripStatus>> ALLOWED = allowedTransitions();

    private TripTransitionPolicy() {
    }

    public static boolean canTransition(TripStatus from, TripStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(TripStatus from, TripStatus to) {
        if (!canTransition(from, to)) {
            throw InvalidStateTransitionException.of("Trip", from, to);
        }
    }

    private static Map<TripStatus, Set<TripStatus>> allowedTransitions() {
        Map<TripStatus, Set<TripStatus>> allowed = new EnumMap<>(TripStatus.class);
        allowed.put(TripStatus.PLANNED, EnumSet.of(TripStatus.ASSIGNED, TripStatus.CANCELLED));
        allowed.put(TripStatus.ASSIGNED, EnumSet.of(
                TripStatus.IN_PROGRESS, TripStatus.ON_HOLD, TripStatus.CANCELLED, TripStatus.ASSIGNED));
        allowed.put(TripStatus.IN_PROGRESS, EnumSet.of(
                TripStatus.ON_HOLD, TripStatus.COMPLETED, TripStatus.CANCELLED));
        // Resume returns to whichever status the hold interrupted, so both are reachable from ON_HOLD.
        allowed.put(TripStatus.ON_HOLD, EnumSet.of(
                TripStatus.ASSIGNED, TripStatus.IN_PROGRESS, TripStatus.CANCELLED));
        allowed.put(TripStatus.COMPLETED, EnumSet.noneOf(TripStatus.class));
        allowed.put(TripStatus.CANCELLED, EnumSet.noneOf(TripStatus.class));
        return Map.copyOf(allowed);
    }
}
