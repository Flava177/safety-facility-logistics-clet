package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A cleaning task's lifecycle - "task generated, assigned to cleaning staff/vendor, completed with
 * evidence" (SRS-SFL-S169-01 workflow).
 *
 * <p>Start is a state of its own, not a flag on assignment, because it is the timestamp the vendor SLA
 * response clock stops on (SRS-SFL-S169-03). A task that jumped from assigned to completed would have
 * no response time to compute, and "computed from actual task timestamps" would become "assumed".
 */
public enum TaskStatus {

    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            OPEN, EnumSet.of(ASSIGNED, CANCELLED),
            // Reassignment before anybody has started is ordinary rota juggling.
            ASSIGNED, EnumSet.of(ASSIGNED, IN_PROGRESS, CANCELLED),
            IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(TaskStatus.class),
            CANCELLED, EnumSet.noneOf(TaskStatus.class));

    /** Returns {@code target} when the move is allowed, refusing it with the SRS wording otherwise. */
    public TaskStatus transitionTo(TaskStatus target) {
        if (!ALLOWED.get(this).contains(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A " + this + " cleaning task cannot move to " + target + ".");
        }
        return target;
    }

    /** {@code true} while the task still commits a crew - what capacity (S169-04) counts. */
    public boolean isLive() {
        return this == OPEN || this == ASSIGNED || this == IN_PROGRESS;
    }
}
