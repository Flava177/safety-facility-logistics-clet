package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The fleet workflow state machine (SRS-SFL-S166-02: creation, assignment, reassignment, escalation,
 * hold, cancellation, closure and permitted reopening).
 *
 * <p>A cancelled item is genuinely terminal; a closed one may be reopened, which is the one exception
 * the SRS allows and it is gated on a privileged permission at the application layer.
 */
public final class FleetWorkflowTransitionPolicy {

    private static final Map<FleetWorkflowStatus, Set<FleetWorkflowStatus>> ALLOWED = allowedTransitions();

    private FleetWorkflowTransitionPolicy() {
    }

    public static boolean canTransition(FleetWorkflowStatus from, FleetWorkflowStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(FleetWorkflowStatus from, FleetWorkflowStatus to) {
        if (!canTransition(from, to)) {
            throw InvalidStateTransitionException.of("FleetWorkflowItem", from, to);
        }
    }

    private static Map<FleetWorkflowStatus, Set<FleetWorkflowStatus>> allowedTransitions() {
        Map<FleetWorkflowStatus, Set<FleetWorkflowStatus>> allowed = new EnumMap<>(FleetWorkflowStatus.class);
        allowed.put(FleetWorkflowStatus.OPEN, EnumSet.of(
                FleetWorkflowStatus.ASSIGNED, FleetWorkflowStatus.ESCALATED, FleetWorkflowStatus.ON_HOLD,
                FleetWorkflowStatus.CANCELLED));
        // ASSIGNED -> ASSIGNED is reassignment, which the SRS names explicitly.
        allowed.put(FleetWorkflowStatus.ASSIGNED, EnumSet.of(
                FleetWorkflowStatus.ASSIGNED, FleetWorkflowStatus.IN_PROGRESS, FleetWorkflowStatus.ON_HOLD,
                FleetWorkflowStatus.ESCALATED, FleetWorkflowStatus.CLOSED, FleetWorkflowStatus.CANCELLED));
        allowed.put(FleetWorkflowStatus.IN_PROGRESS, EnumSet.of(
                FleetWorkflowStatus.ASSIGNED, FleetWorkflowStatus.ON_HOLD, FleetWorkflowStatus.ESCALATED,
                FleetWorkflowStatus.CLOSED, FleetWorkflowStatus.CANCELLED));
        allowed.put(FleetWorkflowStatus.ON_HOLD, EnumSet.of(
                FleetWorkflowStatus.ASSIGNED, FleetWorkflowStatus.IN_PROGRESS, FleetWorkflowStatus.ESCALATED,
                FleetWorkflowStatus.CANCELLED));
        allowed.put(FleetWorkflowStatus.ESCALATED, EnumSet.of(
                FleetWorkflowStatus.ASSIGNED, FleetWorkflowStatus.IN_PROGRESS, FleetWorkflowStatus.ESCALATED,
                FleetWorkflowStatus.CLOSED, FleetWorkflowStatus.CANCELLED));
        // Reopening is the only way out of CLOSED, and it is privileged.
        allowed.put(FleetWorkflowStatus.CLOSED, EnumSet.of(FleetWorkflowStatus.REOPENED));
        allowed.put(FleetWorkflowStatus.REOPENED, EnumSet.of(
                FleetWorkflowStatus.ASSIGNED, FleetWorkflowStatus.IN_PROGRESS, FleetWorkflowStatus.ON_HOLD,
                FleetWorkflowStatus.ESCALATED, FleetWorkflowStatus.CLOSED, FleetWorkflowStatus.CANCELLED));
        allowed.put(FleetWorkflowStatus.CANCELLED, EnumSet.noneOf(FleetWorkflowStatus.class));
        return Map.copyOf(allowed);
    }
}
