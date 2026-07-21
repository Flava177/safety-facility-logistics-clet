package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import java.util.Map;
import java.util.UUID;

/**
 * The fleet workflow commands (SRS-SFL-S166-02).
 *
 * <p>Grouped in one file because each is a small record over the same shape and reading them together
 * makes the queue's vocabulary obvious.
 */
public final class FleetWorkflowCommands {

    private FleetWorkflowCommands() {
    }

    /** Raise a workflow item. */
    public record RaiseWorkflowItem(
            FleetWorkflowType workflowType,
            String relatedRecordType,
            String relatedRecordId,
            String siteCode,
            String title,
            String description,
            WorkflowPriority priority,
            WorkflowSeverity severity,
            OperatingMode operatingMode,
            String assignee,
            ActorContext actor,
            SourceChannel sourceChannel,
            String idempotencyKey) implements FleetCommand {

        public Map<String, Object> idempotencyPayload() {
            return Map.of(
                    "workflowType", String.valueOf(workflowType),
                    "relatedRecordType", String.valueOf(relatedRecordType),
                    "relatedRecordId", String.valueOf(relatedRecordId),
                    "siteCode", String.valueOf(siteCode),
                    "title", String.valueOf(title));
        }
    }

    /** Assign or reassign an item. */
    public record AssignWorkflowItem(
            UUID workflowItemId,
            String assignee,
            String reason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    /** Move an assigned item into active work. */
    public record StartWorkflowItem(
            UUID workflowItemId,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    /** Hold or resume an item. */
    public record HoldWorkflowItem(
            UUID workflowItemId,
            boolean resume,
            String reason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    /** Escalate an item manually. Privileged. */
    public record EscalateWorkflowItem(
            UUID workflowItemId,
            String reason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    /** Cancel an item. Privileged; reason mandatory. */
    public record CancelWorkflowItem(
            UUID workflowItemId,
            String reason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    /** Close an item. Reason and evidence are both mandatory. */
    public record CloseWorkflowItem(
            UUID workflowItemId,
            String closureReason,
            UUID closureEvidenceId,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    /** Reopen a closed item. Privileged; reason mandatory. */
    public record ReopenWorkflowItem(
            UUID workflowItemId,
            String reason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    /** Add an immutable comment. */
    public record CommentOnWorkflowItem(
            UUID workflowItemId,
            String body,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }
}
