package gh.edu.clet.sfl.facilities.maintenance.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The life of a work order.
 *
 * <p>The pre-S152 spine had three states — {@code OPEN}, {@code ASSIGNED}, {@code CLOSED} — which is
 * too thin for SRS-SFL-S153-02: that requirement names "creation, assignment, reassignment,
 * escalation, hold, cancellation and closure". Hold and cancellation had nowhere to live, so a job
 * waiting on a part looked identical to one nobody had started, and a job abandoned looked identical
 * to one completed. The three original values are kept and keep their meaning, so the existing rows
 * migrate without reinterpretation.
 *
 * <p>{@link #COMPLETED} and {@link #CLOSED} are deliberately separate. The technician says the work
 * is done; an authorised officer says it is accepted. SRS-SFL-S153-02 makes closure the point where
 * evidence is required and where completion events publish, so collapsing the two would mean either
 * demanding evidence from the technician's device or accepting a closure nobody verified.
 *
 * <p>Passing through {@link #COMPLETED} is <strong>not</strong> mandatory, though. Closure is reachable
 * from any working state, because the gate on closing is the closing permission and the evidence rule,
 * not the route taken to get there — and a supervisor who does a job themselves should not have to
 * hand it to themselves first. It is also what the pre-S153 rows did, so they migrate meaning intact.
 *
 * <p>Reassignment is not a state. It is an {@link #ASSIGNED} to {@link #ASSIGNED} move, which this
 * machine allows on purpose — the transition is what the audit trail records, not a status change.
 */
public enum WorkOrderStatus {

    /** Raised, nobody assigned. */
    OPEN,
    /** Has an owner — an internal technician or a vendor. Reassignment stays in this state. */
    ASSIGNED,
    /** The assignee has started. Distinguishes real progress from an untouched queue. */
    IN_PROGRESS,
    /** Blocked on something outside the assignee's control: a part, an access window, a vendor. */
    ON_HOLD,
    /** The assignee says the work is done. Not yet accepted. */
    COMPLETED,
    /** Accepted and closed out, with the required evidence and a closure reason. Terminal. */
    CLOSED,
    /** Abandoned before completion, with a reason. Terminal. */
    CANCELLED;

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED = Map.of(
            OPEN, EnumSet.of(ASSIGNED, CANCELLED),
            ASSIGNED, EnumSet.of(ASSIGNED, IN_PROGRESS, ON_HOLD, COMPLETED, CLOSED, CANCELLED),
            IN_PROGRESS, EnumSet.of(ASSIGNED, ON_HOLD, COMPLETED, CLOSED, CANCELLED),
            ON_HOLD, EnumSet.of(ASSIGNED, IN_PROGRESS, CLOSED, CANCELLED),
            COMPLETED, EnumSet.of(CLOSED, IN_PROGRESS),
            CLOSED, EnumSet.noneOf(WorkOrderStatus.class),
            CANCELLED, EnumSet.noneOf(WorkOrderStatus.class));

    /** {@code true} while the work order still represents outstanding work. */
    public boolean isOpen() {
        return this != CLOSED && this != CANCELLED;
    }

    /** {@code true} when the SLA clock should still be running. A held order is still overdue-able. */
    public boolean accruesSla() {
        return isOpen() && this != COMPLETED;
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    public boolean canTransitionTo(WorkOrderStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** Applies a transition, refusing anything the machine does not allow. */
    public WorkOrderStatus transitionTo(WorkOrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A work order cannot move from " + name() + " to " + target + ".");
        }
        return target;
    }
}
