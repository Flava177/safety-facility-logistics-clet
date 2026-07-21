package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable entry in a workflow item's history (SRS-SFL-S166-02: "The system shall retain all
 * workflow transitions and comments in the audit trail").
 *
 * <p>Append-only in code and in the database: the table rejects UPDATE and DELETE.
 */
public record WorkflowTransition(
        UUID id,
        UUID workflowItemId,
        long sequence,
        FleetWorkflowStatus fromStatus,
        FleetWorkflowStatus toStatus,
        WorkflowAction action,
        String actorId,
        Instant occurredAt,
        String reason,
        String correlationId) {

    public WorkflowTransition {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(workflowItemId, "workflowItemId is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        actorId = actorId.strip();
        reason = reason == null || reason.isBlank() ? null : reason.strip();
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence cannot be negative");
        }
    }

    public static WorkflowTransition of(UUID workflowItemId, long sequence, FleetWorkflowStatus fromStatus,
            FleetWorkflowStatus toStatus, WorkflowAction action, String actorId, Instant occurredAt,
            String reason, String correlationId) {
        return new WorkflowTransition(UUID.randomUUID(), workflowItemId, sequence, fromStatus, toStatus, action,
                actorId, occurredAt, reason, correlationId);
    }
}
