package gh.edu.clet.sfl.facilities.maintenance.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The life of a reported fault, as a state machine rather than a label.
 *
 * <p>The pre-S152 spine carried these values with nothing enforcing the order, so any status could
 * follow any other. SRS-SFL-S153-02 requires the transitions themselves to be retained in the audit
 * trail, which is only worth doing if the transitions are real.
 *
 * <p>Two rules are worth naming, because both are refusals a caller will meet:
 *
 * <ul>
 *   <li><strong>{@link #REJECTED} and {@link #DUPLICATE} are terminal.</strong> A fault dismissed as
 *       either can be reported again, but as a new fault with its own number — reopening would leave
 *       an audit trail claiming one report was made when two were.</li>
 *   <li><strong>{@link #RESOLVED} is reachable from {@link #WORK_ORDER_CREATED} only once the work
 *       order is finished</strong>, which the aggregate enforces rather than this enum: the machine
 *       says which moves exist, {@code FacilityFault} says when they are allowed.</li>
 * </ul>
 */
public enum FacilityFaultStatus {

    /** Raised, not yet looked at. */
    REPORTED,
    /** Assessed: priority confirmed or corrected, and either scheduled or dismissed. */
    TRIAGED,
    /** Work is booked. The fault now follows its work order. */
    WORK_ORDER_CREATED,
    /** Fixed and closed out. Terminal. */
    RESOLVED,
    /** Assessed and found to need no work. Terminal. */
    REJECTED,
    /** The same fault as one already open. Terminal, and carries the fault it duplicates. */
    DUPLICATE,
    /** Withdrawn by the reporter or an authorised officer before any work was done. Terminal. */
    CANCELLED;

    private static final Map<FacilityFaultStatus, Set<FacilityFaultStatus>> ALLOWED = Map.of(
            REPORTED, EnumSet.of(TRIAGED, REJECTED, DUPLICATE, CANCELLED),
            TRIAGED, EnumSet.of(WORK_ORDER_CREATED, REJECTED, DUPLICATE, CANCELLED),
            WORK_ORDER_CREATED, EnumSet.of(RESOLVED, CANCELLED),
            RESOLVED, EnumSet.noneOf(FacilityFaultStatus.class),
            REJECTED, EnumSet.noneOf(FacilityFaultStatus.class),
            DUPLICATE, EnumSet.noneOf(FacilityFaultStatus.class),
            CANCELLED, EnumSet.noneOf(FacilityFaultStatus.class));

    /** {@code true} while the fault is still work somebody owes. */
    public boolean isOpen() {
        return this == REPORTED || this == TRIAGED || this == WORK_ORDER_CREATED;
    }

    /** {@code true} once nothing further will happen to this fault. */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    public boolean canTransitionTo(FacilityFaultStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** Applies a transition, refusing anything the machine does not allow. */
    public FacilityFaultStatus transitionTo(FacilityFaultStatus target) {
        if (!canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A fault cannot move from " + name() + " to " + target + ".");
        }
        return target;
    }
}
