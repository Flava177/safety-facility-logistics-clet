package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.Handover;
import gh.edu.clet.sfl.facilities.construction.domain.Milestone;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The S176 dashboard: "project pipeline by stage, milestone/variation exceptions, contractor
 * compliance (insurance, permits), snagging backlog, handover-to-register completeness" (SRS S176
 * system summary).
 *
 * <p>Computed on read from the operational records, never from a snapshot, so a figure on it can
 * always be drilled back to the rows that make it. Every list is narrowed to the caller's sites.
 *
 * <p>{@link #integrations()} is the honest part: it says, for every system S176 depends on, whether
 * anything is actually connected. None of the cross-service ones is.
 */
@Service
public class ConstructionDashboardService {

    private final ConstructionContext context;
    private final ConstructionRepository repository;
    private final ContractorComplianceService contractors;
    private final VariationService variations;

    public ConstructionDashboardService(ConstructionContext context, ContractorComplianceService contractors,
            VariationService variations) {
        this.context = context;
        this.repository = context.repository();
        this.contractors = contractors;
        this.variations = variations;
    }

    public record OverdueMilestone(String projectReference, String milestoneCode, LocalDate targetDate,
            int revision) {
    }

    public record VariationFlag(String projectReference, String variationReference, String kind,
            BigDecimal cumulativePercent) {
    }

    public record ComplianceFlag(String contractorCode, String siteCode, boolean lapsed, List<String> detail) {
    }

    public record DefectBacklog(String projectReference, long openDefects) {
    }

    public record HandoverCompleteness(long handedOver, long flaggedIncomplete, long awaitingHandover,
            long scenarioConfirmationsOutstanding, long registerChangesApplied) {
    }

    public record IntegrationStatus(String system, String direction, String status, String note) {
    }

    public record Dashboard(String siteCode, Instant generatedAt, Map<ProjectStatus, Long> pipeline,
            List<OverdueMilestone> overdueMilestones, long revisedMilestones,
            List<VariationFlag> variationExceptions, List<ComplianceFlag> contractorCompliance,
            long suspendedAccessGrants, String accessEnforcement, List<DefectBacklog> snaggingBacklog,
            HandoverCompleteness handoverCompleteness, List<IntegrationStatus> integrations) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(String siteCode, ActorContext actor, SourceChannel channel) {
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_READ, channel, "ConstructionDashboard",
                "dashboard", siteCode);
        context.authorization().requireRequestedSite(actor, siteCode, channel, "ConstructionDashboard");
        String site = siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
        LocalDate today = context.today();

        List<ConstructionProject> projects = context.authorization().filterBySite(actor,
                repository.findProjectsForSite(site), ConstructionProject::siteCode);
        Map<ProjectStatus, Long> pipeline = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus status : ProjectStatus.values()) {
            pipeline.put(status, 0L);
        }
        List<OverdueMilestone> overdue = new ArrayList<>();
        List<VariationFlag> variationExceptions = new ArrayList<>();
        List<DefectBacklog> backlog = new ArrayList<>();
        long revised = 0;
        long handedOver = 0;
        long flagged = 0;
        long awaiting = 0;
        long scenarioOutstanding = 0;
        long registerChanges = 0;

        for (ConstructionProject project : projects) {
            pipeline.merge(project.status(), 1L, Long::sum);
            boolean live = !project.status().isTerminal();
            for (Milestone milestone : repository.findMilestones(project.id())) {
                if (milestone.revision() > 1) {
                    revised++;
                }
                if (live && project.status() != ProjectStatus.HANDED_OVER && milestone.isOverdue(today)) {
                    overdue.add(new OverdueMilestone(project.projectReference(), milestone.milestoneCode(),
                            milestone.targetDate(), milestone.revision()));
                }
            }
            for (VariationOrder variation : repository.findVariations(project.id())) {
                if (variation.awaitingEscalation()) {
                    variationExceptions.add(new VariationFlag(project.projectReference(),
                            variation.variationReference(), "AWAITING_ESCALATED_APPROVAL", variation.cumulativePercent()));
                }
            }
            if (project.budgetBaseline() != null) {
                VariationService.BudgetBreakdown budget = variations.breakdown(project);
                if (budget.cumulativeVariationPercent().compareTo(budget.escalationThresholdPercent()) > 0) {
                    variationExceptions.add(new VariationFlag(project.projectReference(), null,
                            "CUMULATIVE_ABOVE_THRESHOLD", budget.cumulativeVariationPercent()));
                }
            }
            long open = repository.findDefects(project.id()).stream().filter(DefectItem::isOpen).count();
            if (open > 0) {
                backlog.add(new DefectBacklog(project.projectReference(), open));
            }
            List<Handover> handovers = repository.findHandovers(project.id());
            if (project.status() == ProjectStatus.PRACTICAL_COMPLETION) {
                awaiting++;
                if (handovers.stream().anyMatch(h -> h.outcome() == Handover.Outcome.INCOMPLETE)) {
                    flagged++;
                }
            }
            if (project.status() == ProjectStatus.HANDED_OVER || project.status() == ProjectStatus.CLOSED) {
                handedOver++;
                scenarioOutstanding += handovers.stream()
                        .filter(h -> h.scenarioConfirmation() == Handover.ScenarioConfirmation.UNRESOLVED).count();
                registerChanges += handovers.stream().mapToLong(Handover::registerChangeCount).sum();
            }
        }

        List<ComplianceFlag> compliance = new ArrayList<>();
        long suspended = 0;
        for (Contractor contractor : context.authorization().filterBySite(actor, repository.findContractors(site),
                Contractor::siteCode)) {
            ContractorComplianceService.ContractorView view = contractors.view(contractor);
            if (!view.compliance().compliant()) {
                compliance.add(new ComplianceFlag(contractor.contractorCode(), contractor.siteCode(), true,
                        view.compliance().lapses()));
            } else if (!view.compliance().expiringSoon().isEmpty()) {
                compliance.add(new ComplianceFlag(contractor.contractorCode(), contractor.siteCode(), false,
                        view.compliance().expiringSoon()));
            }
            suspended += view.grants().stream().filter(g -> g.status() == SiteAccessGrant.Status.SUSPENDED).count();
        }

        return new Dashboard(site, context.now(), pipeline, overdue, revised, variationExceptions, compliance,
                suspended, SiteAccessGrant.Enforcement.RECORDED_NOT_ENFORCED.name(), backlog,
                new HandoverCompleteness(handedOver, flagged, awaiting, scenarioOutstanding, registerChanges),
                integrationStatuses());
    }

    /**
     * What S176 is and is not connected to. Stated as fact; nothing here is reported as integrated
     * that is not (SRS CORR-07's rule, applied to the non-vendor dependencies too).
     */
    public List<IntegrationStatus> integrations(ActorContext actor, SourceChannel channel) {
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_READ, channel,
                "ConstructionDashboard", "integrations", "*");
        return integrationStatuses();
    }

    private static List<IntegrationStatus> integrationStatuses() {
        return List.of(
                new IntegrationStatus("S152 CAFM/IWMS", "OUTBOUND", "INTEGRATED_IN_PROCESS",
                        "Handover applies register changes through FacilitiesMasterDataService in the same transaction."),
                new IntegrationStatus("S153 CMMS", "OUTBOUND", "INTEGRATED_IN_PROCESS",
                        "Defects raise CONSTRUCTION_DEFECT work orders through AutomatedWorkOrderIntake."),
                new IntegrationStatus("S158 Space Planning", "BOTH", "CONTRACT_ONLY",
                        "S176 provides ConstructionProjectIntake and consumes ScenarioHandover; S158 is built separately. "
                                + "Until it is merged, scenario confirmations are recorded UNRESOLVED."),
                new IntegrationStatus("S164 Permit-to-Work (SSEMP)", "INBOUND", "NOT_PUBLISHED",
                        "Handler bound to sfl.ssemp.permit-*.v1; S164 is not built and nothing publishes them. "
                                + "Projects whose work type requires a permit are refused the start (fail-closed)."),
                new IntegrationStatus("S160a Access Control / S160 Visitor Management (SSEMP)", "OUTBOUND",
                        "NO_CONSUMER", "Site-access requests and suspensions are recorded and published; "
                                + "no SSEMP consumer exists, so nothing is enforced at the door."),
                new IntegrationStatus("S208 SIEM", "OUTBOUND", "RECORDED_NOT_FORWARDED",
                        "Access suspensions go to the shared recorded SIEM forwarder, which forwards nothing."),
                new IntegrationStatus("S133 Vendor Master", "INBOUND", "NOT_INTEGRATED",
                        "Contractor vendor references are held by value and not verified."),
                new IntegrationStatus("S136 Contract Lifecycle / S141 Payments", "OUTBOUND", "OUT_OF_SCOPE",
                        "Payment certification is not built in this pass."),
                new IntegrationStatus("S223 Master Data Management", "INBOUND", "NOT_INTEGRATED",
                        "Funding sources are held by reference and not resolved."),
                new IntegrationStatus("S140 HRMS", "INBOUND", "NOT_INTEGRATED",
                        "Contractor competency records are entered by hand."));
    }
}
