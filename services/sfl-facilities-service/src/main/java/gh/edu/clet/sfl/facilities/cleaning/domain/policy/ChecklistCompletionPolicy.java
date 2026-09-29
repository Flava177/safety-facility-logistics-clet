package gh.edu.clet.sfl.facilities.cleaning.domain.policy;

import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The completion gate - SRS-SFL-S169-02: "A task cannot be marked complete with an unaddressed required
 * checklist item or missing required photo evidence."
 *
 * <p>The refusal names every item in the way, not the first. A cleaner told "FLOOR is not done", who
 * fixes it and is then told "BINS needs a photo", has been sent back twice for one visit.
 */
public final class ChecklistCompletionPolicy {

    private ChecklistCompletionPolicy() {
    }

    /** One problem with one item. */
    public record Unaddressed(String itemCode, String label, boolean notDone, boolean photoMissing) {

        public String describe() {
            if (notDone && photoMissing) {
                return itemCode + " (" + label + ") not marked done and photo evidence missing";
            }
            return itemCode + " (" + label + ") " + (notDone ? "not marked done" : "photo evidence missing");
        }
    }

    /** Every item still standing in the way, in checklist order. Empty means the task may complete. */
    public static List<Unaddressed> unaddressed(List<TaskChecklistItem> items) {
        return items.stream()
                .filter(TaskChecklistItem::unaddressed)
                .sorted(Comparator.comparingInt(TaskChecklistItem::sequence))
                .map(item -> new Unaddressed(item.itemCode(), item.label(), !item.done(),
                        item.photoRequired() && item.photo() == null))
                .toList();
    }

    /** The refusal text: SRS wording first, then every blocking item by code. */
    public static String describe(List<Unaddressed> problems) {
        return "Incomplete checklist - task cannot be closed: "
                + problems.stream().map(Unaddressed::describe).collect(Collectors.joining("; ")) + ".";
    }
}
