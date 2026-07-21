package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowComment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only workflow comment; the database trigger rejects UPDATE and DELETE. */
@Entity
@Table(name = "fleet_workflow_comments", schema = "fleet_logistics")
public class WorkflowCommentEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_item_id", nullable = false)
    private UUID workflowItemId;

    @Column(nullable = false, length = 160)
    private String author;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected WorkflowCommentEntity() {
    }

    public static WorkflowCommentEntity from(WorkflowComment comment) {
        WorkflowCommentEntity entity = new WorkflowCommentEntity();
        entity.id = comment.id();
        entity.workflowItemId = comment.workflowItemId();
        entity.author = comment.author();
        entity.body = comment.body();
        entity.occurredAt = comment.occurredAt();
        entity.correlationId = comment.correlationId();
        return entity;
    }

    public WorkflowComment toDomain() {
        return new WorkflowComment(id, workflowItemId, author, body, occurredAt, correlationId);
    }
}
