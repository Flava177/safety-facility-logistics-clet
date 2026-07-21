package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ArchivedRecordImmutableException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The vehicle lifecycle state machine (SRS-SFL-S166-01).
 *
 * <pre>
 *   (register) → ACTIVE ⇄ INACTIVE          SUSPENDED
 *                  │  │                        ▲ │
 *                  │  └──── suspend ───────────┘ │ reinstate (privileged)
 *                  │                             │
 *                  └────── archive ────────► ARCHIVED ──restore (privileged)──► INACTIVE
 * </pre>
 *
 * <p>Archived records are read-only: the SRS forbids editing them outside an authorised restoration
 * workflow, and it forbids hard deletion entirely, so archive-then-restore is the only route.
 */
public final class VehicleLifecyclePolicy {

    private static final Map<VehicleLifecycleStatus, Set<VehicleLifecycleStatus>> ALLOWED = allowedTransitions();

    /** Transitions that need a privileged permission rather than ordinary vehicle management. */
    private static final Set<VehicleLifecycleStatus> PRIVILEGED_TARGETS =
            EnumSet.of(VehicleLifecycleStatus.SUSPENDED, VehicleLifecycleStatus.ARCHIVED);

    private VehicleLifecyclePolicy() {
    }

    public static boolean canTransition(VehicleLifecycleStatus from, VehicleLifecycleStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(VehicleLifecycleStatus from, VehicleLifecycleStatus to) {
        if (!canTransition(from, to)) {
            throw InvalidStateTransitionException.of("Vehicle", from, to);
        }
    }

    /** Guards every mutation: an archived record cannot be edited in place. */
    public static void requireEditable(VehicleLifecycleStatus status) {
        if (!status.isEditable()) {
            throw new ArchivedRecordImmutableException(Map.of("lifecycleStatus", status.name()));
        }
    }

    /** True when the transition needs a privileged permission (suspend, archive, restore, reinstate). */
    public static boolean isPrivileged(VehicleLifecycleStatus from, VehicleLifecycleStatus to) {
        return PRIVILEGED_TARGETS.contains(to) || from == VehicleLifecycleStatus.ARCHIVED
                || from == VehicleLifecycleStatus.SUSPENDED;
    }

    private static Map<VehicleLifecycleStatus, Set<VehicleLifecycleStatus>> allowedTransitions() {
        Map<VehicleLifecycleStatus, Set<VehicleLifecycleStatus>> allowed =
                new EnumMap<>(VehicleLifecycleStatus.class);
        allowed.put(VehicleLifecycleStatus.ACTIVE, EnumSet.of(
                VehicleLifecycleStatus.INACTIVE,
                VehicleLifecycleStatus.SUSPENDED,
                VehicleLifecycleStatus.ARCHIVED));
        allowed.put(VehicleLifecycleStatus.INACTIVE, EnumSet.of(
                VehicleLifecycleStatus.ACTIVE,
                VehicleLifecycleStatus.SUSPENDED,
                VehicleLifecycleStatus.ARCHIVED));
        allowed.put(VehicleLifecycleStatus.SUSPENDED, EnumSet.of(
                VehicleLifecycleStatus.ACTIVE,
                VehicleLifecycleStatus.INACTIVE,
                VehicleLifecycleStatus.ARCHIVED));
        // Restoration returns an archived vehicle to INACTIVE, never straight back into operation.
        allowed.put(VehicleLifecycleStatus.ARCHIVED, EnumSet.of(VehicleLifecycleStatus.INACTIVE));
        return Map.copyOf(allowed);
    }
}
