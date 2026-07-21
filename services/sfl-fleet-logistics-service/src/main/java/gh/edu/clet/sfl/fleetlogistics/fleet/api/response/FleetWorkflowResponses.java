package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionResult;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API representations for trips, inspections and the fleet workflow queue. */
public final class FleetWorkflowResponses {

    private FleetWorkflowResponses() {
    }

    /** A trip (vehicle/driver assignment). */
    public record TripResponse(
            UUID id,
            String tripNumber,
            UUID vehicleId,
            UUID driverId,
            String siteCode,
            String purpose,
            String origin,
            String destination,
            OperatingMode operatingMode,
            Instant plannedStart,
            Instant plannedEnd,
            Instant actualStart,
            Instant actualEnd,
            TripStatus status,
            String holdReason,
            String cancellationReason,
            String closureReason,
            UUID closureEvidenceId,
            Long startOdometer,
            Long endOdometer,
            Long distanceCovered,
            String createdBy,
            Instant createdAt,
            String lastModifiedBy,
            Instant lastModifiedAt,
            long version) {
    }

    /** An inspection, with the derived flags a console needs to render its consequence. */
    public record InspectionResponse(
            UUID id,
            UUID vehicleId,
            UUID tripId,
            String siteCode,
            InspectionType inspectionType,
            InspectionStatus status,
            InspectionResult result,
            boolean permitsUse,
            boolean hasOpenCriticalDefect,
            String performedBy,
            Instant performedAt,
            long odometerReading,
            UUID evidenceId,
            List<FindingResponse> findings,
            String notes,
            long version) {
    }

    /** One checklist finding. */
    public record FindingResponse(
            String checkCode,
            String description,
            String severity,
            boolean resolved,
            String resolutionReference) {
    }

    /** A workflow queue item. */
    public record WorkflowItemResponse(
            UUID id,
            String workflowNumber,
            FleetWorkflowType workflowType,
            String relatedRecordType,
            String relatedRecordId,
            String siteCode,
            String title,
            String description,
            WorkflowPriority priority,
            WorkflowSeverity severity,
            OperatingMode operatingMode,
            FleetWorkflowStatus status,
            String assignee,
            Instant slaDueAt,
            Instant responseDueAt,
            boolean slaBreached,
            int escalationLevel,
            Instant firstResponseAt,
            String holdReason,
            String closureReason,
            UUID closureEvidenceId,
            Instant closedAt,
            String closedBy,
            String createdBy,
            Instant createdAt,
            long version) {
    }

    /** One immutable transition. */
    public record TransitionResponse(
            UUID id,
            long sequence,
            FleetWorkflowStatus fromStatus,
            FleetWorkflowStatus toStatus,
            WorkflowAction action,
            String actorId,
            Instant occurredAt,
            String reason,
            String correlationId) {
    }

    /** One immutable comment. */
    public record CommentResponse(
            UUID id,
            String author,
            String body,
            Instant occurredAt,
            String correlationId) {
    }

    /** A workflow item together with its full immutable history. */
    public record WorkflowHistoryResponse(
            UUID workflowItemId,
            List<TransitionResponse> transitions,
            List<CommentResponse> comments) {
    }
}
