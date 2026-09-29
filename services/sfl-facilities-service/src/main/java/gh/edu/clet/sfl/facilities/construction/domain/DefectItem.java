package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A defects-liability item - SRS-SFL-S176-04.
 *
 * <p>"Defects identified during the liability period raise S153 work orders tagged to the project and
 * contractor rather than as ordinary maintenance." The work order is S153's; this record is S176's
 * view of it - which project, which liable contractor, and whether it is resolved. It is resolved in
 * one of exactly two ways: its work order is closed in S153 (observed, never asserted by hand here),
 * or somebody holding the close authority defers it with a reason.
 *
 * <p>A cancelled work order does not close the defect. Cancelling the job does not fix the wall; the
 * defect stays open until it is re-raised and closed, or deferred on the record.
 */
public record DefectItem(
        UUID id,
        String defectReference,
        UUID projectId,
        UUID contractorId,
        String siteCode,
        String description,
        UUID roomId,
        String locationCode,
        Priority priority,
        Status status,
        UUID workOrderId,
        String workOrderNumber,
        String faultNumber,
        String workOrderStatus,
        String deferralReason,
        String resolvedBy,
        Instant resolvedAt,
        String raisedBy,
        Instant raisedAt,
        RecordMetadata metadata) {

    public enum Status {
        OPEN,
        CLOSED,
        DEFERRED
    }

    /** S176's own priority scale, mapped to S153's in the adapter so the domain does not name S153's types. */
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public DefectItem {
        Objects.requireNonNull(id, "id is required");
        defectReference = EstateCodes.normalize(defectReference);
        Objects.requireNonNull(projectId, "projectId is required");
        Objects.requireNonNull(contractorId, "contractorId is required");
        siteCode = EstateCodes.normalize(siteCode);
        if (description == null || description.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A defect must be described.");
        }
        description = description.strip();
        locationCode = EstateCodes.blankToNull(locationCode);
        priority = priority == null ? Priority.MEDIUM : priority;
        Objects.requireNonNull(status, "status is required");
        deferralReason = EstateCodes.blankToNull(deferralReason);
        EstateCodes.require(raisedBy, "raisedBy");
        Objects.requireNonNull(raisedAt, "raisedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (status == Status.DEFERRED && deferralReason == null) {
            throw new IllegalArgumentException("a deferred defect carries its reason");
        }
    }

    public static DefectItem raise(UUID id, String reference, ConstructionProject project, UUID contractorId,
            String description, UUID roomId, String locationCode, Priority priority, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new DefectItem(id, reference, project.id(), contractorId, project.siteCode(), description, roomId,
                locationCode, priority, Status.OPEN, null, null, null, null, null, null, null, actorId, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Links the S153 work order the intake raised for it. Provenance does not move: nobody edited it. */
    public DefectItem withWorkOrder(UUID workOrder, String workOrderNo, String faultNo, String state) {
        return new DefectItem(id, defectReference, projectId, contractorId, siteCode, description, roomId,
                locationCode, priority, status, workOrder, workOrderNo, faultNo, state, deferralReason, resolvedBy,
                resolvedAt, raisedBy, raisedAt, metadata);
    }

    /** S153 reports the work order closed. */
    public DefectItem closeFromWorkOrder(String state, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireOpen();
        return new DefectItem(id, defectReference, projectId, contractorId, siteCode, description, roomId,
                locationCode, priority, Status.CLOSED, workOrderId, workOrderNumber, faultNumber, state, null, actorId,
                at, raisedBy, raisedAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** The work order's state has moved but not to closed; recorded so the backlog reads truthfully. */
    public DefectItem withWorkOrderState(String state) {
        return withWorkOrder(workOrderId, workOrderNumber, faultNumber, state);
    }

    public DefectItem defer(String reason, String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        if (reason == null || reason.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A deferred defect must carry a reason.");
        }
        return new DefectItem(id, defectReference, projectId, contractorId, siteCode, description, roomId,
                locationCode, priority, Status.DEFERRED, workOrderId, workOrderNumber, faultNumber, workOrderStatus,
                reason.strip(), actorId, at, raisedBy, raisedAt, metadata.modifiedBy(actorId, at, channel,
                        correlationId));
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    private void requireOpen() {
        if (status != Status.OPEN) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Defect " + defectReference + " is already " + status + ".");
        }
    }
}
