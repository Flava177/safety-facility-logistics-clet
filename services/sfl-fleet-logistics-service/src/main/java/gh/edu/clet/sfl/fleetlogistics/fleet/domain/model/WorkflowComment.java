package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An immutable comment on a workflow item (SRS-SFL-S166-02 transition and comment history). */
public record WorkflowComment(
        UUID id,
        UUID workflowItemId,
        String author,
        String body,
        Instant occurredAt,
        String correlationId) {

    public WorkflowComment {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(workflowItemId, "workflowItemId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("author is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("comment body is required");
        }
        author = author.strip();
        body = body.strip();
        if (body.length() > 4000) {
            throw new IllegalArgumentException("comment body cannot exceed 4000 characters");
        }
    }

    public static WorkflowComment of(UUID workflowItemId, String author, String body, Instant occurredAt,
            String correlationId) {
        return new WorkflowComment(UUID.randomUUID(), workflowItemId, author, body, occurredAt, correlationId);
    }
}
