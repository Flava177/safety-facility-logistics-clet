package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowTransition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only transition history.
 *
 * <p>No setters and no version column: rows are inserted and never touched again, and a database
 * trigger rejects UPDATE and DELETE so the rule holds even against direct SQL.
 */
@Entity
@Table(name = "fleet_workflow_transitions", schema = "fleet_logistics")
public class WorkflowTransitionEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_item_id", nullable = false)
    private UUID workflowItemId;

    @Column(name = "sequence", nullable = false)
    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private FleetWorkflowStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30)
    private FleetWorkflowStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkflowAction action;

    @Column(name = "actor_id", nullable = false, length = 160)
    private String actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 2000)
    private String reason;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected WorkflowTransitionEntity() {
    }

    public static WorkflowTransitionEntity from(WorkflowTransition transition) {
        WorkflowTransitionEntity entity = new WorkflowTransitionEntity();
        entity.id = transition.id();
        entity.workflowItemId = transition.workflowItemId();
        entity.sequence = transition.sequence();
        entity.fromStatus = transition.fromStatus();
        entity.toStatus = transition.toStatus();
        entity.action = transition.action();
        entity.actorId = transition.actorId();
        entity.occurredAt = transition.occurredAt();
        entity.reason = transition.reason();
        entity.correlationId = transition.correlationId();
        return entity;
    }

    public WorkflowTransition toDomain() {
        return new WorkflowTransition(id, workflowItemId, sequence, fromStatus, toStatus, action, actorId,
                occurredAt, reason, correlationId);
    }
}
