package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ClosureEvidenceMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.FleetWorkflowTransitionPolicy;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A fleet workflow item — one piece of accountable work in a queue (SRS-SFL-S166-02).
 *
 * <p>The SLA due date is stamped when the item is raised, from the configuration active at that
 * moment, and re-derived on each escalation. That is deliberate: an item raised under yesterday's SLA
 * is judged against yesterday's SLA, while the scheduled evaluation still reads today's configuration
 * to decide what to do about it.
 *
 * <p>Closure requires both a reason and evidence, and reopening is a privileged action the caller must
 * have been authorised for.
 */
public record FleetWorkflowItem(
        UUID id,
        String workflowNumber,
        FleetWorkflowType workflowType,
        String relatedRecordType,
        String relatedRecordId,
        SiteCode siteCode,
        String title,
        String description,
        WorkflowPriority priority,
        WorkflowSeverity severity,
        OperatingMode operatingMode,
        FleetWorkflowStatus status,
        String assignee,
        Instant slaDueAt,
        Instant responseDueAt,
        int escalationLevel,
        Instant firstResponseAt,
        FleetWorkflowStatus statusBeforeHold,
        String holdReason,
        String closureReason,
        UUID closureEvidenceId,
        Instant closedAt,
        String closedBy,
        RecordMetadata metadata) {

    public FleetWorkflowItem {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(workflowType, "workflowType is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(priority, "priority is required");
        Objects.requireNonNull(severity, "severity is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
        operatingMode = operatingMode == null ? OperatingMode.ROUTINE : operatingMode;
        workflowNumber = requireText(workflowNumber, "workflowNumber", 40);
        title = requireText(title, "title", 200);
        description = requireText(description, "description", 2000);
        if (escalationLevel < 0) {
            throw new IllegalArgumentException("escalationLevel cannot be negative");
        }
    }

    public static FleetWorkflowItem raise(UUID id, String workflowNumber, FleetWorkflowType workflowType,
            String relatedRecordType, String relatedRecordId, SiteCode siteCode, String title, String description,
            WorkflowPriority priority, WorkflowSeverity severity, OperatingMode operatingMode, SlaTarget sla,
            Instant raisedAt, RecordMetadata metadata) {
        Objects.requireNonNull(sla, "sla is required");
        return new FleetWorkflowItem(id, workflowNumber, workflowType, relatedRecordType, relatedRecordId,
                siteCode, title, description, priority, severity, operatingMode, FleetWorkflowStatus.OPEN, null,
                sla.dueAt(raisedAt), sla.responseDueAt(raisedAt), 0, null, null, null, null, null, null, null,
                metadata);
    }

    /** Assigns or reassigns the item. The first assignment also records the first response. */
    public FleetWorkflowItem assignTo(String newAssignee, Instant now, RecordMetadata newMetadata) {
        if (newAssignee == null || newAssignee.isBlank()) {
            throw new IllegalArgumentException("assignee is required");
        }
        FleetWorkflowTransitionPolicy.requireTransition(status, FleetWorkflowStatus.ASSIGNED);
        return copy(FleetWorkflowStatus.ASSIGNED, newAssignee.strip(), slaDueAt, responseDueAt, escalationLevel,
                firstResponseAt == null ? now : firstResponseAt, null, null, closureReason, closureEvidenceId,
                closedAt, closedBy, newMetadata);
    }

    /** Moves an assigned item into active work. */
    public FleetWorkflowItem start(Instant now, RecordMetadata newMetadata) {
        FleetWorkflowTransitionPolicy.requireTransition(status, FleetWorkflowStatus.IN_PROGRESS);
        return copy(FleetWorkflowStatus.IN_PROGRESS, assignee, slaDueAt, responseDueAt, escalationLevel,
                firstResponseAt == null ? now : firstResponseAt, null, null, closureReason, closureEvidenceId,
                closedAt, closedBy, newMetadata);
    }

    /** Holds the item, remembering what it was doing so resume can restore it. */
    public FleetWorkflowItem hold(String reason, RecordMetadata newMetadata) {
        FleetWorkflowTransitionPolicy.requireTransition(status, FleetWorkflowStatus.ON_HOLD);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A hold reason is required");
        }
        return copy(FleetWorkflowStatus.ON_HOLD, assignee, slaDueAt, responseDueAt, escalationLevel,
                firstResponseAt, status, reason.strip(), closureReason, closureEvidenceId, closedAt, closedBy,
                newMetadata);
    }

    public FleetWorkflowItem resume(RecordMetadata newMetadata) {
        if (status != FleetWorkflowStatus.ON_HOLD) {
            throw InvalidStateTransitionException.of("FleetWorkflowItem", status, FleetWorkflowStatus.IN_PROGRESS);
        }
        FleetWorkflowStatus restored = statusBeforeHold == null ? FleetWorkflowStatus.ASSIGNED : statusBeforeHold;
        return copy(restored, assignee, slaDueAt, responseDueAt, escalationLevel, firstResponseAt, null, null,
                closureReason, closureEvidenceId, closedAt, closedBy, newMetadata);
    }

    /**
     * Escalates the item.
     *
     * <p>The assignee is deliberately kept: escalation adds oversight, it does not silently remove the
     * person who was working the item. The new SLA is taken from the rule active at this evaluation.
     */
    public FleetWorkflowItem escalate(SlaTarget sla, Instant escalatedAt, RecordMetadata newMetadata) {
        FleetWorkflowTransitionPolicy.requireTransition(status, FleetWorkflowStatus.ESCALATED);
        Instant newDueAt = sla == null ? slaDueAt : sla.dueAt(escalatedAt);
        return copy(FleetWorkflowStatus.ESCALATED, assignee, newDueAt, responseDueAt, escalationLevel + 1,
                firstResponseAt, null, null, closureReason, closureEvidenceId, closedAt, closedBy, newMetadata);
    }

    /** Returns an escalated item to active work once it is being handled again. */
    public FleetWorkflowItem returnToProgress(RecordMetadata newMetadata) {
        FleetWorkflowTransitionPolicy.requireTransition(status, FleetWorkflowStatus.IN_PROGRESS);
        return copy(FleetWorkflowStatus.IN_PROGRESS, assignee, slaDueAt, responseDueAt, escalationLevel,
                firstResponseAt, null, null, closureReason, closureEvidenceId, closedAt, closedBy, newMetadata);
    }

    /** Cancels the item. Privileged; the reason is mandatory. */
    public FleetWorkflowItem cancel(String reason, Instant now, String actorId, RecordMetadata newMetadata) {
        FleetWorkflowTransitionPolicy.requireTransition(status, FleetWorkflowStatus.CANCELLED);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A cancellation reason is required");
        }
        return copy(FleetWorkflowStatus.CANCELLED, assignee, slaDueAt, responseDueAt, escalationLevel,
                firstResponseAt, null, null, reason.strip(), closureEvidenceId, now, actorId, newMetadata);
    }

    /**
     * Closes the item.
     *
     * <p>SRS-SFL-S166-02: "A workflow cannot be closed without required evidence or closure reason."
     * Both are enforced here, so no caller can close through a side door.
     */
    public FleetWorkflowItem close(String reason, UUID evidenceId, Instant now, String actorId,
            RecordMetadata newMetadata) {
        FleetWorkflowTransitionPolicy.requireTransition(status, FleetWorkflowStatus.CLOSED);
        if (reason == null || reason.isBlank()) {
            throw new ClosureEvidenceMissingException(Map.of(
                    "workflowItemId", id.toString(), "missing", "closureReason"));
        }
        if (evidenceId == null) {
            throw new ClosureEvidenceMissingException(Map.of(
                    "workflowItemId", id.toString(), "missing", "closureEvidenceId"));
        }
        return copy(FleetWorkflowStatus.CLOSED, assignee, slaDueAt, responseDueAt, escalationLevel,
                firstResponseAt, null, null, reason.strip(), evidenceId, now, actorId, newMetadata);
    }

    /** Reopens a closed item. Privileged; the SLA restarts from the reopening. */
    public FleetWorkflowItem reopen(String reason, SlaTarget sla, Instant now, RecordMetadata newMetadata) {
        FleetWorkflowTransitionPolicy.requireTransition(status, FleetWorkflowStatus.REOPENED);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reopening reason is required");
        }
        Instant newDueAt = sla == null ? slaDueAt : sla.dueAt(now);
        Instant newResponseDueAt = sla == null ? responseDueAt : sla.responseDueAt(now);
        return copy(FleetWorkflowStatus.REOPENED, assignee, newDueAt, newResponseDueAt, escalationLevel, null,
                null, null, null, null, null, null, newMetadata);
    }

    /** True when the resolution target has passed and the item is still live. */
    public boolean hasBreachedSlaAt(Instant now) {
        return status.isLive() && slaDueAt != null && now.isAfter(slaDueAt);
    }

    /** True when nobody has responded and the response target has passed. */
    public boolean hasBreachedResponseTargetAt(Instant now) {
        return status.isLive() && firstResponseAt == null && responseDueAt != null && now.isAfter(responseDueAt);
    }

    public boolean isEscalated() {
        return status == FleetWorkflowStatus.ESCALATED || escalationLevel > 0;
    }

    private FleetWorkflowItem copy(FleetWorkflowStatus newStatus, String newAssignee, Instant newSlaDueAt,
            Instant newResponseDueAt, int newEscalationLevel, Instant newFirstResponseAt,
            FleetWorkflowStatus newStatusBeforeHold, String newHoldReason, String newClosureReason,
            UUID newClosureEvidenceId, Instant newClosedAt, String newClosedBy, RecordMetadata newMetadata) {
        return new FleetWorkflowItem(id, workflowNumber, workflowType, relatedRecordType, relatedRecordId,
                siteCode, title, description, priority, severity, operatingMode, newStatus, newAssignee,
                newSlaDueAt, newResponseDueAt, newEscalationLevel, newFirstResponseAt, newStatusBeforeHold,
                newHoldReason, newClosureReason, newClosureEvidenceId, newClosedAt, newClosedBy, newMetadata);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        }
        return stripped;
    }
}
