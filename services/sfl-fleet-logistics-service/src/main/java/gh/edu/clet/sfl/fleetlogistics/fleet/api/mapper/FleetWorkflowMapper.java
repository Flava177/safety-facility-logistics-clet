package gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.CommentResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.FindingResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.InspectionResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.TransitionResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.TripResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetWorkflowResponses.WorkflowItemResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowComment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowTransition;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Maps trips, inspections and workflow items to their API representations. */
@Component
public class FleetWorkflowMapper {

    public TripResponse toResponse(Trip trip) {
        return new TripResponse(
                trip.id(), trip.tripNumber(), trip.vehicleId(), trip.driverId(), trip.siteCode().value(),
                trip.purpose(), trip.origin(), trip.destination(), trip.operatingMode(),
                trip.plannedPeriod().start(), trip.plannedPeriod().end(), trip.actualStart(), trip.actualEnd(),
                trip.status(), trip.holdReason(), trip.cancellationReason(), trip.closureReason(),
                trip.closureEvidenceId(), trip.startOdometer(), trip.endOdometer(), trip.distanceCovered(),
                trip.acknowledgement().state(), trip.acknowledgement().reason(),
                trip.acknowledgement().answeredAt(), trip.acknowledgement().answeredBy(),
                trip.metadata().createdBy(), trip.metadata().createdAt(), trip.metadata().lastModifiedBy(),
                trip.metadata().lastModifiedAt(), trip.metadata().version());
    }

    public InspectionResponse toResponse(VehicleInspection inspection) {
        return new InspectionResponse(
                inspection.id(), inspection.vehicleId(), inspection.tripId(), inspection.siteCode().value(),
                inspection.inspectionType(), inspection.status(), inspection.result(), inspection.permitsUse(),
                inspection.hasOpenCriticalDefect(), inspection.performedBy(), inspection.performedAt(),
                inspection.odometerReading(), inspection.evidenceId(),
                inspection.findings().stream()
                        .map(finding -> new FindingResponse(finding.checkCode(), finding.description(),
                                finding.severity().name(), finding.resolved(), finding.resolutionReference()))
                        .toList(),
                inspection.notes(), inspection.metadata().version());
    }

    /**
     * Maps a workflow item.
     *
     * <p>{@code slaBreached} is computed against {@code now} rather than stored, so the queue shows the
     * truth at the moment it is read instead of at the moment the sweep last ran.
     */
    public WorkflowItemResponse toResponse(FleetWorkflowItem item, Instant now) {
        return new WorkflowItemResponse(
                item.id(), item.workflowNumber(), item.workflowType(), item.relatedRecordType(),
                item.relatedRecordId(), item.siteCode().value(), item.title(), item.description(),
                item.priority(), item.severity(), item.operatingMode(), item.status(), item.assignee(),
                item.slaDueAt(), item.responseDueAt(), item.hasBreachedSlaAt(now), item.escalationLevel(),
                item.firstResponseAt(), item.holdReason(), item.closureReason(), item.closureEvidenceId(),
                item.closedAt(), item.closedBy(), item.metadata().createdBy(), item.metadata().createdAt(),
                item.metadata().version());
    }

    public TransitionResponse toResponse(WorkflowTransition transition) {
        return new TransitionResponse(transition.id(), transition.sequence(), transition.fromStatus(),
                transition.toStatus(), transition.action(), transition.actorId(), transition.occurredAt(),
                transition.reason(), transition.correlationId());
    }

    public CommentResponse toResponse(WorkflowComment comment) {
        return new CommentResponse(comment.id(), comment.author(), comment.body(), comment.occurredAt(),
                comment.correlationId());
    }
}
