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
 * A reported fault: something is wrong somewhere, and somebody has said so.
 *
 * <p>SRS-SFL-S153-01 names "work order, fault report, preventive schedule, vendor assignment, SLA
 * timer, closure evidence" as this module's operational records, and requires every one of them to
 * carry the system-managed fields and to be linked to an authorised site and, where applicable, a
 * building, room, zone or device reference. This aggregate is the fault report.
 *
 * <h2>What changed from the pre-S152 version, and why</h2>
 *
 * The original record predated the S152 platform and carried none of it — no {@link RecordMetadata},
 * no lifecycle, no optimistic lock, and a {@code locationCode} string where the estate now has real
 * spaces. Three consequences followed, and all three are fixed here:
 *
 * <ul>
 *   <li>A fault could not be tied to a room, so it could not raise a readiness blocker on one. A
 *       fault that stops a hall being used, and a readiness system that does not know about it, is
 *       the gap this module exists to close.</li>
 *   <li>There was no version, so two officers triaging the same fault silently overwrote each other.</li>
 *   <li>The status was a label with nothing enforcing the order. It is now a state machine — see
 *       {@link FacilityFaultStatus}.</li>
 * </ul>
 *
 * <p>{@code locationCode} is kept beside {@code roomId} rather than replaced by it. A fault can be
 * reported against a corridor, a car park or an external wall — places the estate model has no room
 * for, deliberately, because they are not bookable spaces. Requiring a room would mean either
 * inventing rooms for them or refusing the report.
 *
 * @param slaDueAt when this fault must have been dealt with, computed at triage from the
 *        configuration active at that moment. Null until triaged: an untriaged fault has no
 *        confirmed priority, and a deadline measured from a guess is worse than no deadline.
 * @param escalationLevel how many times the SLA evaluator has escalated this fault. Monotonic, which
 *        is what lets the evaluator run twice without escalating twice.
 * @param blockerRaised whether this fault currently holds a readiness blocker open on its room.
 */
public record FacilityFault(
        UUID id,
        String faultNumber,
        String siteCode,
        UUID roomId,
        String locationCode,
        UUID assetId,
        String title,
        String description,
        String category,
        FaultPriority priority,
        FacilityFaultStatus status,
        String reportedBy,
        Instant reportedAt,
        String triagedBy,
        Instant triagedAt,
        String triageNotes,
        UUID duplicateOfFaultId,
        UUID workOrderId,
        Instant slaDueAt,
        int escalationLevel,
        Instant escalatedAt,
        boolean blockerRaised,
        Instant resolvedAt,
        String resolutionNotes,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public FacilityFault {
        Objects.requireNonNull(id, "id is required");
        faultNumber = EstateCodes.normalize(faultNumber);
        siteCode = EstateCodes.normalize(siteCode);
        locationCode = EstateCodes.blankToNull(locationCode);
        EstateCodes.require(title, "title");
        EstateCodes.require(description, "description");
        title = title.strip();
        description = description.strip();
        category = EstateCodes.blankToNull(category);
        triageNotes = EstateCodes.blankToNull(triageNotes);
        resolutionNotes = EstateCodes.blankToNull(resolutionNotes);
        Objects.requireNonNull(priority, "priority is required");
        Objects.requireNonNull(status, "status is required");
        EstateCodes.require(reportedBy, "reportedBy");
        reportedBy = reportedBy.strip();
        Objects.requireNonNull(reportedAt, "reportedAt is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (escalationLevel < 0) {
            throw new IllegalArgumentException("escalationLevel cannot be negative");
        }
        if (roomId == null && locationCode == null) {
            // SRS-SFL-S153-01: "Each record shall be linked to an authorised site, building, room,
            // zone, vehicle or device reference as applicable." A fault carrying a site and nothing
            // else cannot be dispatched anywhere, which makes it a complaint rather than a fault.
            throw new IllegalArgumentException("a fault needs either a room or a location code");
        }
    }

    /** A newly reported fault. Untriaged, so no SLA yet. */
    public static FacilityFault report(
            UUID id,
            String faultNumber,
            String siteCode,
            UUID roomId,
            String locationCode,
            UUID assetId,
            String title,
            String description,
            String category,
            FaultPriority priority,
            String reportedBy,
            Instant reportedAt,
            SourceChannel channel,
            String correlationId) {
        return new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                category, priority, FacilityFaultStatus.REPORTED, reportedBy, reportedAt,
                null, null, null, null, null, null, 0, null, false, null, null,
                RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(reportedBy, reportedAt, channel, correlationId));
    }

    /**
     * Triage: an officer confirms or corrects the priority, and the clock starts.
     *
     * <p>The priority may change here and only here. SRS-SFL-S153-02 computes the SLA from priority,
     * so letting it be edited afterwards would mean either a stale due date or a due date that moves
     * — and a due date that moves is not a deadline.
     */
    public FacilityFault triage(FaultPriority confirmedPriority, String notes, Instant slaDue, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        Objects.requireNonNull(confirmedPriority, "priority is required");
        FacilityFaultStatus next = status.transitionTo(FacilityFaultStatus.TRIAGED);
        return new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                category, confirmedPriority, next, reportedBy, reportedAt, actorId, at, notes,
                duplicateOfFaultId, workOrderId, slaDue, escalationLevel, escalatedAt, blockerRaised,
                resolvedAt, resolutionNotes, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Links the work order raised against this fault. */
    public FacilityFault linkWorkOrder(UUID newWorkOrderId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(newWorkOrderId, "workOrderId is required");
        if (workOrderId != null && !workOrderId.equals(newWorkOrderId)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "This fault already has work order " + workOrderId + ".");
        }
        FacilityFaultStatus next = status == FacilityFaultStatus.WORK_ORDER_CREATED
                ? status
                : status.transitionTo(FacilityFaultStatus.WORK_ORDER_CREATED);
        return new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                category, priority, next, reportedBy, reportedAt, triagedBy, triagedAt, triageNotes,
                duplicateOfFaultId, newWorkOrderId, slaDueAt, escalationLevel, escalatedAt, blockerRaised,
                resolvedAt, resolutionNotes, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** The fault is fixed. Reached when its work order closes, or directly when no work was needed. */
    public FacilityFault resolve(String notes, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        FacilityFaultStatus next = status.transitionTo(FacilityFaultStatus.RESOLVED);
        return new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                category, priority, next, reportedBy, reportedAt, triagedBy, triagedAt, triageNotes,
                duplicateOfFaultId, workOrderId, slaDueAt, escalationLevel, escalatedAt, blockerRaised,
                at, notes, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Dismissed: assessed and found to need no work, withdrawn, or a duplicate of another fault. */
    public FacilityFault dismiss(FacilityFaultStatus outcome, String reason, UUID duplicateOf, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        if (outcome != FacilityFaultStatus.REJECTED && outcome != FacilityFaultStatus.DUPLICATE
                && outcome != FacilityFaultStatus.CANCELLED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    outcome + " is not a dismissal outcome.");
        }
        if (outcome == FacilityFaultStatus.DUPLICATE && duplicateOf == null) {
            throw new IllegalArgumentException("a duplicate must name the fault it duplicates");
        }
        EstateCodes.require(reason, "reason");
        FacilityFaultStatus next = status.transitionTo(outcome);
        return new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                category, priority, next, reportedBy, reportedAt, triagedBy, triagedAt, triageNotes,
                duplicateOf, workOrderId, slaDueAt, escalationLevel, escalatedAt, blockerRaised,
                at, reason, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Records an escalation.
     *
     * <p>Refuses to go backwards. The evaluator is scheduled and at-least-once, so it recomputes the
     * same level for the same fault repeatedly; taking the maximum is what makes a second run a no-op
     * rather than a second notification to the same manager.
     */
    public FacilityFault escalateTo(int level, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (level <= escalationLevel) {
            return this;
        }
        return new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                category, priority, status, reportedBy, reportedAt, triagedBy, triagedAt, triageNotes,
                duplicateOfFaultId, workOrderId, slaDueAt, level, at, blockerRaised, resolvedAt,
                resolutionNotes, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Records whether this fault currently holds a readiness blocker open.
     *
     * <p>Deliberately does not touch the metadata. The flag mirrors a decision readiness made; it is
     * not an edit somebody performed, and bumping the version for it would make every blocker
     * reconciliation collide with a concurrent triage.
     *
     * <p><strong>This is the only place the flag moves.</strong> {@link #resolve} and {@link #dismiss}
     * carry it through unchanged rather than clearing it, and they must: the reconciliation that
     * actually closes the blocker runs <em>after</em> the transition and decides what to do by reading
     * this flag. A transition that helpfully set it to false first would leave the blocker open on the
     * space forever, with the fault reading as resolved — a hall that nobody can book and nothing
     * explains. Found by a test; worth a paragraph.
     */
    public FacilityFault withBlockerRaised(boolean raised) {
        return raised == blockerRaised
                ? this
                : new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                        category, priority, status, reportedBy, reportedAt, triagedBy, triagedAt, triageNotes,
                        duplicateOfFaultId, workOrderId, slaDueAt, escalationLevel, escalatedAt, raised,
                        resolvedAt, resolutionNotes, lifecycleStatus, metadata);
    }

    public FacilityFault changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        RecordLifecycleStatus next = lifecycleStatus.transitionTo(target, "Facility fault");
        return new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                category, priority, status, reportedBy, reportedAt, triagedBy, triagedAt, triageNotes,
                duplicateOfFaultId, workOrderId, slaDueAt, escalationLevel, escalatedAt, blockerRaised,
                resolvedAt, resolutionNotes, next, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when the SLA has passed and the fault is still somebody's to deal with. */
    public boolean isOverdue(Instant now) {
        return slaDueAt != null && status.isOpen() && now.isAfter(slaDueAt);
    }

    /** How far past its SLA this fault is, or {@code null} when it is not overdue. */
    public Duration overdueBy(Instant now) {
        return isOverdue(now) ? Duration.between(slaDueAt, now) : null;
    }
}
