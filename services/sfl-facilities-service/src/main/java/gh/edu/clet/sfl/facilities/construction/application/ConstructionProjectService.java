package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.application.ports.EstateRegisterPort;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.Milestone;
import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectApproval;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectContractor;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectPermitLink;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectRevision;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.construction.domain.policy.ProjectStartPolicy;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The project register, milestones and approval gate - SRS-SFL-S176-01.
 *
 * <h2>The start gate, in the order it asks</h2>
 *
 * <ol>
 *   <li><strong>Permission, site and ownership.</strong> Refused and audited before anything is read.</li>
 *   <li><strong>Sign-off.</strong> A recorded approval by an accountable approver that covers the
 *       current baseline - {@code PROJECT_APPROVAL_MISSING} otherwise.</li>
 *   <li><strong>Permits.</strong> For every configured permit-requiring work type the project
 *       declares, a linked S164 permit that the S164 projection shows current -
 *       {@code PROJECT_PERMIT_MISSING} otherwise, naming the uncovered types.</li>
 *   <li><strong>A responsible contractor.</strong> Works with nobody responsible for them are not
 *       works anybody can be held to - {@code PROJECT_CONTRACTOR_UNASSIGNED}.</li>
 * </ol>
 *
 * <p>Every refusal is audited {@code PROJECT_START_REFUSED} with its reason, and survives the
 * refusal: the methods here do not roll back for {@link ConstructionRefusal}, which is only ever thrown
 * after the refusal is recorded and before any state is written.
 *
 * <h2>Separation of duties</h2>
 *
 * The approver must hold {@code FACILITIES_PROJECT_APPROVE} and must not be the project's manager -
 * administrators included. A project manager who can approve their own project has not been
 * approved by anybody.
 */
@Service
public class ConstructionProjectService {

    private final ConstructionContext context;
    private final ConstructionRepository repository;
    private final AuditPort audit;
    private final EstateRegisterPort estate;
    private final IdempotencyPort idempotency;

    public ConstructionProjectService(ConstructionContext context, EstateRegisterPort estate,
            IdempotencyPort idempotency) {
        this.context = context;
        this.repository = context.repository();
        this.audit = context.audit();
        this.estate = estate;
        this.idempotency = idempotency;
    }

    // =============================================================================================
    // Registration
    // =============================================================================================

    @Transactional
    public ConstructionProject register(ConstructionCommands.RegisterProject command) {
        ActorContext actor = command.actor();
        String siteCode = EstateCodes.normalize(command.siteCode());
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_MANAGE, siteCode, command.channel(),
                "ConstructionProject", "new");
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<ConstructionProject> replayed = idempotency.findExistingResult("register-construction-project",
                    command.idempotencyKey(), idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(repository::findProject);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        if (!estate.siteExists(siteCode)) {
            throw new FacilitiesException.InvalidParentReferenceException("Site", siteCode);
        }
        ConstructionProject.Definition definition = definition(siteCode, command.scope(), command.budgetBaseline(),
                command.currency(), command.fundingSourceReference(), command.fundingSourceName(),
                command.workTypes());
        requireMilestones(command.milestones());
        Instant at = context.now();
        String projectManager = command.projectManagerId() == null ? actor.actorId() : command.projectManagerId();
        ConstructionProject project = repository.saveProject(ConstructionProject.register(UUID.randomUUID(),
                repository.nextProjectReference(siteCode), siteCode, command.title(), definition, projectManager,
                actor.actorId(), at, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_REGISTERED, "ConstructionProject",
                project.id().toString(), project.siteCode(), null, project);
        defineBaselineAndMilestones(project, command.milestones(), command.contractors(), actor, command.channel(), at);
        publishRegistered(project, actor);
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            idempotency.recordResult("register-construction-project", command.idempotencyKey(),
                    idempotency.fingerprint(command.idempotencyPayload()), project.id(), project.siteCode(),
                    actor.actorId());
        }
        return project;
    }

    /** Defines a PROPOSED project - typically one S158 handed over - and moves it to REGISTERED. */
    @Transactional
    public ConstructionProject completeRegistration(ConstructionCommands.CompleteRegistration command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        project.metadata().requireVersion(command.expectedVersion(), "Construction project", project.id());
        ConstructionProject.Definition definition = definition(project.siteCode(),
                command.scope() == null ? project.scope() : command.scope(), command.budgetBaseline(),
                command.currency(), command.fundingSourceReference(), command.fundingSourceName(),
                command.workTypes());
        requireMilestones(command.milestones());
        Instant at = context.now();
        ConstructionProject registered = repository.saveProject(project.completeRegistration(definition,
                command.projectManagerId(), actor.actorId(), at, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_REGISTERED, "ConstructionProject",
                registered.id().toString(), registered.siteCode(), project, registered);
        defineBaselineAndMilestones(registered, command.milestones(), command.contractors(), actor,
                command.channel(), at);
        publishRegistered(registered, actor);
        return registered;
    }

    // =============================================================================================
    // Approval gate
    // =============================================================================================

    @Transactional
    public ConstructionProject approve(ConstructionCommands.ApproveProject command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_APPROVE, project.siteCode(),
                command.channel(), "ConstructionProject", project.id().toString());
        if (actor.actorId().equals(project.projectManagerId())) {
            audit.recordDenial(actor, command.channel(), "ConstructionProject", project.id().toString(),
                    project.siteCode(), "The accountable approver may not be the project's own manager");
            throw new FacilitiesException.UnauthorizedApprovalException(
                    "The accountable approver must be someone other than the project manager.");
        }
        project.metadata().requireVersion(command.expectedVersion(), "Construction project", project.id());
        if (project.status() != ProjectStatus.REGISTERED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only a registered project can be approved; " + project.projectReference() + " is "
                            + project.status() + ".");
        }
        Instant at = context.now();
        ProjectApproval approval = repository.saveApproval(new ProjectApproval(UUID.randomUUID(), project.id(),
                project.siteCode(), actor.actorId(), project.baselineRevision(), project.budgetBaseline(),
                project.currency(), command.note(), at,
                gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata.createdBy(actor.actorId(), at,
                        command.channel(), actor.correlationId())));
        ConstructionProject approved = repository.saveProject(project.approve(approval.id(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_APPROVED, "ConstructionProject",
                approved.id().toString(), approved.siteCode(), project, approval);
        context.publish(ConstructionEvents.PROJECT_APPROVED, "ConstructionProject", approved.id(),
                approved.siteCode(), actor, ConstructionEvents.payload("projectId", approved.id(),
                        "projectReference", approved.projectReference(), "approvalId", approval.id(),
                        "approverId", approval.approverId(), "baselineRevision", approval.baselineRevision(),
                        "baselineAmount", approval.baselineAmount(), "currency", approval.currency()));
        return approved;
    }

    @Transactional(noRollbackFor = ConstructionRefusal.class)
    public ConstructionProject start(ConstructionCommands.StartProject command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        project.metadata().requireVersion(command.expectedVersion(), "Construction project", project.id());
        if (!project.status().isPreStart()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Project " + project.projectReference() + " is already " + project.status() + ".");
        }
        Instant at = context.now();
        ProjectStartPolicy.Decision decision = ProjectStartPolicy.evaluate(project, currentApproval(project),
                context.configuration().permitRequiredWorkTypes(project.siteCode()), repository.findLinks(project.id()),
                permitsFor(repository.findLinks(project.id())), at);
        if (!decision.allowed()) {
            throw refuseStart(project, decision.refusal(), decision.reason(), actor, command.channel());
        }
        if (repository.findAssignments(project.id()).isEmpty()) {
            throw refuseStart(project, FacilitiesErrorCode.PROJECT_CONTRACTOR_UNASSIGNED,
                    "Assign the responsible contractor to " + project.projectReference() + " first.", actor,
                    command.channel());
        }
        ConstructionProject started = repository.saveProject(project.start(actor.actorId(), at, command.channel(),
                actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_STARTED, "ConstructionProject",
                started.id().toString(), started.siteCode(), project, started);
        context.publish(ConstructionEvents.PROJECT_STARTED, "ConstructionProject", started.id(), started.siteCode(),
                actor, ConstructionEvents.payload("projectId", started.id(), "projectReference",
                        started.projectReference(), "workTypes", started.workTypes(), "approvalId",
                        started.approvalId(), "startedAt", started.startedAt()));
        return started;
    }

    @Transactional
    public ConstructionProject cancel(ConstructionCommands.CancelProject command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        project.metadata().requireVersion(command.expectedVersion(), "Construction project", project.id());
        if (command.reason() == null || command.reason().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A cancellation must carry a reason.");
        }
        ConstructionProject cancelled = repository.saveProject(project.cancel(command.reason(), actor.actorId(),
                context.now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_CANCELLED, "ConstructionProject",
                cancelled.id().toString(), cancelled.siteCode(), project, cancelled);
        return cancelled;
    }

    // =============================================================================================
    // Versioned baseline and milestones
    // =============================================================================================

    @Transactional
    public ConstructionProject reviseBaseline(ConstructionCommands.ReviseBaseline command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        project.metadata().requireVersion(command.expectedVersion(), "Construction project", project.id());
        if (command.reason() == null || command.reason().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A baseline revision must record why.");
        }
        if (!repository.findVariations(project.id()).isEmpty()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Variation orders have been raised against this baseline; further budget change is by variation.");
        }
        Instant at = context.now();
        ConstructionProject revised = repository.saveProject(project.reviseBaseline(command.amount(),
                command.currency(), actor.actorId(), at, command.channel(), actor.correlationId()));
        repository.appendRevision(ProjectRevision.baseline(revised, command.reason(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_BASELINE_REVISED, "ConstructionProject",
                revised.id().toString(), revised.siteCode(), project, revised);
        return revised;
    }

    @Transactional
    public Milestone addMilestone(ConstructionCommands.AddMilestone command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        requireNotFinished(project);
        return createMilestone(project, command.milestone(), actor, command.channel(), context.now());
    }

    @Transactional
    public Milestone reviseMilestone(ConstructionCommands.ReviseMilestone command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        requireNotFinished(project);
        Milestone milestone = requireMilestone(project, command.milestoneId());
        milestone.metadata().requireVersion(command.expectedVersion(), "Milestone", milestone.id());
        if (command.reason() == null || command.reason().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A milestone revision must record why.");
        }
        Instant at = context.now();
        Milestone revised = repository.saveMilestone(milestone.revise(command.targetDate(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        repository.appendRevision(ProjectRevision.milestone(revised, command.reason(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_MILESTONE_REVISED, "Milestone",
                revised.id().toString(), revised.siteCode(), milestone, revised);
        return revised;
    }

    @Transactional
    public Milestone achieveMilestone(ConstructionCommands.AchieveMilestone command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        Milestone milestone = requireMilestone(project, command.milestoneId());
        Milestone achieved = repository.saveMilestone(milestone.achieve(
                command.achievedOn() == null ? context.today() : command.achievedOn(), actor.actorId(),
                context.now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_MILESTONE_ACHIEVED, "Milestone",
                achieved.id().toString(), achieved.siteCode(), milestone, achieved);
        return achieved;
    }

    // =============================================================================================
    // Contractors and permits on a project
    // =============================================================================================

    @Transactional
    public ProjectContractor assignContractor(ConstructionCommands.AssignContractor command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        requireNotFinished(project);
        return assign(project, command.contractorId(), command.role(), actor, command.channel(), context.now());
    }

    /**
     * Links an S164 permit by reference. Recorded whatever S164 has said about it - the link is the
     * project manager's claim - and counted by the start gate only while the projection shows it current.
     */
    @Transactional
    public ProjectPermitLink linkPermit(ConstructionCommands.LinkPermit command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        requireNotFinished(project);
        EstateCodes.require(command.permitId(), "permitId");
        String workType = EstateCodes.normalize(command.workType());
        if (!project.workTypes().contains(workType)) {
            throw new FacilitiesException.ValidationFailedException(
                    "Project " + project.projectReference() + " declares no " + workType + " work.");
        }
        boolean duplicate = repository.findLinks(project.id()).stream()
                .anyMatch(link -> link.permitId().equals(command.permitId().strip()));
        if (duplicate) {
            throw new FacilitiesException.DuplicateIdentifierException("permit link", command.permitId(),
                    project.siteCode());
        }
        ProjectPermitLink link = repository.saveLink(ProjectPermitLink.link(project, command.permitId(), workType,
                actor.actorId(), context.now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_PERMIT_LINKED, "ConstructionProject",
                project.id().toString(), project.siteCode(), null, link);
        return link;
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public RepositoryPage<ConstructionProject> search(String siteCode, ProjectStatus status, int page, int size,
            ActorContext actor, SourceChannel channel) {
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_READ, channel, "ConstructionProject",
                "list", siteCode);
        context.authorization().requireRequestedSite(actor, siteCode, channel, "ConstructionProject");
        RepositoryPage<ConstructionProject> found = repository.searchProjects(new ConstructionRepository.ProjectQuery(
                siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode), status, page, size));
        List<ConstructionProject> visible = context.authorization().filterBySite(actor, found.items(),
                ConstructionProject::siteCode);
        return visible.size() == found.items().size() ? found
                : RepositoryPage.of(visible, visible.size(), found.page(), found.size());
    }

    @Transactional(readOnly = true)
    public ConstructionProject find(UUID projectId, ActorContext actor, SourceChannel channel) {
        ConstructionProject project = context.requireProject(projectId);
        context.requireRead(actor, project.siteCode(), channel, "ConstructionProject", projectId.toString());
        return project;
    }

    @Transactional(readOnly = true)
    public List<ProjectApproval> approvals(UUID projectId, ActorContext actor, SourceChannel channel) {
        return repository.findApprovals(find(projectId, actor, channel).id());
    }

    @Transactional(readOnly = true)
    public List<Milestone> milestones(UUID projectId, ActorContext actor, SourceChannel channel) {
        return repository.findMilestones(find(projectId, actor, channel).id());
    }

    @Transactional(readOnly = true)
    public List<ProjectContractor> contractors(UUID projectId, ActorContext actor, SourceChannel channel) {
        return repository.findAssignments(find(projectId, actor, channel).id());
    }

    /** Every version of the baseline and every milestone target, oldest first (S176-01). */
    @Transactional(readOnly = true)
    public List<ProjectRevision> history(UUID projectId, ActorContext actor, SourceChannel channel) {
        return repository.findRevisions(find(projectId, actor, channel).id()).stream()
                .sorted(Comparator.comparing(ProjectRevision::subject).thenComparing(ProjectRevision::subjectCode)
                        .thenComparingInt(ProjectRevision::revision))
                .toList();
    }

    /** A link, the projection row for its permit if S164 has said anything, and whether it is current. */
    public record LinkedPermit(ProjectPermitLink link, PermitRecord permit, boolean current) {
    }

    @Transactional(readOnly = true)
    public List<LinkedPermit> permits(UUID projectId, ActorContext actor, SourceChannel channel) {
        ConstructionProject project = find(projectId, actor, channel);
        List<ProjectPermitLink> links = repository.findLinks(project.id());
        Map<String, PermitRecord> permits = permitsFor(links);
        Instant now = context.now();
        return links.stream().map(link -> new LinkedPermit(link, permits.get(link.permitId()),
                link.isSatisfiedBy(permits.get(link.permitId()), now))).toList();
    }

    /** The permit-requiring work types the project still lacks a current permit for. */
    @Transactional(readOnly = true)
    public Set<String> missingPermits(UUID projectId, ActorContext actor, SourceChannel channel) {
        ConstructionProject project = find(projectId, actor, channel);
        List<ProjectPermitLink> links = repository.findLinks(project.id());
        return ProjectStartPolicy.missingPermits(project.workTypes(),
                context.configuration().permitRequiredWorkTypes(project.siteCode()), links, permitsFor(links),
                context.now());
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    Optional<ProjectApproval> currentApproval(ConstructionProject project) {
        return repository.findApprovals(project.id()).stream()
                .filter(approval -> approval.id().equals(project.approvalId()))
                .findFirst();
    }

    private Map<String, PermitRecord> permitsFor(List<ProjectPermitLink> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        return repository.findPermits(links.stream().map(ProjectPermitLink::permitId).toList()).stream()
                .collect(Collectors.toMap(PermitRecord::permitId, Function.identity(), (a, b) -> a));
    }

    private ConstructionRefusal refuseStart(ConstructionProject project, FacilitiesErrorCode code, String reason,
            ActorContext actor, SourceChannel channel) {
        audit.record(actor, channel, AuditAction.PROJECT_START_REFUSED, "ConstructionProject",
                project.id().toString(), project.siteCode(), project.status(),
                Map.of("code", code.name(), "reason", reason));
        return new ConstructionRefusal(code, reason);
    }

    private ConstructionProject.Definition definition(String siteCode, String scope, java.math.BigDecimal baseline,
            String currency, String fundingReference, String fundingName, List<String> workTypes) {
        ConstructionProject.Definition definition = new ConstructionProject.Definition(scope, baseline, currency,
                fundingReference, fundingName, workTypes);
        Set<String> allowed = context.configuration().workTypes(siteCode);
        Set<String> unknown = new HashSet<>();
        for (String type : workTypes) {
            if (type == null || type.isBlank() || !allowed.contains(EstateCodes.normalize(type))) {
                unknown.add(String.valueOf(type));
            }
        }
        if (!unknown.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException("Unknown work type(s) " + unknown
                    + "; the configured catalogue is " + new java.util.TreeSet<>(allowed) + ".");
        }
        return definition;
    }

    private static void requireMilestones(List<ConstructionCommands.MilestoneSpec> milestones) {
        if (milestones == null || milestones.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException("A project must record its target milestones.");
        }
        long distinct = milestones.stream().map(spec -> EstateCodes.normalize(spec.code())).distinct().count();
        if (distinct != milestones.size()) {
            throw new FacilitiesException.ValidationFailedException("Milestone codes must be unique within a project.");
        }
    }

    private void defineBaselineAndMilestones(ConstructionProject project,
            List<ConstructionCommands.MilestoneSpec> milestones, List<ConstructionCommands.ContractorSpec> contractors,
            ActorContext actor, SourceChannel channel, Instant at) {
        repository.appendRevision(ProjectRevision.baseline(project, null, actor.actorId(), at, channel,
                actor.correlationId()));
        Set<String> existing = repository.findMilestones(project.id()).stream().map(Milestone::milestoneCode)
                .collect(Collectors.toSet());
        for (ConstructionCommands.MilestoneSpec spec : milestones) {
            if (!existing.contains(EstateCodes.normalize(spec.code()))) {
                createMilestone(project, spec, actor, channel, at);
            }
        }
        if (contractors != null) {
            for (ConstructionCommands.ContractorSpec spec : contractors) {
                assign(project, spec.contractorId(), spec.role(), actor, channel, at);
            }
        }
    }

    private Milestone createMilestone(ConstructionProject project, ConstructionCommands.MilestoneSpec spec,
            ActorContext actor, SourceChannel channel, Instant at) {
        if (spec == null || spec.targetDate() == null) {
            throw new FacilitiesException.ValidationFailedException("A milestone must have a target date.");
        }
        String code = EstateCodes.normalize(spec.code());
        if (repository.findMilestones(project.id()).stream().anyMatch(m -> m.milestoneCode().equals(code))) {
            throw new FacilitiesException.DuplicateIdentifierException("milestone", code, project.siteCode());
        }
        Milestone milestone = repository.saveMilestone(Milestone.create(UUID.randomUUID(), project.id(),
                project.siteCode(), code, spec.name(), spec.targetDate(), actor.actorId(), at, channel,
                actor.correlationId()));
        repository.appendRevision(ProjectRevision.milestone(milestone, null, actor.actorId(), at, channel,
                actor.correlationId()));
        audit.record(actor, channel, AuditAction.PROJECT_UPDATED, "Milestone", milestone.id().toString(),
                milestone.siteCode(), null, milestone);
        return milestone;
    }

    private ProjectContractor assign(ConstructionProject project, UUID contractorId, ProjectContractor.Role role,
            ActorContext actor, SourceChannel channel, Instant at) {
        Contractor contractor = repository.findContractor(contractorId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Contractor", contractorId));
        if (!contractor.siteCode().equals(project.siteCode())) {
            throw new FacilitiesException.ValidationFailedException("Contractor " + contractor.contractorCode()
                    + " is registered at " + contractor.siteCode() + ", not " + project.siteCode() + ".");
        }
        Optional<ProjectContractor> existing = repository.findAssignments(project.id()).stream()
                .filter(assignment -> assignment.contractorId().equals(contractorId)).findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        ProjectContractor assignment = repository.saveAssignment(ProjectContractor.assign(project, contractor, role,
                actor.actorId(), at, channel, actor.correlationId()));
        audit.record(actor, channel, AuditAction.PROJECT_CONTRACTOR_ASSIGNED, "ConstructionProject",
                project.id().toString(), project.siteCode(), null, assignment);
        return assignment;
    }

    private Milestone requireMilestone(ConstructionProject project, UUID milestoneId) {
        Milestone milestone = repository.findMilestone(milestoneId)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Milestone", milestoneId));
        if (!milestone.projectId().equals(project.id())) {
            throw new FacilitiesException.RecordNotFoundException("Milestone", milestoneId);
        }
        return milestone;
    }

    private static void requireNotFinished(ConstructionProject project) {
        if (project.status().isTerminal() || project.status() == ProjectStatus.HANDED_OVER) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Project " + project.projectReference() + " is " + project.status() + ".");
        }
    }

    private void publishRegistered(ConstructionProject project, ActorContext actor) {
        context.publish(ConstructionEvents.PROJECT_REGISTERED, "ConstructionProject", project.id(), project.siteCode(),
                actor, ConstructionEvents.payload("projectId", project.id(), "projectReference",
                        project.projectReference(), "status", project.status(), "origin", project.origin(),
                        "workTypes", project.workTypes(), "budgetBaseline", project.budgetBaseline(), "currency",
                        project.currency(), "fundingSourceReference", project.fundingSourceReference(),
                        "spaceChangeRequestId", project.spaceChangeRequestId(), "committedScenarioId",
                        project.committedScenarioId()));
    }
}
