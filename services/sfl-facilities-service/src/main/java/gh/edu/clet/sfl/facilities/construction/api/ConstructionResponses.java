package gh.edu.clet.sfl.facilities.construction.api;

import gh.edu.clet.sfl.facilities.construction.application.ConstructionProjectService;
import gh.edu.clet.sfl.facilities.construction.application.ContractorComplianceService;
import gh.edu.clet.sfl.facilities.construction.application.HandoverService;
import gh.edu.clet.sfl.facilities.construction.application.VariationService;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.ContractorCompetency;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.Handover;
import gh.edu.clet.sfl.facilities.construction.domain.Milestone;
import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectApproval;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectContractor;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectPermitLink;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectRevision;
import gh.edu.clet.sfl.facilities.construction.domain.RegisterChange;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import gh.edu.clet.sfl.facilities.construction.domain.policy.ContractorCompliancePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The S176 response shapes, one static {@code from} per domain type. */
public final class ConstructionResponses {

    private ConstructionResponses() {
    }

    public record ProjectResponse(UUID id, String projectReference, String siteCode, String title, String scope,
            List<String> workTypes, BigDecimal budgetBaseline, String currency, int baselineRevision,
            String fundingSourceReference, String fundingSourceName, String projectManagerId, String status,
            String origin, UUID spaceChangeRequestId, UUID committedScenarioId, List<UUID> affectedRoomIds,
            UUID approvalId, Instant startedAt, Instant practicalCompletionAt, Instant handedOverAt,
            LocalDate defectsLiabilityEndsOn, Instant closedAt, String cancellationReason, long version) {

        public static ProjectResponse from(ConstructionProject p) {
            return new ProjectResponse(p.id(), p.projectReference(), p.siteCode(), p.title(), p.scope(),
                    p.workTypes(), p.budgetBaseline(), p.currency(), p.baselineRevision(), p.fundingSourceReference(),
                    p.fundingSourceName(), p.projectManagerId(), p.status().name(), p.origin().name(),
                    p.spaceChangeRequestId(), p.committedScenarioId(), p.affectedRoomIds(), p.approvalId(),
                    p.startedAt(), p.practicalCompletionAt(), p.handedOverAt(), p.defectsLiabilityEndsOn(),
                    p.closedAt(), p.cancellationReason(), p.metadata().version());
        }
    }

    public record ApprovalResponse(UUID id, UUID projectId, String approverId, int baselineRevision,
            BigDecimal baselineAmount, String currency, String note, Instant approvedAt) {

        public static ApprovalResponse from(ProjectApproval a) {
            return new ApprovalResponse(a.id(), a.projectId(), a.approverId(), a.baselineRevision(),
                    a.baselineAmount(), a.currency(), a.note(), a.approvedAt());
        }
    }

    public record MilestoneResponse(UUID id, UUID projectId, String milestoneCode, String name,
            LocalDate targetDate, int revision, LocalDate achievedOn, long version) {

        public static MilestoneResponse from(Milestone m) {
            return new MilestoneResponse(m.id(), m.projectId(), m.milestoneCode(), m.name(), m.targetDate(),
                    m.revision(), m.achievedOn(), m.metadata().version());
        }
    }

    public record RevisionResponse(UUID id, String subject, UUID subjectId, String subjectCode, int revision,
            BigDecimal amount, String currency, LocalDate targetDate, String reason, String revisedBy,
            Instant revisedAt) {

        public static RevisionResponse from(ProjectRevision r) {
            return new RevisionResponse(r.id(), r.subject().name(), r.subjectId(), r.subjectCode(), r.revision(),
                    r.amount(), r.currency(), r.targetDate(), r.reason(), r.revisedBy(), r.revisedAt());
        }
    }

    public record ContractorAssignmentResponse(UUID id, UUID contractorId, String role) {

        public static ContractorAssignmentResponse from(ProjectContractor c) {
            return new ContractorAssignmentResponse(c.id(), c.contractorId(), c.role().name());
        }
    }

    public record PermitLinkResponse(UUID linkId, String permitId, String workType, String permitStatus,
            Instant validFrom, Instant validTo, boolean current) {

        public static PermitLinkResponse from(ConstructionProjectService.LinkedPermit linked) {
            PermitRecord permit = linked.permit();
            return new PermitLinkResponse(linked.link().id(), linked.link().permitId(), linked.link().workType(),
                    permit == null ? "UNKNOWN_TO_S164" : permit.status().name(), permit == null ? null
                            : permit.validFrom(), permit == null ? null : permit.validTo(), linked.current());
        }
    }

    public record ContractorResponse(UUID id, String siteCode, String contractorCode, String name,
            String vendorReference, String insuranceProvider, String insurancePolicyReference,
            LocalDate insuranceExpiresOn, boolean compliant, List<String> lapses, List<String> expiringSoon,
            List<String> currentPermitIds, long version) {

        public static ContractorResponse from(ContractorComplianceService.ContractorView view) {
            Contractor c = view.contractor();
            ContractorCompliancePolicy.Compliance compliance = view.compliance();
            return new ContractorResponse(c.id(), c.siteCode(), c.contractorCode(), c.name(), c.vendorReference(),
                    c.insuranceProvider(), c.insurancePolicyReference(), c.insuranceExpiresOn(),
                    compliance.compliant(), compliance.lapses(), compliance.expiringSoon(),
                    view.currentPermits().stream().map(PermitRecord::permitId).toList(), c.metadata().version());
        }
    }

    public record CompetencyResponse(UUID id, UUID contractorId, String certificationCode, String description,
            String certificateReference, LocalDate expiresOn) {

        public static CompetencyResponse from(ContractorCompetency c) {
            return new CompetencyResponse(c.id(), c.contractorId(), c.certificationCode(), c.description(),
                    c.certificateReference(), c.expiresOn());
        }
    }

    public record SiteAccessGrantResponse(UUID id, UUID contractorId, UUID projectId, String accessScope,
            Instant validFrom, Instant validTo, String status, String refusalReason, Instant suspendedAt,
            String suspensionReason, String enforcement) {

        public static SiteAccessGrantResponse from(SiteAccessGrant g) {
            return new SiteAccessGrantResponse(g.id(), g.contractorId(), g.projectId(), g.accessScope(),
                    g.validFrom(), g.validTo(), g.status().name(), g.refusalReason(), g.suspendedAt(),
                    g.suspensionReason(), g.enforcement().name());
        }
    }

    public record VariationResponse(UUID id, String variationReference, UUID projectId, String changeDescription,
            BigDecimal costDelta, String currency, String justification, String status, boolean escalationRequired,
            String escalationReason, String submittedBy, Instant submittedAt, String decidedBy, Instant decidedAt,
            String decisionNote, String escalatedApprovedBy, BigDecimal cumulativePercent, long version) {

        public static VariationResponse from(VariationOrder v) {
            return new VariationResponse(v.id(), v.variationReference(), v.projectId(), v.changeDescription(),
                    v.costDelta(), v.currency(), v.justification(), v.status().name(), v.escalationRequired(),
                    v.escalationReason(), v.submittedBy(), v.submittedAt(), v.decidedBy(), v.decidedAt(),
                    v.decisionNote(), v.escalatedApprovedBy(), v.cumulativePercent(), v.metadata().version());
        }
    }

    public record BudgetVariationLine(String variationReference, BigDecimal costDelta, Instant approvedAt) {
    }

    public record BudgetResponse(UUID projectId, String projectReference, String currency,
            BigDecimal originalBaseline, int baselineRevision, List<BudgetVariationLine> approvedVariations,
            BigDecimal approvedVariationTotal, BigDecimal currentBudget, BigDecimal cumulativeVariationPercent,
            BigDecimal escalationThresholdPercent, int pendingVariationCount) {

        public static BudgetResponse from(VariationService.BudgetBreakdown b) {
            return new BudgetResponse(b.projectId(), b.projectReference(), b.currency(), b.originalBaseline(),
                    b.baselineRevision(), b.approvedVariations().stream()
                            .map(v -> new BudgetVariationLine(v.variationReference(), v.costDelta(), v.decidedAt()))
                            .toList(),
                    b.approvedVariationTotal(), b.currentBudget(), b.cumulativeVariationPercent(),
                    b.escalationThresholdPercent(), b.pendingVariations().size());
        }
    }

    public record RegisterChangeResponse(UUID id, String action, UUID roomId, String roomCode, long roomVersion) {

        public static RegisterChangeResponse from(RegisterChange c) {
            return new RegisterChangeResponse(c.id(), c.action().name(), c.roomId(), c.roomCode(), c.roomVersion());
        }
    }

    public record HandoverResponse(UUID id, UUID projectId, String outcome, String incompleteReason,
            LocalDate handoverDate, String notes, int registerChangeCount, UUID scenarioId,
            String scenarioConfirmation, String scenarioConfirmationDetail, String recordedBy, Instant recordedAt,
            List<RegisterChangeResponse> registerChanges) {

        public static HandoverResponse from(HandoverService.HandoverView view) {
            Handover h = view.handover();
            return new HandoverResponse(h.id(), h.projectId(), h.outcome().name(), h.incompleteReason(),
                    h.handoverDate(), h.notes(), h.registerChangeCount(), h.scenarioId(),
                    h.scenarioConfirmation().name(), h.scenarioConfirmationDetail(), h.recordedBy(), h.recordedAt(),
                    view.registerChanges().stream().map(RegisterChangeResponse::from).toList());
        }

        public static HandoverResponse from(Handover h) {
            return new HandoverResponse(h.id(), h.projectId(), h.outcome().name(), h.incompleteReason(),
                    h.handoverDate(), h.notes(), h.registerChangeCount(), h.scenarioId(),
                    h.scenarioConfirmation().name(), h.scenarioConfirmationDetail(), h.recordedBy(), h.recordedAt(),
                    List.of());
        }
    }

    public record DefectResponse(UUID id, String defectReference, UUID projectId, UUID contractorId,
            String description, UUID roomId, String locationCode, String priority, String status, UUID workOrderId,
            String workOrderNumber, String faultNumber, String workOrderStatus, String deferralReason,
            Instant raisedAt, long version) {

        public static DefectResponse from(DefectItem d) {
            return new DefectResponse(d.id(), d.defectReference(), d.projectId(), d.contractorId(), d.description(),
                    d.roomId(), d.locationCode(), d.priority().name(), d.status().name(), d.workOrderId(),
                    d.workOrderNumber(), d.faultNumber(), d.workOrderStatus(), d.deferralReason(), d.raisedAt(),
                    d.metadata().version());
        }
    }
}
