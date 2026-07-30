package gh.edu.clet.sfl.facilities.readiness.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.util.List;
import java.util.UUID;

/** Every write command the readiness module accepts. */
public final class ReadinessCommands {

    private ReadinessCommands() {
    }

    public record CreateChecklist(
            String siteCode,
            String checklistCode,
            String name,
            String description,
            SpaceType spaceType,
            OperatingMode operatingMode,
            List<ChecklistItem> items,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", String.valueOf(siteCode), String.valueOf(checklistCode),
                    String.valueOf(name));
        }
    }

    public record ChecklistItem(
            String itemCode,
            String description,
            BlockerSeverity severityIfFailed,
            Boolean mandatory,
            Integer weight,
            Integer sortOrder) {
    }

    public record UpdateChecklist(
            UUID checklistId,
            String name,
            String description,
            List<ChecklistItem> items,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    /**
     * A completed inspection.
     *
     * <p>{@code checklistId} is optional: when absent the service resolves the applicable checklist
     * from the space's type and its site's operating mode, which is what a field user's client does
     * rather than making them pick from a list they cannot see.
     */
    public record SubmitAssessment(
            UUID roomId,
            UUID checklistId,
            List<AssessmentAnswer> answers,
            String notes,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.valueOf(roomId) + "|" + String.valueOf(checklistId) + "|"
                    + (answers == null ? "" : answers.stream()
                            .map(answer -> answer.itemCode() + "=" + answer.passed())
                            .sorted()
                            .reduce("", (left, right) -> left + "," + right));
        }
    }

    public record AssessmentAnswer(String itemCode, boolean passed, String comment) {
    }

    /** A blocker an officer saw that no checklist item covers. */
    public record RaiseBlocker(
            UUID roomId,
            BlockerSeverity severity,
            String description,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ResolveBlocker(
            UUID blockerId,
            String resolutionNotes,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record LockReadiness(
            UUID roomId,
            String reason,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record UnlockReadiness(
            UUID roomId,
            String reason,
            ActorContext actor,
            SourceChannel channel) {
    }
}
