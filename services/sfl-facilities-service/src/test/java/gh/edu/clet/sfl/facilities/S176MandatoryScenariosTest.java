package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.construction.ConstructionTestSupport;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionCommands;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionConfiguration;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionContext;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionDashboardService;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionProjectService;
import gh.edu.clet.sfl.facilities.construction.application.ContractorComplianceService;
import gh.edu.clet.sfl.facilities.construction.application.HandoverService;
import gh.edu.clet.sfl.facilities.construction.application.PermitProjectionService;
import gh.edu.clet.sfl.facilities.construction.application.SpaceChangeProjectIntake;
import gh.edu.clet.sfl.facilities.construction.application.VariationService;
import gh.edu.clet.sfl.facilities.construction.application.contract.ConstructionProjectIntake;
import gh.edu.clet.sfl.facilities.construction.application.ports.DefectWorkOrderPort;
import gh.edu.clet.sfl.facilities.construction.application.ports.EstateRegisterPort;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.ContractorCompetency;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.Handover;
import gh.edu.clet.sfl.facilities.construction.domain.Milestone;
import gh.edu.clet.sfl.facilities.construction.domain.PermitNotice;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectContractor;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import gh.edu.clet.sfl.facilities.construction.infrastructure.integration.OutboxSiteAccessAdapter;
import gh.edu.clet.sfl.facilities.construction.infrastructure.integration.PermitEventHandler;
import gh.edu.clet.sfl.facilities.construction.infrastructure.integration.S152EstateRegisterAdapter;
import gh.edu.clet.sfl.facilities.construction.infrastructure.integration.S153DefectWorkOrderAdapter;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceCommands;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.application.integration.InboundIntegrationEvent;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import gh.edu.clet.sfl.facilities.support.InMemoryConstructionRepository;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The S176 acceptance criteria, end to end through the application services - one {@code @Nested}
 * class per SRS requirement, one test per acceptance criterion, validation rule and error state.
 *
 * <p>S152 and S153 are exercised for real, through {@code IfimpTestHarness.estate}/{@code intake} and
 * this module's own {@code S152EstateRegisterAdapter}/{@code S153DefectWorkOrderAdapter}: both are
 * built in this workspace, and a double of either would only prove the call was made. S158 (scenario
 * confirmation) and S160a/S160 (site access) have no real counterpart anywhere in the workspace, so
 * {@link ConstructionTestSupport} stands in for both, honestly.
 */
class S176MandatoryScenariosTest {

    private IfimpTestHarness harness;
    private InMemoryConstructionRepository repository;
    private ConstructionConfiguration configuration;
    private ConstructionContext context;
    private EstateRegisterPort estate;
    private DefectWorkOrderPort workOrderPort;
    private gh.edu.clet.sfl.facilities.construction.application.ports.SiteAccessPort siteAccess;
    private ConstructionTestSupport.StubScenarioConfirmationPort scenarios;
    private ConstructionProjectService projects;
    private ContractorComplianceService contractors;
    private VariationService variations;
    private HandoverService handovers;
    private SpaceChangeProjectIntake intake;
    private PermitProjectionService permitProjection;
    private ConstructionDashboardService dashboard;

    @BeforeEach
    void setUp() {
        harness = new IfimpTestHarness();
        repository = new InMemoryConstructionRepository();
        configuration = new ConstructionConfiguration(harness.configuration);
        context = new ConstructionContext(repository, harness.authorization, harness.audit, harness.outbox,
                configuration, harness.clock);
        estate = new S152EstateRegisterAdapter(harness.estate, harness.facilities);
        workOrderPort = new S153DefectWorkOrderAdapter(harness.intake);
        siteAccess = new OutboxSiteAccessAdapter(harness.outbox);
        scenarios = new ConstructionTestSupport.StubScenarioConfirmationPort();
        projects = new ConstructionProjectService(context, estate, harness.idempotency);
        contractors = new ContractorComplianceService(context, siteAccess,
                event -> new gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort.ForwardResult(
                        "TEST", false));
        variations = new VariationService(context);
        handovers = new HandoverService(context, estate, scenarios, workOrderPort);
        intake = new SpaceChangeProjectIntake(context, estate);
        permitProjection = new PermitProjectionService(context);
        dashboard = new ConstructionDashboardService(context, contractors, variations);
    }

    // =============================================================================================
    // Helpers
    // =============================================================================================

    private static final List<String> ONE_MILESTONE_LIST = List.of("PRACTICAL_COMPLETION");

    private ConstructionCommands.RegisterProject registerCommand(List<String> workTypes) {
        return new ConstructionCommands.RegisterProject("MAIN", "New library wing", "Extension to the library block",
                workTypes, new BigDecimal("500000.00"), "GHS", "FUND-CAPEX-2026-014", "Capital Budget 2026",
                List.of(new ConstructionCommands.MilestoneSpec("PRACTICAL_COMPLETION", "Practical completion",
                        LocalDate.of(2026, 12, 1))),
                List.of(), null, harness.projectManager, SourceChannel.WEB, null);
    }

    private ConstructionProject registerAndApprove(List<String> workTypes, UUID contractorId) {
        ConstructionProject project = projects.register(registerCommand(workTypes));
        if (contractorId != null) {
            projects.assignContractor(new ConstructionCommands.AssignContractor(project.id(), contractorId,
                    ProjectContractor.Role.MAIN_CONTRACTOR, harness.projectManager, SourceChannel.WEB));
        }
        return projects.approve(new ConstructionCommands.ApproveProject(project.id(), "Approved for FY26",
                null, harness.director, SourceChannel.WEB));
    }

    private Contractor registerContractor(LocalDate insuranceExpiry) {
        return contractors.register(new ConstructionCommands.RegisterContractor("MAIN", "ACME-01", "Acme Builders",
                "V-1001", "Star Insurance", "POL-1", insuranceExpiry, harness.hseManager, SourceChannel.WEB));
    }

    // =============================================================================================
    // SRS-SFL-S176-01: Project Register, Milestones and Approval Gate
    // =============================================================================================

    @Nested
    @DisplayName("SRS-SFL-S176-01 Project Register, Milestones and Approval Gate")
    class ProjectRegisterAndApprovalGate {

        @Test
        @DisplayName("a project records scope, budget baseline and currency, funding source, milestones and work types")
        void registers_full_definition() {
            ConstructionProject project = projects.register(registerCommand(List.of("FIT_OUT")));

            assertThat(project.status()).isEqualTo(ProjectStatus.REGISTERED);
            assertThat(project.budgetBaseline()).isEqualByComparingTo("500000.00");
            assertThat(project.currency()).isEqualTo("GHS");
            assertThat(project.fundingSourceReference()).isEqualTo("FUND-CAPEX-2026-014");
            assertThat(project.workTypes()).containsExactly("FIT_OUT");
            assertThat(repository.findMilestones(project.id())).hasSize(1);
            assertThat(harness.audit.recorded(AuditAction.PROJECT_REGISTERED)).isTrue();
        }

        @Test
        @DisplayName("an unknown work type is refused at registration")
        void refuses_unknown_work_type() {
            assertThatThrownBy(() -> projects.register(registerCommand(List.of("SPACE_TRAVEL"))))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("SPACE_TRAVEL");
        }

        @Test
        @DisplayName("milestone dates and budget baseline revisions are versioned, never overwritten")
        void baseline_and_milestone_revisions_are_versioned() {
            ConstructionProject project = projects.register(registerCommand(List.of("FIT_OUT")));
            Milestone milestone = repository.findMilestones(project.id()).get(0);

            projects.reviseBaseline(new ConstructionCommands.ReviseBaseline(project.id(), new BigDecimal("550000.00"),
                    null, "Scope grew to include the annex", null, harness.projectManager, SourceChannel.WEB));
            projects.reviseMilestone(new ConstructionCommands.ReviseMilestone(project.id(), milestone.id(),
                    LocalDate.of(2027, 1, 15), "Contractor mobilisation delayed", null, harness.projectManager,
                    SourceChannel.WEB));

            List<gh.edu.clet.sfl.facilities.construction.domain.ProjectRevision> history =
                    projects.history(project.id(), harness.projectManager, SourceChannel.WEB);
            assertThat(history).filteredOn(r -> r.subject() == gh.edu.clet.sfl.facilities.construction.domain.RevisionSubject.BUDGET_BASELINE)
                    .hasSize(2);
            assertThat(history).filteredOn(r -> r.subject() == gh.edu.clet.sfl.facilities.construction.domain.RevisionSubject.MILESTONE_TARGET)
                    .hasSize(2);
            // Revision 1 is the original value, kept, not replaced.
            assertThat(history.stream().filter(r -> r.subject() == gh.edu.clet.sfl.facilities.construction.domain.RevisionSubject.BUDGET_BASELINE
                            && r.revision() == 1).findFirst().orElseThrow().amount())
                    .isEqualByComparingTo("500000.00");
        }

        @Test
        @DisplayName("the accountable approver must not be the project's own manager")
        void self_approval_is_refused() {
            // The accountable approver (director) is, for this one project, also its manager - the
            // separation-of-duties rule refuses that combination whoever holds it.
            ConstructionProject project = projects.register(new ConstructionCommands.RegisterProject("MAIN",
                    "New library wing", "Extension to the library block", List.of("FIT_OUT"),
                    new BigDecimal("500000.00"), "GHS", "FUND-CAPEX-2026-014", "Capital Budget 2026",
                    List.of(new ConstructionCommands.MilestoneSpec("PRACTICAL_COMPLETION", "Practical completion",
                            LocalDate.of(2026, 12, 1))),
                    List.of(), harness.director.actorId(), harness.projectManager, SourceChannel.WEB, null));

            assertThatThrownBy(() -> projects.approve(new ConstructionCommands.ApproveProject(project.id(), null,
                    null, harness.director, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedApprovalException.class);
        }

        @Test
        @DisplayName("Error State - Missing Approval: starting without a recorded sign-off is refused")
        void start_without_approval_is_refused() {
            ConstructionProject project = projects.register(registerCommand(List.of("FIT_OUT")));
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            projects.assignContractor(new ConstructionCommands.AssignContractor(project.id(), contractor.id(),
                    ProjectContractor.Role.MAIN_CONTRACTOR, harness.projectManager, SourceChannel.WEB));

            assertThatThrownBy(() -> projects.start(new ConstructionCommands.StartProject(project.id(), null,
                    harness.projectManager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)
                    .satisfies(failure -> assertThat(((gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException) failure)
                            .code()).isEqualTo(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.PROJECT_APPROVAL_MISSING));
            assertThat(harness.audit.recorded(AuditAction.PROJECT_START_REFUSED)).isTrue();
        }

        @Test
        @DisplayName("AC: a work type requiring a permit with none linked refuses the start - PROJECT_PERMIT_MISSING")
        void start_with_permit_requiring_work_and_no_permit_is_refused() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            ConstructionProject approved = registerAndApprove(List.of("HOT_WORK"), contractor.id());

            assertThatThrownBy(() -> projects.start(new ConstructionCommands.StartProject(approved.id(), null,
                    harness.projectManager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)
                    .satisfies(failure -> assertThat(((gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException) failure)
                            .code()).isEqualTo(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.PROJECT_PERMIT_MISSING));
            assertThat(harness.audit.recorded(AuditAction.PROJECT_START_REFUSED)).isTrue();
        }

        @Test
        @DisplayName("fail-closed: S164 is not built, so a linked permit id nobody has issued still refuses the start")
        void a_linked_but_unissued_permit_does_not_satisfy_the_gate() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            ConstructionProject approved = registerAndApprove(List.of("HOT_WORK"), contractor.id());
            projects.linkPermit(new ConstructionCommands.LinkPermit(approved.id(), "PERMIT-999", "HOT_WORK",
                    harness.projectManager, SourceChannel.WEB));

            assertThatThrownBy(() -> projects.start(new ConstructionCommands.StartProject(approved.id(), null,
                    harness.projectManager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class);
        }

        @Test
        @DisplayName("once S164 publishes permit-issued and the project links it, the start gate is satisfied")
        void start_succeeds_once_the_permit_projection_shows_it_current() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            ConstructionProject approved = registerAndApprove(List.of("HOT_WORK"), contractor.id());

            PermitEventHandler handler = new PermitEventHandler(permitProjection);
            handler.handle(new InboundIntegrationEvent(UUID.randomUUID(), "sfl.ssemp.permit-issued.v1", "Permit",
                    "PERMIT-42", "MAIN", "corr-1", null, Map.of("permitId", "PERMIT-42", "workType", "HOT_WORK",
                            "validFrom", IfimpTestHarness.NOW.minus(Duration.ofDays(1)).toString(), "validTo",
                            IfimpTestHarness.NOW.plus(Duration.ofDays(30)).toString(), "occurredAt",
                            IfimpTestHarness.NOW.toString())));
            projects.linkPermit(new ConstructionCommands.LinkPermit(approved.id(), "PERMIT-42", "HOT_WORK",
                    harness.projectManager, SourceChannel.WEB));

            ConstructionProject started = projects.start(new ConstructionCommands.StartProject(approved.id(), null,
                    harness.projectManager, SourceChannel.WEB));

            assertThat(started.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
            assertThat(harness.outbox.published("sfl.ifimp.project-started.v1")).isTrue();
        }

        @Test
        @DisplayName("a suspended permit stops satisfying the gate, mirroring S164's own resumption rule")
        void a_suspended_permit_no_longer_satisfies_the_gate() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            ConstructionProject approved = registerAndApprove(List.of("HOT_WORK"), contractor.id());
            PermitEventHandler handler = new PermitEventHandler(permitProjection);
            handler.handle(issuedEvent("PERMIT-77", "HOT_WORK"));
            projects.linkPermit(new ConstructionCommands.LinkPermit(approved.id(), "PERMIT-77", "HOT_WORK",
                    harness.projectManager, SourceChannel.WEB));
            handler.handle(new InboundIntegrationEvent(UUID.randomUUID(), "sfl.ssemp.permit-suspended.v1", "Permit",
                    "PERMIT-77", "MAIN", "corr-2", null, Map.of("permitId", "PERMIT-77", "occurredAt",
                            IfimpTestHarness.NOW.plusSeconds(60).toString())));

            assertThatThrownBy(() -> projects.start(new ConstructionCommands.StartProject(approved.id(), null,
                    harness.projectManager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class);
        }

        @Test
        @DisplayName("a project requires a responsible contractor before it can start - PROJECT_CONTRACTOR_UNASSIGNED")
        void start_without_a_responsible_contractor_is_refused() {
            ConstructionProject project = projects.register(registerCommand(List.of("FIT_OUT")));
            ConstructionProject approved = projects.approve(new ConstructionCommands.ApproveProject(project.id(),
                    null, null, harness.director, SourceChannel.WEB));

            assertThatThrownBy(() -> projects.start(new ConstructionCommands.StartProject(approved.id(), null,
                    harness.projectManager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)
                    .satisfies(failure -> assertThat(((gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException) failure)
                            .code()).isEqualTo(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.PROJECT_CONTRACTOR_UNASSIGNED));
        }

        @Test
        @DisplayName("the explicit transition table refuses an invalid move, e.g. REGISTERED straight to CLOSED")
        void invalid_transitions_are_refused() {
            assertThat(ProjectStatus.REGISTERED.canTransitionTo(ProjectStatus.CLOSED)).isFalse();
            assertThat(ProjectStatus.PROPOSED.canTransitionTo(ProjectStatus.IN_PROGRESS)).isFalse();
            assertThat(ProjectStatus.HANDED_OVER.canTransitionTo(ProjectStatus.IN_PROGRESS)).isFalse();
            assertThat(ProjectStatus.CLOSED.isTerminal()).isTrue();
            assertThat(ProjectStatus.CANCELLED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("a construction project manager may manage only the projects they manage")
        void per_record_narrowing_for_project_managers() {
            ConstructionProject own = projects.register(registerCommand(List.of("FIT_OUT")));
            ActorContext otherManager = TestDoubles.actor("other.pm", Set.of(SflRole.CONSTRUCTION_PROJECT_MANAGER),
                    "MAIN");

            assertThatThrownBy(() -> projects.reviseBaseline(new ConstructionCommands.ReviseBaseline(own.id(),
                    new BigDecimal("600000.00"), null, "not my project", null, otherManager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("a request outside the actor's site is refused")
        void other_site_is_refused() {
            ConstructionProject project = projects.register(registerCommand(List.of("FIT_OUT")));

            assertThatThrownBy(() -> projects.find(project.id(), harness.kumasiManager, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("S158's approved space-change hand-off proposes a project subject to every S176 gate")
        void the_intake_registers_a_proposed_project_subject_to_every_gate() {
            ConstructionProjectIntake.SpaceChangeProposal proposal = new ConstructionProjectIntake.SpaceChangeProposal(
                    UUID.randomUUID(), "MAIN", "Convert store to seminar room", "Reassign OFF-101 for teaching",
                    "Law Faculty", "Enrolment growth", null, List.of(harness.office.id()), "space.officer");

            ConstructionProjectIntake.ProposedProject proposed = intake.proposeFromSpaceChange(proposal);

            assertThat(proposed.status()).isEqualTo("PROPOSED");
            ConstructionProject project = repository.findProject(proposed.projectId()).orElseThrow();
            assertThat(project.budgetBaseline()).isNull();
            assertThat(project.origin()).isEqualTo(gh.edu.clet.sfl.facilities.construction.domain.ProjectOrigin.S158_SPACE_CHANGE);
            // Retrying the same hand-off is idempotent on the space-change request id.
            assertThat(intake.proposeFromSpaceChange(proposal).projectId()).isEqualTo(proposed.projectId());
            // Starting it still needs the full gate - registering, approving, permits if required. A
            // PROPOSED project has no sign-off yet, so the refusal is the same one an unapproved
            // directly-registered project gets, not a shortcut around it.
            assertThatThrownBy(() -> projects.start(new ConstructionCommands.StartProject(proposed.projectId(), null,
                    harness.projectManager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)
                    .satisfies(failure -> assertThat(((gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException) failure)
                            .code()).isEqualTo(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.PROJECT_APPROVAL_MISSING));
        }
    }

    private static InboundIntegrationEvent issuedEvent(String permitId, String workType) {
        return new InboundIntegrationEvent(UUID.randomUUID(), "sfl.ssemp.permit-issued.v1", "Permit", permitId,
                "MAIN", "corr-issue-" + permitId, null, Map.of("permitId", permitId, "workType", workType,
                        "validFrom", IfimpTestHarness.NOW.minus(Duration.ofDays(1)).toString(), "validTo",
                        IfimpTestHarness.NOW.plus(Duration.ofDays(30)).toString(), "occurredAt",
                        IfimpTestHarness.NOW.toString()));
    }

    // =============================================================================================
    // SRS-SFL-S176-02: Contractor Compliance and Site-Access Coordination
    // =============================================================================================

    @Nested
    @DisplayName("SRS-SFL-S176-02 Contractor Compliance and Site-Access Coordination")
    class ContractorComplianceAndAccess {

        @Test
        @DisplayName("a compliant contractor is granted access, recorded and published for S160a")
        void access_is_granted_and_published_for_a_compliant_contractor() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));

            SiteAccessGrant grant = contractors.requestAccess(new ConstructionCommands.RequestSiteAccess(
                    contractor.id(), null, "Site gate 2, working hours", null,
                    IfimpTestHarness.NOW.plus(Duration.ofDays(30)), harness.hseManager, SourceChannel.WEB));

            assertThat(grant.status()).isEqualTo(SiteAccessGrant.Status.ACTIVE);
            assertThat(grant.enforcement()).isEqualTo(SiteAccessGrant.Enforcement.RECORDED_NOT_ENFORCED);
            assertThat(harness.outbox.published("sfl.ifimp.contractor-site-access-requested.v1")).isTrue();
        }

        @Test
        @DisplayName("Error State - Compliance Lapsed: expired insurance refuses access with the reason shown")
        void access_is_refused_for_lapsed_insurance() {
            Contractor contractor = registerContractor(IfimpTestHarness.NOW.minus(Duration.ofDays(1))
                    .atZone(ZoneOffset.UTC).toLocalDate());

            assertThatThrownBy(() -> contractors.requestAccess(new ConstructionCommands.RequestSiteAccess(
                    contractor.id(), null, "Site gate 2", null, IfimpTestHarness.NOW.plus(Duration.ofDays(1)),
                    harness.hseManager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)
                    .hasMessageContaining("Insurance")
                    .satisfies(failure -> assertThat(((gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException) failure)
                            .code()).isEqualTo(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.CONTRACTOR_COMPLIANCE_LAPSED));
            assertThat(harness.audit.recorded(AuditAction.CONTRACTOR_ACCESS_REFUSED)).isTrue();
        }

        @Test
        @DisplayName("a lapsed competency also refuses access, named")
        void access_is_refused_for_lapsed_competency() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            contractors.recordCompetency(new ConstructionCommands.RecordCompetency(contractor.id(), "CONFINED_SPACE",
                    "Confined space entry", "CERT-1", IfimpTestHarness.NOW.minus(Duration.ofDays(2))
                            .atZone(ZoneOffset.UTC).toLocalDate(), harness.hseManager, SourceChannel.WEB));

            assertThatThrownBy(() -> contractors.requestAccess(new ConstructionCommands.RequestSiteAccess(
                    contractor.id(), null, "Site gate 2", null, IfimpTestHarness.NOW.plus(Duration.ofDays(1)),
                    harness.hseManager, SourceChannel.WEB)))
                    .hasMessageContaining("CONFINED_SPACE");
        }

        @Test
        @DisplayName("AC: when a contractor's insurance expiry passes, the sweep automatically suspends every active grant")
        void the_sweep_automatically_suspends_lapsed_grants() {
            LocalDate expiresTomorrow = IfimpTestHarness.NOW.plus(Duration.ofDays(1)).atZone(ZoneOffset.UTC)
                    .toLocalDate();
            Contractor contractor = registerContractor(expiresTomorrow);
            SiteAccessGrant grant = contractors.requestAccess(new ConstructionCommands.RequestSiteAccess(
                    contractor.id(), null, "Site gate 2", null, IfimpTestHarness.NOW.plus(Duration.ofDays(60)),
                    harness.hseManager, SourceChannel.WEB));
            assertThat(grant.status()).isEqualTo(SiteAccessGrant.Status.ACTIVE);

            harness.clock.advance(Duration.ofDays(2));
            ContractorComplianceService.SuspensionSweep sweep = contractors.suspendLapsedGrants(harness.system);

            assertThat(sweep.suspended()).isEqualTo(1);
            SiteAccessGrant suspended = repository.findGrant(grant.id()).orElseThrow();
            assertThat(suspended.status()).isEqualTo(SiteAccessGrant.Status.SUSPENDED);
            assertThat(suspended.suspensionReason()).isNotBlank();
            assertThat(harness.outbox.published("sfl.ifimp.contractor-site-access-suspended.v1")).isTrue();
            assertThat(harness.audit.recorded(AuditAction.CONTRACTOR_ACCESS_SUSPENDED)).isTrue();
        }

        @Test
        @DisplayName("a suspended grant is never resumed automatically when insurance is renewed")
        void a_suspended_grant_does_not_reactivate() {
            LocalDate expiresTomorrow = IfimpTestHarness.NOW.plus(Duration.ofDays(1)).atZone(ZoneOffset.UTC)
                    .toLocalDate();
            Contractor contractor = registerContractor(expiresTomorrow);
            SiteAccessGrant grant = contractors.requestAccess(new ConstructionCommands.RequestSiteAccess(
                    contractor.id(), null, "Site gate 2", null, IfimpTestHarness.NOW.plus(Duration.ofDays(60)),
                    harness.hseManager, SourceChannel.WEB));
            harness.clock.advance(Duration.ofDays(2));
            contractors.suspendLapsedGrants(harness.system);

            contractors.updateInsurance(new ConstructionCommands.UpdateInsurance(contractor.id(), null, null,
                    LocalDate.of(2031, 1, 1), null, harness.hseManager, SourceChannel.WEB));

            assertThat(repository.findGrant(grant.id()).orElseThrow().status())
                    .isEqualTo(SiteAccessGrant.Status.SUSPENDED);
        }
    }

    // =============================================================================================
    // SRS-SFL-S176-03: Variation Orders and Budget Control
    // =============================================================================================

    @Nested
    @DisplayName("SRS-SFL-S176-03 Variation Orders and Budget Control")
    class VariationsAndBudget {

        @Test
        @DisplayName("a submitted variation does not affect the budget before its own approval")
        void a_pending_variation_does_not_move_the_budget() {
            ConstructionProject approved = registerAndApprove(List.of("FIT_OUT"), registerContractor(
                    LocalDate.of(2030, 1, 1)).id());
            variations.submit(new ConstructionCommands.SubmitVariation(approved.id(), "Add a lift", new BigDecimal(
                    "20000.00"), null, "Accessibility requirement", harness.projectManager, SourceChannel.WEB));

            VariationService.BudgetBreakdown budget = variations.budget(approved.id(), harness.projectManager,
                    SourceChannel.WEB);

            assertThat(budget.currentBudget()).isEqualByComparingTo(approved.budgetBaseline());
            assertThat(budget.pendingVariations()).hasSize(1);
        }

        @Test
        @DisplayName("the submitter may not decide their own variation")
        void submitter_cannot_decide_own_variation() {
            // No ordinary role holds both FACILITIES_PROJECT_MANAGE (to submit) and
            // FACILITIES_VARIATION_APPROVE (to decide) - that separation is the point. Using the
            // platform account for both isolates the self-decision rule from the permission check:
            // holding every permission does not let an actor decide their own variation.
            ConstructionProject approved = registerAndApprove(List.of("FIT_OUT"), registerContractor(
                    LocalDate.of(2030, 1, 1)).id());
            VariationOrder variation = variations.submit(new ConstructionCommands.SubmitVariation(approved.id(),
                    "Add a lift", new BigDecimal("20000.00"), null, "Accessibility requirement",
                    harness.system, SourceChannel.WEB));

            assertThatThrownBy(() -> variations.decide(new ConstructionCommands.DecideVariation(variation.id(), true,
                    null, harness.system, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedApprovalException.class);
        }

        @Test
        @DisplayName("an approved variation is reflected in the traceable budget breakdown")
        void an_approved_variation_updates_the_traceable_budget() {
            ConstructionProject approved = registerAndApprove(List.of("FIT_OUT"), registerContractor(
                    LocalDate.of(2030, 1, 1)).id());
            VariationOrder variation = variations.submit(new ConstructionCommands.SubmitVariation(approved.id(),
                    "Add a lift", new BigDecimal("20000.00"), null, "Accessibility requirement",
                    harness.projectManager, SourceChannel.WEB));

            variations.decide(new ConstructionCommands.DecideVariation(variation.id(), true, "Agreed",
                    harness.director, SourceChannel.WEB));

            VariationService.BudgetBreakdown budget = variations.budget(approved.id(), harness.director,
                    SourceChannel.WEB);
            assertThat(budget.currentBudget()).isEqualByComparingTo("520000.00");
            assertThat(budget.approvedVariations()).hasSize(1);
            assertThat(harness.outbox.published("sfl.ifimp.project-variation-approved.v1")).isTrue();
        }

        @Test
        @DisplayName("AC: cumulative variations beyond the configured threshold hold a further variation for escalation")
        void a_variation_over_the_threshold_requires_escalated_approval() {
            ConstructionProject approved = registerAndApprove(List.of("FIT_OUT"), registerContractor(
                    LocalDate.of(2030, 1, 1)).id());
            // 500000 baseline, 10% default threshold -> 50000 crosses it.
            VariationOrder big = variations.submit(new ConstructionCommands.SubmitVariation(approved.id(),
                    "Structural rework", new BigDecimal("60000.00"), null, "Ground survey found bad soil",
                    harness.projectManager, SourceChannel.WEB));

            assertThat(big.escalationRequired()).isTrue();
            assertThat(harness.audit.recorded(AuditAction.VARIATION_ESCALATION_REQUIRED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.project-variation-escalation-required.v1")).isTrue();

            assertThatThrownBy(() -> variations.decide(new ConstructionCommands.DecideVariation(big.id(), true, null,
                    harness.director, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)
                    .satisfies(failure -> assertThat(((gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException) failure)
                            .code()).isEqualTo(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.VARIATION_ESCALATION_REQUIRED));

            VariationOrder escalated = variations.approveEscalated(new ConstructionCommands.EscalatedApproval(
                    big.id(), "Board approved", harness.director, SourceChannel.WEB));
            assertThat(escalated.status()).isEqualTo(VariationOrder.Status.APPROVED);
        }

        @Test
        @DisplayName("AC: every further variation is held until the escalated approval is obtained")
        void further_variations_are_blocked_behind_a_held_one() {
            ConstructionProject approved = registerAndApprove(List.of("FIT_OUT"), registerContractor(
                    LocalDate.of(2030, 1, 1)).id());
            VariationOrder held = variations.submit(new ConstructionCommands.SubmitVariation(approved.id(),
                    "Structural rework", new BigDecimal("60000.00"), null, "Ground survey found bad soil",
                    harness.projectManager, SourceChannel.WEB));
            assertThat(held.escalationRequired()).isTrue();

            VariationOrder small = variations.submit(new ConstructionCommands.SubmitVariation(approved.id(),
                    "Repaint corridor", new BigDecimal("500.00"), null, "Snag from client walkthrough",
                    harness.projectManager, SourceChannel.WEB));

            assertThatThrownBy(() -> variations.decide(new ConstructionCommands.DecideVariation(small.id(), true,
                    null, harness.director, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class);
        }

        @Test
        @DisplayName("a variation with FACILITIES_VARIATION_APPROVE alone cannot give escalated sign-off")
        void ordinary_approver_cannot_give_escalated_signoff() {
            ConstructionProject approved = registerAndApprove(List.of("FIT_OUT"), registerContractor(
                    LocalDate.of(2030, 1, 1)).id());
            VariationOrder held = variations.submit(new ConstructionCommands.SubmitVariation(approved.id(),
                    "Structural rework", new BigDecimal("60000.00"), null, "Ground survey found bad soil",
                    harness.projectManager, SourceChannel.WEB));

            assertThatThrownBy(() -> variations.approveEscalated(new ConstructionCommands.EscalatedApproval(
                    held.id(), null, harness.manager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }

    // =============================================================================================
    // SRS-SFL-S176-04: Handover, Defects-Liability Tracking and Register Update
    // =============================================================================================

    @Nested
    @DisplayName("SRS-SFL-S176-04 Handover, Defects-Liability Tracking and Register Update")
    class HandoverAndDefects {

        private ConstructionProject atPracticalCompletion(UUID contractorId) {
            ConstructionProject approved = registerAndApprove(List.of("FIT_OUT"), contractorId);
            ConstructionProject started = projects.start(new ConstructionCommands.StartProject(approved.id(), null,
                    harness.projectManager, SourceChannel.WEB));
            return handovers.recordPracticalCompletion(new ConstructionCommands.RecordPracticalCompletion(
                    started.id(), null, harness.projectManager, SourceChannel.WEB));
        }

        @Test
        @DisplayName("Error State - Incomplete Handover: no S152 register change flags the handover and refuses it")
        void a_handover_with_no_register_change_is_flagged_incomplete() {
            ConstructionProject project = atPracticalCompletion(registerContractor(LocalDate.of(2030, 1, 1)).id());

            assertThatThrownBy(() -> handovers.handover(new ConstructionCommands.RecordHandover(project.id(),
                    LocalDate.of(2026, 12, 5), "Works finished", List.of(), null, harness.manager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)
                    .satisfies(failure -> assertThat(((gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException) failure)
                            .code()).isEqualTo(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.PROJECT_HANDOVER_INCOMPLETE));
            assertThat(harness.audit.recorded(AuditAction.PROJECT_HANDOVER_FLAGGED_INCOMPLETE)).isTrue();
            assertThat(repository.findProject(project.id()).orElseThrow().status())
                    .isEqualTo(ProjectStatus.PRACTICAL_COMPLETION);
        }

        @Test
        @DisplayName("AC: handover applies the S152 register change in the same act, without a separate manual update")
        void handover_applies_the_s152_register_change() {
            ConstructionProject project = atPracticalCompletion(registerContractor(LocalDate.of(2030, 1, 1)).id());

            Handover handover = handovers.handover(new ConstructionCommands.RecordHandover(project.id(),
                    LocalDate.of(2026, 12, 5), "New wing ready",
                    List.of(new ConstructionCommands.RoomChange(ConstructionCommands.RoomChange.Action.CREATE, null,
                            harness.groundFloor.id(), "LIB-201", "Reading Room", SpaceType.LIBRARY, 80, null, null,
                            true, false)),
                    null, harness.manager, SourceChannel.WEB));

            assertThat(handover.outcome()).isEqualTo(Handover.Outcome.COMPLETE);
            assertThat(harness.facilities.findRoomByCode("MAIN", "LIB-201")).isPresent();
            assertThat(repository.findProject(project.id()).orElseThrow().status())
                    .isEqualTo(ProjectStatus.HANDED_OVER);
            assertThat(harness.outbox.published("sfl.ifimp.project-handed-over.v1")).isTrue();
        }

        @Test
        @DisplayName("a handover missing an update to a space the project declared it would change is flagged incomplete")
        void handover_missing_a_declared_space_is_flagged_incomplete() {
            ConstructionProjectIntake.ProposedProject proposed = intake.proposeFromSpaceChange(
                    new ConstructionProjectIntake.SpaceChangeProposal(UUID.randomUUID(), "MAIN", "Reassign office",
                            "Convert office to meeting room", "Registry", "Growth", null,
                            List.of(harness.office.id()), "space.officer"));
            ConstructionProject registered = projects.completeRegistration(new ConstructionCommands.CompleteRegistration(
                    proposed.projectId(), "Convert office to meeting room", List.of("FIT_OUT"),
                    new BigDecimal("10000.00"), "GHS", "FUND-1", null,
                    List.of(new ConstructionCommands.MilestoneSpec("DONE", "Done", LocalDate.of(2026, 11, 1))),
                    List.of(new ConstructionCommands.ContractorSpec(registerContractor(LocalDate.of(2030, 1, 1)).id(),
                            ProjectContractor.Role.MAIN_CONTRACTOR)),
                    null, null, harness.projectManager, SourceChannel.WEB));
            projects.approve(new ConstructionCommands.ApproveProject(registered.id(), null, null, harness.director,
                    SourceChannel.WEB));
            ConstructionProject started = projects.start(new ConstructionCommands.StartProject(registered.id(), null,
                    harness.projectManager, SourceChannel.WEB));
            handovers.recordPracticalCompletion(new ConstructionCommands.RecordPracticalCompletion(started.id(),
                    null, harness.projectManager, SourceChannel.WEB));

            assertThatThrownBy(() -> handovers.handover(new ConstructionCommands.RecordHandover(started.id(),
                    LocalDate.of(2026, 11, 2), null,
                    List.of(new ConstructionCommands.RoomChange(ConstructionCommands.RoomChange.Action.CREATE, null,
                            harness.groundFloor.id(), "IRRELEVANT-1", "Irrelevant room", SpaceType.STORE, null, null,
                            null, null, null)),
                    null, harness.manager, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class);
        }

        @Test
        @DisplayName("where the project carries an S158 scenario, handover asks S158 to confirm it")
        void handover_confirms_a_committed_s158_scenario() {
            UUID scenarioId = UUID.randomUUID();
            scenarios.knownScenarios.add(scenarioId);
            ConstructionProjectIntake.ProposedProject proposed = intake.proposeFromSpaceChange(
                    new ConstructionProjectIntake.SpaceChangeProposal(UUID.randomUUID(), "MAIN", "Reassign office",
                            "Convert office", "Registry", "Growth", scenarioId, List.of(), "space.officer"));
            ConstructionProject registered = projects.completeRegistration(new ConstructionCommands.CompleteRegistration(
                    proposed.projectId(), "Convert office", List.of("FIT_OUT"), new BigDecimal("10000.00"), "GHS",
                    "FUND-1", null,
                    List.of(new ConstructionCommands.MilestoneSpec("DONE", "Done", LocalDate.of(2026, 11, 1))),
                    List.of(new ConstructionCommands.ContractorSpec(registerContractor(LocalDate.of(2030, 1, 1)).id(),
                            ProjectContractor.Role.MAIN_CONTRACTOR)),
                    null, null, harness.projectManager, SourceChannel.WEB));
            projects.approve(new ConstructionCommands.ApproveProject(registered.id(), null, null, harness.director,
                    SourceChannel.WEB));
            ConstructionProject started = projects.start(new ConstructionCommands.StartProject(registered.id(), null,
                    harness.projectManager, SourceChannel.WEB));
            handovers.recordPracticalCompletion(new ConstructionCommands.RecordPracticalCompletion(started.id(), null,
                    harness.projectManager, SourceChannel.WEB));

            Handover handover = handovers.handover(new ConstructionCommands.RecordHandover(started.id(),
                    LocalDate.of(2026, 11, 2), null,
                    List.of(new ConstructionCommands.RoomChange(ConstructionCommands.RoomChange.Action.UPDATE,
                            harness.office.id(), null, null, "Meeting Room 2", SpaceType.MEETING_ROOM, null, null,
                            null, null, null)),
                    null, harness.manager, SourceChannel.WEB));

            assertThat(handover.scenarioConfirmation()).isEqualTo(Handover.ScenarioConfirmation.CONFIRMED);
        }

        @Test
        @DisplayName("an unresolved S158 scenario (unbuilt) still completes handover honestly and can be retried")
        void an_unknown_s158_scenario_completes_handover_unresolved() {
            UUID scenarioId = UUID.randomUUID();
            ConstructionProjectIntake.ProposedProject proposed = intake.proposeFromSpaceChange(
                    new ConstructionProjectIntake.SpaceChangeProposal(UUID.randomUUID(), "MAIN", "Reassign office",
                            "Convert office", "Registry", "Growth", scenarioId, List.of(), "space.officer"));
            ConstructionProject registered = projects.completeRegistration(new ConstructionCommands.CompleteRegistration(
                    proposed.projectId(), "Convert office", List.of("FIT_OUT"), new BigDecimal("10000.00"), "GHS",
                    "FUND-1", null,
                    List.of(new ConstructionCommands.MilestoneSpec("DONE", "Done", LocalDate.of(2026, 11, 1))),
                    List.of(new ConstructionCommands.ContractorSpec(registerContractor(LocalDate.of(2030, 1, 1)).id(),
                            ProjectContractor.Role.MAIN_CONTRACTOR)),
                    null, null, harness.projectManager, SourceChannel.WEB));
            projects.approve(new ConstructionCommands.ApproveProject(registered.id(), null, null, harness.director,
                    SourceChannel.WEB));
            ConstructionProject started = projects.start(new ConstructionCommands.StartProject(registered.id(), null,
                    harness.projectManager, SourceChannel.WEB));
            handovers.recordPracticalCompletion(new ConstructionCommands.RecordPracticalCompletion(started.id(), null,
                    harness.projectManager, SourceChannel.WEB));

            Handover handover = handovers.handover(new ConstructionCommands.RecordHandover(started.id(),
                    LocalDate.of(2026, 11, 2), null,
                    List.of(new ConstructionCommands.RoomChange(ConstructionCommands.RoomChange.Action.UPDATE,
                            harness.office.id(), null, null, "Meeting Room 3", SpaceType.MEETING_ROOM, null, null,
                            null, null, null)),
                    null, harness.manager, SourceChannel.WEB));

            assertThat(handover.outcome()).isEqualTo(Handover.Outcome.COMPLETE);
            assertThat(handover.scenarioConfirmation()).isEqualTo(Handover.ScenarioConfirmation.UNRESOLVED);

            scenarios.knownScenarios.add(scenarioId);
            Handover retried = handovers.retryScenarioConfirmation(new ConstructionCommands.RetryScenarioConfirmation(
                    started.id(), harness.manager, SourceChannel.WEB));
            assertThat(retried.scenarioConfirmation()).isEqualTo(Handover.ScenarioConfirmation.CONFIRMED);
        }

        @Test
        @DisplayName("a defect raised in the liability period is a tagged S153 CONSTRUCTION_DEFECT work order")
        void a_defect_raises_a_tagged_s153_work_order() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            ConstructionProject project = atPracticalCompletion(contractor.id());
            handovers.handover(new ConstructionCommands.RecordHandover(project.id(), LocalDate.of(2026, 12, 5), null,
                    List.of(new ConstructionCommands.RoomChange(ConstructionCommands.RoomChange.Action.CREATE, null,
                            harness.groundFloor.id(), "LIB-202", "Reading Room 2", SpaceType.LIBRARY, 80, null, null,
                            true, false)),
                    null, harness.manager, SourceChannel.WEB));

            DefectItem defect = handovers.raiseDefect(new ConstructionCommands.RaiseDefect(project.id(),
                    contractor.id(), "Leaking window frame", null, "LIB-202", DefectItem.Priority.MEDIUM,
                    harness.manager, SourceChannel.WEB));

            assertThat(defect.workOrderId()).isNotNull();
            gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault fault = harness.maintenance
                    .findFault(harness.maintenance.findWorkOrder(defect.workOrderId()).orElseThrow().facilityFaultId())
                    .orElseThrow();
            assertThat(fault.category()).isEqualTo("CONSTRUCTION_DEFECT");
            assertThat(fault.description()).contains(project.projectReference()).contains(defect.defectReference())
                    .contains(contractor.contractorCode());
            assertThat(harness.outbox.published("sfl.ifimp.project-defect-raised.v1")).isTrue();
        }

        @Test
        @DisplayName("Error State: the project cannot close while a defects-liability item is open - PROJECT_DEFECTS_OPEN")
        void project_cannot_close_with_open_defects() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            ConstructionProject project = atPracticalCompletion(contractor.id());
            handovers.handover(new ConstructionCommands.RecordHandover(project.id(), LocalDate.of(2026, 12, 5), null,
                    List.of(new ConstructionCommands.RoomChange(ConstructionCommands.RoomChange.Action.CREATE, null,
                            harness.groundFloor.id(), "LIB-203", "Reading Room 3", SpaceType.LIBRARY, 80, null, null,
                            true, false)),
                    null, harness.manager, SourceChannel.WEB));
            DefectItem defect = handovers.raiseDefect(new ConstructionCommands.RaiseDefect(project.id(),
                    contractor.id(), "Sticking door", null, "LIB-203", DefectItem.Priority.LOW, harness.manager,
                    SourceChannel.WEB));
            harness.clock.advance(Duration.ofDays(400));

            assertThatThrownBy(() -> handovers.close(new ConstructionCommands.CloseProject(project.id(), null,
                    harness.director, SourceChannel.WEB)))
                    .isInstanceOf(gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)
                    .hasMessageContaining(defect.defectReference())
                    .satisfies(failure -> assertThat(((gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException) failure)
                            .code()).isEqualTo(gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.PROJECT_DEFECTS_OPEN));
        }

        @Test
        @DisplayName("a defect deferred with a reason lets the project close once the liability period ends")
        void a_deferred_defect_allows_closure() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            ConstructionProject project = atPracticalCompletion(contractor.id());
            handovers.handover(new ConstructionCommands.RecordHandover(project.id(), LocalDate.of(2026, 12, 5), null,
                    List.of(new ConstructionCommands.RoomChange(ConstructionCommands.RoomChange.Action.CREATE, null,
                            harness.groundFloor.id(), "LIB-204", "Reading Room 4", SpaceType.LIBRARY, 80, null, null,
                            true, false)),
                    null, harness.manager, SourceChannel.WEB));
            DefectItem defect = handovers.raiseDefect(new ConstructionCommands.RaiseDefect(project.id(),
                    contractor.id(), "Cosmetic scuff", null, "LIB-204", DefectItem.Priority.LOW, harness.manager,
                    SourceChannel.WEB));
            handovers.deferDefect(new ConstructionCommands.DeferDefect(defect.id(),
                    "Client accepted cosmetic finish as-is", harness.director, SourceChannel.WEB));
            harness.clock.advance(Duration.ofDays(450));

            ConstructionProject closed = handovers.close(new ConstructionCommands.CloseProject(project.id(), null,
                    harness.director, SourceChannel.WEB));

            assertThat(closed.status()).isEqualTo(ProjectStatus.CLOSED);
            assertThat(harness.outbox.published("sfl.ifimp.project-closed.v1")).isTrue();
        }

        @Test
        @DisplayName("closing a project with a closed defect (via S153) succeeds")
        void closing_with_a_resolved_defect_succeeds() {
            Contractor contractor = registerContractor(LocalDate.of(2030, 1, 1));
            ConstructionProject project = atPracticalCompletion(contractor.id());
            handovers.handover(new ConstructionCommands.RecordHandover(project.id(), LocalDate.of(2026, 12, 5), null,
                    List.of(new ConstructionCommands.RoomChange(ConstructionCommands.RoomChange.Action.CREATE, null,
                            harness.groundFloor.id(), "LIB-205", "Reading Room 5", SpaceType.LIBRARY, 80, null, null,
                            true, false)),
                    null, harness.manager, SourceChannel.WEB));
            DefectItem defect = handovers.raiseDefect(new ConstructionCommands.RaiseDefect(project.id(),
                    contractor.id(), "Loose handrail", null, "LIB-205", DefectItem.Priority.LOW, harness.manager,
                    SourceChannel.WEB));

            WorkOrder assigned = harness.workOrders.assign(new MaintenanceCommands.AssignWorkOrder(
                    defect.workOrderId(), "tech.one", null, null, harness.supervisor, SourceChannel.WEB));
            harness.workOrders.close(new MaintenanceCommands.CloseWorkOrder(assigned.id(), "Handrail refitted", null,
                    harness.supervisor, SourceChannel.WEB));
            harness.clock.advance(Duration.ofDays(450));

            HandoverService.DefectSync sync = handovers.syncDefects(harness.system);
            assertThat(sync.closed()).isEqualTo(1);
            assertThat(repository.findDefect(defect.id()).orElseThrow().status()).isEqualTo(DefectItem.Status.CLOSED);

            ConstructionProject closed = handovers.close(new ConstructionCommands.CloseProject(project.id(), null,
                    harness.director, SourceChannel.WEB));
            assertThat(closed.status()).isEqualTo(ProjectStatus.CLOSED);
        }
    }

    // =============================================================================================
    // Dashboard
    // =============================================================================================

    @Nested
    @DisplayName("Dashboard read")
    class DashboardTests {

        @Test
        @DisplayName("the dashboard reports pipeline by stage and never claims a dependency as integrated")
        void dashboard_reports_pipeline_and_honest_integration_status() {
            projects.register(registerCommand(List.of("FIT_OUT")));

            ConstructionDashboardService.Dashboard board = dashboard.dashboard("MAIN", harness.director,
                    SourceChannel.WEB);

            assertThat(board.pipeline().get(ProjectStatus.REGISTERED)).isEqualTo(1L);
            assertThat(board.integrations()).extracting(ConstructionDashboardService.IntegrationStatus::status)
                    .doesNotContain("INTEGRATED");
        }
    }

    // =============================================================================================
    // Refusal tests: wrong role
    // =============================================================================================

    @Test
    @DisplayName("an actor without FACILITIES_PROJECT_MANAGE cannot register a project")
    void wrong_role_is_refused() {
        assertThatThrownBy(() -> projects.register(new ConstructionCommands.RegisterProject("MAIN", "x", "y",
                List.of("FIT_OUT"), new BigDecimal("1"), "GHS", "F-1", null,
                List.of(new ConstructionCommands.MilestoneSpec("M", "M", LocalDate.of(2027, 1, 1))), List.of(), null,
                harness.auditor, SourceChannel.WEB, null)))
                .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        assertThat(harness.audit.recorded(AuditAction.AUTHORIZATION_DENIED)).isTrue();
    }
}
