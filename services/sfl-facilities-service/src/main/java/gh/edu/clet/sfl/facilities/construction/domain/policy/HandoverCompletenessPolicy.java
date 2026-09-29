package gh.edu.clet.sfl.facilities.construction.domain.policy;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Whether a handover carries the S152 register update it owes - SRS-SFL-S176-04.
 *
 * <p>"A handover without a corresponding S152 update is flagged as incomplete, not silently accepted
 * as done." Two ways to fall short, both named:
 *
 * <ol>
 *   <li>No register change at all. A finished building nobody has put in the register is the
 *       "reflected months later" the user story exists to stop.</li>
 *   <li>The project said which spaces it would touch - an S158 space change lists them - and the
 *       handover does not update one of them. "Corresponding" means the update matches the works, not
 *       that some room somewhere was edited.</li>
 * </ol>
 */
public final class HandoverCompletenessPolicy {

    private HandoverCompletenessPolicy() {
    }

    public record Completeness(boolean complete, String reason) {
    }

    /**
     * @param changeCount how many register changes the handover lists
     * @param updatedRoomIds the existing rooms the handover updates
     * @param affectedRoomIds the rooms the project declared it would change
     */
    public static Completeness evaluate(int changeCount, Set<UUID> updatedRoomIds,
            Collection<UUID> affectedRoomIds) {
        if (changeCount == 0) {
            return new Completeness(false,
                    "The handover lists no S152 register change; the new or changed space would not be in the register.");
        }
        List<UUID> missing = affectedRoomIds.stream().filter(id -> !updatedRoomIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            return new Completeness(false, "The project declared changes to space(s) " + missing
                    + " that the handover does not update in S152.");
        }
        return new Completeness(true, null);
    }
}
