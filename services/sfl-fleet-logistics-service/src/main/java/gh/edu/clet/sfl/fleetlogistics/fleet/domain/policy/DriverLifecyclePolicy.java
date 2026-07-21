package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ArchivedRecordImmutableException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The driver profile lifecycle state machine (SRS-SFL-S166-01), mirroring the vehicle lifecycle.
 * Archived driver profiles are read-only and return only through the restoration transition.
 */
public final class DriverLifecyclePolicy {

    private static final Map<DriverLifecycleStatus, Set<DriverLifecycleStatus>> ALLOWED = allowedTransitions();

    private static final Set<DriverLifecycleStatus> PRIVILEGED_TARGETS =
            EnumSet.of(DriverLifecycleStatus.SUSPENDED, DriverLifecycleStatus.ARCHIVED);

    private DriverLifecyclePolicy() {
    }

    public static boolean canTransition(DriverLifecycleStatus from, DriverLifecycleStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(DriverLifecycleStatus from, DriverLifecycleStatus to) {
        if (!canTransition(from, to)) {
            throw InvalidStateTransitionException.of("DriverProfileReference", from, to);
        }
    }

    public static void requireEditable(DriverLifecycleStatus status) {
        if (!status.isEditable()) {
            throw new ArchivedRecordImmutableException(Map.of("lifecycleStatus", status.name()));
        }
    }

    public static boolean isPrivileged(DriverLifecycleStatus from, DriverLifecycleStatus to) {
        return PRIVILEGED_TARGETS.contains(to) || from == DriverLifecycleStatus.ARCHIVED
                || from == DriverLifecycleStatus.SUSPENDED;
    }

    private static Map<DriverLifecycleStatus, Set<DriverLifecycleStatus>> allowedTransitions() {
        Map<DriverLifecycleStatus, Set<DriverLifecycleStatus>> allowed = new EnumMap<>(DriverLifecycleStatus.class);
        allowed.put(DriverLifecycleStatus.ACTIVE, EnumSet.of(
                DriverLifecycleStatus.INACTIVE, DriverLifecycleStatus.SUSPENDED, DriverLifecycleStatus.ARCHIVED));
        allowed.put(DriverLifecycleStatus.INACTIVE, EnumSet.of(
                DriverLifecycleStatus.ACTIVE, DriverLifecycleStatus.SUSPENDED, DriverLifecycleStatus.ARCHIVED));
        allowed.put(DriverLifecycleStatus.SUSPENDED, EnumSet.of(
                DriverLifecycleStatus.ACTIVE, DriverLifecycleStatus.INACTIVE, DriverLifecycleStatus.ARCHIVED));
        allowed.put(DriverLifecycleStatus.ARCHIVED, EnumSet.of(DriverLifecycleStatus.INACTIVE));
        return Map.copyOf(allowed);
    }
}
