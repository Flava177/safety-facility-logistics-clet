package gh.edu.clet.sfl.facilities.construction.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionCommands;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionProjectService;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.Milestone;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectContractor;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.shared.api.IdempotencyKey;
import gh.edu.clet.sfl.facilities.shared.api.PageResponse;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The project register, milestones, approval and start gate - SRS-SFL-S176-01.
 *
 * <p>{@code Idempotency-Key} is honoured on registration, S159's one state-creating POST pattern
 * repeated here; every other write is a PATCH guarded by the record's version and its state machine.
 */
@RestController
@RequestMapping("/api/v1/facilities/construction/projects")
@Tag(name = "S176 Construction Projects", description = "Project register, milestones, approval and start gate")
public class ConstructionProjectController {

    private final ConstructionProjectService service;
    private final gh.edu.clet.sfl.facilities.construction.application.VariationService variations;

    public ConstructionProjectController(ConstructionProjectService service,
            gh.edu.clet.sfl.facilities.construction.application.VariationService variations) {
        this.service = service;
        this.variations = variations;
    }

    @PostMapping
    @Operation(summary = "Register a construction project",
            description = "SRS-SFL-S176-01. Scope, budget baseline and currency, funding source (by "
                    + "reference - S223 MDM is not integrated), target milestones and work type(s) are "
                    + "all required; a work type not in the configured catalogue is refused.")
    public ResponseEntity<ApiResponse<ConstructionResponses.ProjectResponse>> register(
            @Valid @RequestBody ConstructionRequests.RegisterProject request, ActorContext actor,
            SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        ConstructionProject project = service.register(new ConstructionCommands.RegisterProject(request.siteCode(),
                request.title(), request.scope(), request.workTypes(), request.budgetBaseline(), request.currency(),
                request.fundingSourceReference(), request.fundingSourceName(), milestones(request.milestones()),
                contractors(request.contractors()), request.projectManagerId(), actor, channel, idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/facilities/construction/projects/" + project.id()))
                .body(ApiResponse.ok(ConstructionResponses.ProjectResponse.from(project)));
    }

    @PatchMapping("/{projectId}/registration")
    @Operation(summary = "Complete registration of a proposed project",
            description = "For a PROPOSED project (an S158 hand-off): defines the scope, baseline, "
                    + "funding source, milestones and work types S158's proposal did not carry, moving "
                    + "it to REGISTERED.")
    public ApiResponse<ConstructionResponses.ProjectResponse> completeRegistration(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.CompleteRegistration request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.completeRegistration(new ConstructionCommands.CompleteRegistration(projectId,
                request.scope(), request.workTypes(), request.budgetBaseline(), request.currency(),
                request.fundingSourceReference(), request.fundingSourceName(), milestones(request.milestones()),
                contractors(request.contractors()), request.projectManagerId(), request.expectedVersion(), actor,
                channel)));
    }

    @PatchMapping("/{projectId}/approval")
    @Operation(summary = "Record the accountable approver's sign-off",
            description = "SRS-SFL-S176-01. Requires FACILITIES_PROJECT_APPROVE; refused if the "
                    + "approver is the project's own manager.")
    public ApiResponse<ConstructionResponses.ProjectResponse> approve(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.ApproveProject request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.approve(new ConstructionCommands.ApproveProject(projectId, request.note(),
                request.expectedVersion(), actor, channel)));
    }

    @PatchMapping("/{projectId}/start")
    @Operation(summary = "Start the project (move to in progress)",
            description = "SRS-SFL-S176-01 AC: refused with PROJECT_APPROVAL_MISSING without a sign-off "
                    + "covering the current baseline, or PROJECT_PERMIT_MISSING for a configured "
                    + "permit-requiring work type with no current linked S164 permit. Every refusal is "
                    + "audited PROJECT_START_REFUSED.")
    public ApiResponse<ConstructionResponses.ProjectResponse> start(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.VersionedRequest request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.start(new ConstructionCommands.StartProject(projectId, request.expectedVersion(),
                actor, channel)));
    }

    @PatchMapping("/{projectId}/cancellation")
    @Operation(summary = "Cancel a project before handover, with a reason")
    public ApiResponse<ConstructionResponses.ProjectResponse> cancel(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.CancelProject request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.cancel(new ConstructionCommands.CancelProject(projectId, request.reason(),
                request.expectedVersion(), actor, channel)));
    }

    @PatchMapping("/{projectId}/baseline")
    @Operation(summary = "Revise the budget baseline before works start",
            description = "SRS-SFL-S176-01: versioned, never overwritten - see the history endpoint. "
                    + "Returns an approved project to REGISTERED for a fresh sign-off.")
    public ApiResponse<ConstructionResponses.ProjectResponse> reviseBaseline(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.ReviseBaseline request, ActorContext actor,
            SourceChannel channel) {
        return respond(service.reviseBaseline(new ConstructionCommands.ReviseBaseline(projectId, request.amount(),
                request.currency(), request.reason(), request.expectedVersion(), actor, channel)));
    }

    @PostMapping("/{projectId}/milestones")
    @Operation(summary = "Add a target milestone")
    public ApiResponse<ConstructionResponses.MilestoneResponse> addMilestone(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.MilestoneRequest request, ActorContext actor,
            SourceChannel channel) {
        Milestone milestone = service.addMilestone(new ConstructionCommands.AddMilestone(projectId,
                new ConstructionCommands.MilestoneSpec(request.code(), request.name(), request.targetDate()), actor,
                channel));
        return ApiResponse.ok(ConstructionResponses.MilestoneResponse.from(milestone));
    }

    @PatchMapping("/{projectId}/milestones/{milestoneId}")
    @Operation(summary = "Revise a milestone's target date",
            description = "SRS-SFL-S176-01: versioned, never overwritten.")
    public ApiResponse<ConstructionResponses.MilestoneResponse> reviseMilestone(@PathVariable UUID projectId,
            @PathVariable UUID milestoneId, @Valid @RequestBody ConstructionRequests.ReviseMilestone request,
            ActorContext actor, SourceChannel channel) {
        Milestone milestone = service.reviseMilestone(new ConstructionCommands.ReviseMilestone(projectId,
                milestoneId, request.targetDate(), request.reason(), request.expectedVersion(), actor, channel));
        return ApiResponse.ok(ConstructionResponses.MilestoneResponse.from(milestone));
    }

    @PatchMapping("/{projectId}/milestones/{milestoneId}/achievement")
    @Operation(summary = "Mark a milestone achieved")
    public ApiResponse<ConstructionResponses.MilestoneResponse> achieveMilestone(@PathVariable UUID projectId,
            @PathVariable UUID milestoneId, @RequestBody(required = false) ConstructionRequests.AchieveMilestone request,
            ActorContext actor, SourceChannel channel) {
        Milestone milestone = service.achieveMilestone(new ConstructionCommands.AchieveMilestone(projectId,
                milestoneId, request == null ? null : request.achievedOn(), actor, channel));
        return ApiResponse.ok(ConstructionResponses.MilestoneResponse.from(milestone));
    }

    @PostMapping("/{projectId}/contractors")
    @Operation(summary = "Assign a responsible contractor to the project")
    public ApiResponse<ConstructionResponses.ContractorAssignmentResponse> assignContractor(
            @PathVariable UUID projectId, @Valid @RequestBody ConstructionRequests.ContractorAssignmentRequest request,
            ActorContext actor, SourceChannel channel) {
        ProjectContractor assignment = service.assignContractor(new ConstructionCommands.AssignContractor(projectId,
                request.contractorId(), request.role(), actor, channel));
        return ApiResponse.ok(ConstructionResponses.ContractorAssignmentResponse.from(assignment));
    }

    @PostMapping("/{projectId}/permits")
    @Operation(summary = "Link an S164 permit to the project",
            description = "Recorded whatever S164 has said about it; counts toward the start gate only "
                    + "while S164's own events show it current for this work type.")
    public ApiResponse<ConstructionResponses.PermitLinkResponse> linkPermit(@PathVariable UUID projectId,
            @Valid @RequestBody ConstructionRequests.LinkPermit request, ActorContext actor, SourceChannel channel) {
        service.linkPermit(new ConstructionCommands.LinkPermit(projectId, request.permitId(), request.workType(),
                actor, channel));
        return ApiResponse.ok(service.permits(projectId, actor, channel).stream()
                .filter(linked -> linked.link().permitId().equals(request.permitId().strip()))
                .findFirst().map(ConstructionResponses.PermitLinkResponse::from).orElseThrow());
    }

    @GetMapping
    @Operation(summary = "Search construction projects",
            description = "Pipeline by stage - filter by siteCode and/or status.")
    public ApiResponse<PageResponse<ConstructionResponses.ProjectResponse>> search(
            @RequestParam(required = false) String siteCode, @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(PageResponse.from(service.search(siteCode, status, page, size, actor, channel),
                ConstructionResponses.ProjectResponse::from));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Read one project")
    public ApiResponse<ConstructionResponses.ProjectResponse> find(@PathVariable UUID projectId, ActorContext actor,
            SourceChannel channel) {
        return respond(service.find(projectId, actor, channel));
    }

    @GetMapping("/{projectId}/approvals")
    @Operation(summary = "The sign-off record(s) for a project")
    public ApiResponse<List<ConstructionResponses.ApprovalResponse>> approvals(@PathVariable UUID projectId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.approvals(projectId, actor, channel).stream()
                .map(ConstructionResponses.ApprovalResponse::from).toList());
    }

    @GetMapping("/{projectId}/milestones")
    @Operation(summary = "The project's current milestones")
    public ApiResponse<List<ConstructionResponses.MilestoneResponse>> milestones(@PathVariable UUID projectId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.milestones(projectId, actor, channel).stream()
                .map(ConstructionResponses.MilestoneResponse::from).toList());
    }

    @GetMapping("/{projectId}/contractors")
    @Operation(summary = "Contractors responsible for the project")
    public ApiResponse<List<ConstructionResponses.ContractorAssignmentResponse>> contractors(
            @PathVariable UUID projectId, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.contractors(projectId, actor, channel).stream()
                .map(ConstructionResponses.ContractorAssignmentResponse::from).toList());
    }

    @GetMapping("/{projectId}/permits")
    @Operation(summary = "Permits linked to the project and whether each is current")
    public ApiResponse<List<ConstructionResponses.PermitLinkResponse>> permits(@PathVariable UUID projectId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.permits(projectId, actor, channel).stream()
                .map(ConstructionResponses.PermitLinkResponse::from).toList());
    }

    @GetMapping("/{projectId}/permits/missing")
    @Operation(summary = "Permit-requiring work types with no current linked permit")
    public ApiResponse<Set<String>> missingPermits(@PathVariable UUID projectId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(service.missingPermits(projectId, actor, channel));
    }

    @GetMapping("/{projectId}/history")
    @Operation(summary = "Every version of the budget baseline and every milestone target",
            description = "SRS-SFL-S176-01: milestone dates and budget revisions are versioned, never "
                    + "overwritten. Revision 1 of each subject is its original value.")
    public ApiResponse<List<ConstructionResponses.RevisionResponse>> history(@PathVariable UUID projectId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.history(projectId, actor, channel).stream()
                .map(ConstructionResponses.RevisionResponse::from).toList());
    }

    @GetMapping("/{projectId}/budget")
    @Operation(summary = "The current budget, traceable to each approved variation",
            description = "SRS-SFL-S176-03: current budget = baseline + approved variations, broken "
                    + "down by variation record.")
    public ApiResponse<ConstructionResponses.BudgetResponse> budget(@PathVariable UUID projectId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(ConstructionResponses.BudgetResponse.from(
                variations.budget(projectId, actor, channel)));
    }

    private static List<ConstructionCommands.MilestoneSpec> milestones(
            List<ConstructionRequests.MilestoneRequest> requests) {
        return requests == null ? List.of() : requests.stream()
                .map(r -> new ConstructionCommands.MilestoneSpec(r.code(), r.name(), r.targetDate())).toList();
    }

    private static List<ConstructionCommands.ContractorSpec> contractors(
            List<ConstructionRequests.ContractorAssignmentRequest> requests) {
        return requests == null ? List.of() : requests.stream()
                .map(r -> new ConstructionCommands.ContractorSpec(r.contractorId(), r.role())).toList();
    }

    private ApiResponse<ConstructionResponses.ProjectResponse> respond(ConstructionProject project) {
        return ApiResponse.ok(ConstructionResponses.ProjectResponse.from(project));
    }
}
