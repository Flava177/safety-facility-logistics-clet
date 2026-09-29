package gh.edu.clet.sfl.facilities.eventlogistics.api;

import gh.edu.clet.sfl.facilities.eventlogistics.application.EventReadinessView;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventRiskCriteriaService;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.DeliveryOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventReconciliationLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventTemplateLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.OwningSystem;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessEscalation;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventReadinessPolicy;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventRiskPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The S173 wire types. */
public final class EventLogisticsResponses {

    private EventLogisticsResponses() {
    }

    public record HandoffResponse(UUID handoffId, String s078EventReference, String outcome, String action,
            UUID setupTaskId, boolean duplicate) {

        public static HandoffResponse from(EventHandoff handoff, boolean duplicate) {
            return new HandoffResponse(handoff.id(), handoff.s078EventReference(), handoff.outcome().name(),
                    handoff.action() == null ? null : handoff.action().name(), handoff.setupTaskId(), duplicate);
        }
    }

    public record SetupTaskResponse(
            UUID id,
            String siteCode,
            String taskReference,
            String s078EventReference,
            String title,
            String eventCategory,
            Instant startsAt,
            Instant endsAt,
            UUID roomId,
            String roomCode,
            int expectedAttendance,
            String statedRequirements,
            boolean externalContractors,
            boolean temporaryStructures,
            EventSetupTaskStatus status,
            String coordinatorId,
            String riskAssessmentId,
            Integer riskAssessmentVersion,
            String confirmedBy,
            Instant confirmedAt,
            String completedBy,
            Instant completedAt,
            String closureReason,
            long recordVersion) {

        public static SetupTaskResponse from(EventSetupTask task) {
            return new SetupTaskResponse(task.id(), task.siteCode(), task.taskReference(),
                    task.s078EventReference(), task.details().title(), task.details().eventCategory(),
                    task.details().startsAt(), task.details().endsAt(), task.details().roomId(),
                    task.details().roomCode(), task.details().expectedAttendance(),
                    task.details().statedRequirements(), task.details().externalContractors(),
                    task.details().temporaryStructures(), task.status(), task.coordinatorId(),
                    task.riskAssessmentId(), task.riskAssessmentVersion(), task.confirmedBy(), task.confirmedAt(),
                    task.completedBy(), task.completedAt(), task.closureReason(), task.metadata().version());
        }
    }

    public record ResourceRequestResponse(
            UUID id,
            UUID setupTaskId,
            EventResourceType resourceType,
            OwningSystem owningSystem,
            String description,
            int quantity,
            UUID bookableResourceId,
            Instant neededFrom,
            Instant neededTo,
            ResourceRequestStatus status,
            String externalReference,
            String externalParentReference,
            String statusDetail,
            String competingCommitment,
            String manualAcceptedBy,
            String manualArrangedWith,
            boolean unresolved,
            long recordVersion) {

        public static ResourceRequestResponse from(EventResourceRequest request) {
            return new ResourceRequestResponse(request.id(), request.setupTaskId(), request.resourceType(),
                    request.owningSystem(), request.description(), request.quantity(), request.bookableResourceId(),
                    request.neededFrom(), request.neededTo(), request.status(), request.externalReference(),
                    request.externalParentReference(), request.statusDetail(), request.competingCommitment(),
                    request.manualAcceptedBy(), request.manualArrangedWith(), request.isUnresolved(),
                    request.metadata().version());
        }
    }

    /** The consolidated readiness view - SRS-SFL-S173-02. */
    public record ReadinessResponse(
            SetupTaskResponse task,
            ReadinessStatus readiness,
            SummaryResponse summary,
            List<ResourceRequestResponse> requests,
            List<ResourceRequestResponse> conflicts,
            List<ResourceRequestResponse> manualCoordination,
            List<EscalationResponse> escalations,
            Set<EventRiskPolicy.Trigger> riskTriggers,
            Boolean riskAssessmentCurrent,
            String riskAssessmentDetail) {

        public static ReadinessResponse from(EventReadinessView view) {
            return new ReadinessResponse(SetupTaskResponse.from(view.task()), view.readiness(),
                    SummaryResponse.from(view.summary()), view.requests().stream()
                            .map(ResourceRequestResponse::from).toList(),
                    view.conflicts().stream().map(ResourceRequestResponse::from).toList(),
                    view.manualCoordination().stream().map(ResourceRequestResponse::from).toList(),
                    view.escalations().stream().map(EscalationResponse::from).toList(), view.riskTriggers(),
                    view.riskAssessmentCurrent(), view.riskAssessmentDetail());
        }
    }

    public record SummaryResponse(int live, int resolved, int requested, int confirmed, int fulfilled,
            int conflicted, int manualCoordination, int manualAccepted, int cancelled, int completenessPercent) {

        static SummaryResponse from(EventReadinessPolicy.Summary summary) {
            return new SummaryResponse(summary.live(), summary.resolved(), summary.requested(), summary.confirmed(),
                    summary.fulfilled(), summary.conflicted(), summary.manualCoordination(),
                    summary.manualAccepted(), summary.cancelled(), summary.completenessPercent());
        }
    }

    public record EscalationResponse(UUID id, UUID resourceRequestId, ResourceRequestStatus requestStatus,
            Instant escalatedAt, String notifiedTo, String notificationStatus, boolean beforeEventStart) {

        static EscalationResponse from(ReadinessEscalation escalation) {
            return new EscalationResponse(escalation.id(), escalation.resourceRequestId(), escalation.requestStatus(),
                    escalation.escalatedAt(), escalation.notifiedTo(), escalation.notificationStatus(),
                    escalation.beforeEventStart());
        }
    }

    public record ReconciliationLineResponse(UUID id, UUID resourceRequestId, EventResourceType resourceType,
            int requestedQuantity, Integer deliveredQuantity, DeliveryOutcome outcome, String notes,
            Instant recordedAt) {

        public static ReconciliationLineResponse from(EventReconciliationLine line) {
            return new ReconciliationLineResponse(line.id(), line.resourceRequestId(), line.resourceType(),
                    line.requestedQuantity(), line.deliveredQuantity(), line.outcome(), line.notes(),
                    line.recordedAt());
        }
    }

    public record TemplateLineResponse(UUID id, String eventCategory, EventResourceType resourceType,
            String description, int quantity, String lesson, int gapCount, boolean persistent,
            Instant lastGapAt) {

        public static TemplateLineResponse from(EventTemplateLine line, int threshold) {
            return new TemplateLineResponse(line.id(), line.eventCategory(), line.resourceType(), line.description(),
                    line.quantity(), line.lesson(), line.gapCount(), line.isPersistent(threshold), line.lastGapAt());
        }
    }

    public record RiskCriteriaResponse(Integer attendanceThreshold, boolean externalContractorsAreHigherRisk,
            boolean temporaryStructuresAreHigherRisk, Set<String> higherRiskCategories) {

        public static RiskCriteriaResponse from(EventRiskPolicy.RiskCriteria criteria) {
            return new RiskCriteriaResponse(criteria.attendanceThreshold(),
                    criteria.externalContractorsAreHigherRisk(), criteria.temporaryStructuresAreHigherRisk(),
                    criteria.higherRiskCategories());
        }
    }

    public record IntegrationResponse(String ccpEvents, List<OwningSystemResponse> owningSystems,
            String riskAssessments) {

        public static IntegrationResponse from(EventRiskCriteriaService.IntegrationPosition position) {
            return new IntegrationResponse(position.ccpEvents(), position.owningSystems().stream()
                    .map(status -> new OwningSystemResponse(status.system(), status.name(), status.available(),
                            status.statement()))
                    .toList(), position.riskAssessments());
        }
    }

    public record OwningSystemResponse(OwningSystem system, String name, boolean available, String statement) {
    }
}
