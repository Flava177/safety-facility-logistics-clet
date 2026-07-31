package gh.edu.clet.sfl.facilities.maintenance.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A unit of maintenance work: who owes what, by when, and what proves it was done.
 *
 * <p>SRS-SFL-S153-02 is the requirement this aggregate answers, and it asks for more than the
 * pre-S152 spine could express: "creation, assignment, reassignment, escalation, hold, cancellation
 * and closure", SLA timers computed from configurable rules, and closure refused without evidence or
 * a reason. The state machine lives in {@link WorkOrderStatus}; the rules about <em>when</em> a legal
 * move is allowed live here.
 *
 * <h2>Three decisions worth knowing before changing this</h2>
 *
 * <ul>
 *   <li><strong>A work order does not have to come from a fault.</strong> {@code facilityFaultId} is
 *       nullable because a {@link WorkOrderType#PREVENTIVE} order is generated from a schedule and
 *       nothing is wrong yet. The old model made it mandatory, which is why preventive maintenance
 *       had nowhere to go.</li>
 *   <li><strong>The SLA is computed once, at creation, and stored.</strong> Recomputing it on read
 *       would let a configuration change silently move every open deadline, including ones already
 *       breached. SRS-SFL-S153-02's rule about evaluating against the active configuration is about
 *       the escalation ladder, not about rewriting deadlines that were already set.</li>
 *   <li><strong>Time on hold does not stop the clock.</strong> {@code totalHeldSeconds} accumulates
 *       so a report can say how much of an overrun was waiting on a part, but the SLA stays fixed: a
 *       hall is no less unusable because the reason is a supplier.</li>
 * </ul>
 *
 * @param evidenceRequired how many pieces of closure evidence this order needs, resolved from
 *        configuration at creation. Stored rather than recomputed, so the assignee is held to the
 *        rule that applied when the work was raised.
 * @param escalationLevel monotonic; see {@link #escalateTo}.
 */
public record WorkOrder(
        UUID id,
        String workOrderNumber,
        WorkOrderType workOrderType,
        UUID facilityFaultId,
        String faultNumber,
        UUID scheduleId,
        String siteCode,
        UUID roomId,
        String locationCode,
        UUID assetId,
        String title,
        String description,
        FaultPriority priority,
        WorkOrderStatus status,
        String assignedTo,
        UUID vendorId,
        Instant assignedAt,
        Instant startedAt,
        String holdReason,
        Instant heldAt,
        long totalHeldSeconds,
        Instant slaDueAt,
        int escalationLevel,
        Instant escalatedAt,
        int evidenceRequired,
        Instant completedAt,
        String completionNotes,
        String closureNotes,
        String closedBy,
        Instant closedAt,
        String cancellationReason,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public WorkOrder {
        Objects.requireNonNull(id, "id is required");
        workOrderNumber = EstateCodes.normalize(workOrderNumber);
        Objects.requireNonNull(workOrderType, "workOrderType is required");
        siteCode = EstateCodes.normalize(siteCode);
        locationCode = EstateCodes.blankToNull(locationCode);
        EstateCodes.require(title, "title");
        title = title.strip();
        description = EstateCodes.blankToNull(description);
        holdReason = EstateCodes.blankToNull(holdReason);
        completionNotes = EstateCodes.blankToNull(completionNotes);
        closureNotes = EstateCodes.blankToNull(closureNotes);
        cancellationReason = EstateCodes.blankToNull(cancellationReason);
        assignedTo = EstateCodes.blankToNull(assignedTo);
        closedBy = EstateCodes.blankToNull(closedBy);
        faultNumber = EstateCodes.blankToNull(faultNumber);
        Objects.requireNonNull(priority, "priority is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (escalationLevel < 0) {
            throw new IllegalArgumentException("escalationLevel cannot be negative");
        }
        if (evidenceRequired < 0) {
            throw new IllegalArgumentException("evidenceRequired cannot be negative");
        }
        if (totalHeldSeconds < 0) {
            throw new IllegalArgumentException("totalHeldSeconds cannot be negative");
        }
        if (workOrderType == WorkOrderType.CORRECTIVE && facilityFaultId == null) {
            throw new IllegalArgumentException("a corrective work order must answer a fault");
        }
        if (workOrderType == WorkOrderType.PREVENTIVE && assetId == null) {
            // Closing a preventive order records the service against the asset. With no asset there
            // is nothing to record it against, and the order would silently do nothing on closure.
            throw new IllegalArgumentException("a preventive work order must name the asset it services");
        }
    }

    /** A corrective order raised against a reported fault. */
    public static WorkOrder fromFault(UUID id, String workOrderNumber, FacilityFault fault, Instant slaDue,
            int evidenceRequired, String actorId, Instant at, SourceChannel channel, String correlationId) {
        Objects.requireNonNull(fault, "fault is required");
        return new WorkOrder(id, workOrderNumber, WorkOrderType.CORRECTIVE, fault.id(), fault.faultNumber(), null,
                fault.siteCode(), fault.roomId(), fault.locationCode(), fault.assetId(), fault.title(),
                fault.description(), fault.priority(), WorkOrderStatus.OPEN, null, null, null, null, null, null,
                0L, slaDue, 0, null, evidenceRequired, null, null, null, null, null, null,
                RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** A planned order generated from a preventive schedule. */
    public static WorkOrder planned(UUID id, String workOrderNumber, WorkOrderType type, UUID scheduleId,
            String siteCode, UUID roomId, String locationCode, UUID assetId, String title, String description,
            FaultPriority priority, Instant slaDue, int evidenceRequired, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new WorkOrder(id, workOrderNumber, type, null, null, scheduleId, siteCode, roomId, locationCode,
                assetId, title, description, priority, WorkOrderStatus.OPEN, null, null, null, null, null, null,
                0L, slaDue, 0, null, evidenceRequired, null, null, null, null, null, null,
                RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Assignment and reassignment, which are the same move.
     *
     * <p>{@code ASSIGNED → ASSIGNED} is a legal transition on purpose: reassignment is not a different
     * state, it is a change of owner that the audit trail records. Modelling it as its own status
     * would make an order reassigned twice look different from one reassigned once.
     *
     * <p>Assigning an order that is on hold releases the hold — handing work to somebody new while
     * telling them it is blocked is not an assignment anybody can act on.
     */
    public WorkOrder assignTo(String assignee, UUID vendor, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        EstateCodes.require(assignee, "assignedTo");
        WorkOrderStatus next = status.transitionTo(WorkOrderStatus.ASSIGNED);
        return copy(next, assignee.strip(), vendor, at, startedAt, null, null,
                totalHeldSeconds + heldSecondsSince(at), escalationLevel, escalatedAt, completedAt,
                completionNotes, closureNotes, closedBy, closedAt, cancellationReason, actorId, at, channel,
                correlationId);
    }

    /** The assignee has started. */
    public WorkOrder start(String actorId, Instant at, SourceChannel channel, String correlationId) {
        WorkOrderStatus next = status.transitionTo(WorkOrderStatus.IN_PROGRESS);
        return copy(next, assignedTo, vendorId, assignedAt, startedAt == null ? at : startedAt, null, null,
                totalHeldSeconds + heldSecondsSince(at), escalationLevel, escalatedAt, completedAt,
                completionNotes, closureNotes, closedBy, closedAt, cancellationReason, actorId, at, channel,
                correlationId);
    }

    /** Blocked on something outside the assignee's control. The reason is required. */
    public WorkOrder hold(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        EstateCodes.require(reason, "holdReason");
        WorkOrderStatus next = status.transitionTo(WorkOrderStatus.ON_HOLD);
        return copy(next, assignedTo, vendorId, assignedAt, startedAt, reason.strip(), at, totalHeldSeconds,
                escalationLevel, escalatedAt, completedAt, completionNotes, closureNotes, closedBy, closedAt,
                cancellationReason, actorId, at, channel, correlationId);
    }

    /** The assignee says the work is done. Not yet accepted — see {@link #close}. */
    public WorkOrder complete(String notes, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        WorkOrderStatus next = status.transitionTo(WorkOrderStatus.COMPLETED);
        return copy(next, assignedTo, vendorId, assignedAt, startedAt, null, null,
                totalHeldSeconds + heldSecondsSince(at), escalationLevel, escalatedAt, at,
                EstateCodes.blankToNull(notes), closureNotes, closedBy, closedAt, cancellationReason, actorId,
                at, channel, correlationId);
    }

    /**
     * Accepted and closed out.
     *
     * <p>SRS-SFL-S153-02: "A workflow cannot be closed without required evidence or closure reason."
     * Both halves are enforced here rather than at the API, so a closure reached by any route — a
     * controller, a saga, a future integration — meets the same bar. The evidence count is passed in
     * because this aggregate does not own the evidence; it owns the rule about how much is needed.
     */
    public WorkOrder close(String notes, int attachedEvidence, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        EstateCodes.require(notes, "closureNotes");
        if (attachedEvidence < evidenceRequired) {
            throw new FacilitiesException.ClosureEvidenceMissingException(evidenceRequired, attachedEvidence);
        }
        WorkOrderStatus next = status.transitionTo(WorkOrderStatus.CLOSED);
        return copy(next, assignedTo, vendorId, assignedAt, startedAt, null, null,
                totalHeldSeconds + heldSecondsSince(at), escalationLevel, escalatedAt,
                completedAt == null ? at : completedAt, completionNotes, notes.strip(), actorId, at,
                cancellationReason, actorId, at, channel, correlationId);
    }

    /** Abandoned before completion. The reason is required and is what a review will read. */
    public WorkOrder cancel(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        EstateCodes.require(reason, "cancellationReason");
        WorkOrderStatus next = status.transitionTo(WorkOrderStatus.CANCELLED);
        return copy(next, assignedTo, vendorId, assignedAt, startedAt, null, null,
                totalHeldSeconds + heldSecondsSince(at), escalationLevel, escalatedAt, completedAt,
                completionNotes, closureNotes, closedBy, closedAt, reason.strip(), actorId, at, channel,
                correlationId);
    }

    /** Reopening a completed order the verifier will not accept. */
    public WorkOrder reopen(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        EstateCodes.require(reason, "reason");
        WorkOrderStatus next = status.transitionTo(WorkOrderStatus.IN_PROGRESS);
        return copy(next, assignedTo, vendorId, assignedAt, startedAt, null, null, totalHeldSeconds,
                escalationLevel, escalatedAt, null, reason.strip(), closureNotes, closedBy, closedAt,
                cancellationReason, actorId, at, channel, correlationId);
    }

    /**
     * Records an escalation. Monotonic, for the same reason {@code FacilityFault.escalateTo} is: the
     * evaluator is scheduled and at-least-once, and escalating twice means notifying twice.
     */
    public WorkOrder escalateTo(int level, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (level <= escalationLevel) {
            return this;
        }
        return copy(status, assignedTo, vendorId, assignedAt, startedAt, holdReason, heldAt, totalHeldSeconds,
                level, at, completedAt, completionNotes, closureNotes, closedBy, closedAt, cancellationReason,
                actorId, at, channel, correlationId);
    }

    public WorkOrder changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        RecordLifecycleStatus next = lifecycleStatus.transitionTo(target, "Work order");
        return new WorkOrder(id, workOrderNumber, workOrderType, facilityFaultId, faultNumber, scheduleId,
                siteCode, roomId, locationCode, assetId, title, description, priority, status, assignedTo,
                vendorId, assignedAt, startedAt, holdReason, heldAt, totalHeldSeconds, slaDueAt, escalationLevel,
                escalatedAt, evidenceRequired, completedAt, completionNotes, closureNotes, closedBy, closedAt,
                cancellationReason, next, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when the SLA has passed and the order is still accruing against it. */
    public boolean isOverdue(Instant now) {
        return slaDueAt != null && status.accruesSla() && now.isAfter(slaDueAt);
    }

    /** How far past its SLA this order is, or {@code null} when it is not overdue. */
    public Duration overdueBy(Instant now) {
        return isOverdue(now) ? Duration.between(slaDueAt, now) : null;
    }

    /** {@code true} when closing this order should record a service against {@link #assetId}. */
    public boolean recordsServiceOnClosure() {
        return workOrderType.recordsService() && assetId != null;
    }

    private long heldSecondsSince(Instant at) {
        return heldAt == null ? 0L : Math.max(0L, Duration.between(heldAt, at).getSeconds());
    }

    /**
     * One place the two dozen unchanged components are carried, so each transition above reads as the
     * rule it enforces rather than as a wall of positional arguments. {@code slaDueAt} is deliberately
     * not a parameter: nothing may move a deadline once it is set.
     */
    private WorkOrder copy(WorkOrderStatus newStatus, String newAssignee, UUID newVendor, Instant newAssignedAt,
            Instant newStartedAt, String newHoldReason, Instant newHeldAt, long newHeldSeconds,
            int newEscalationLevel, Instant newEscalatedAt, Instant newCompletedAt, String newCompletionNotes,
            String newClosureNotes, String newClosedBy, Instant newClosedAt, String newCancellationReason,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new WorkOrder(id, workOrderNumber, workOrderType, facilityFaultId, faultNumber, scheduleId,
                siteCode, roomId, locationCode, assetId, title, description, priority, newStatus, newAssignee,
                newVendor, newAssignedAt, newStartedAt, newHoldReason, newHeldAt, newHeldSeconds, slaDueAt,
                newEscalationLevel, newEscalatedAt, evidenceRequired, newCompletedAt, newCompletionNotes,
                newClosureNotes, newClosedBy, newClosedAt, newCancellationReason, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
