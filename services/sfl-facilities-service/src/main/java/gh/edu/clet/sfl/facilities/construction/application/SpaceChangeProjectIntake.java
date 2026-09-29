package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.construction.application.contract.ConstructionProjectIntake;
import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.application.ports.EstateRegisterPort;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S176's side of the S158 hand-off - the provider of {@link ConstructionProjectIntake}
 * (SRS-SFL-S158-04: "an approved request ... is handed to Construction Project Management (S176) when
 * physical works are required").
 *
 * <h2>A proposal, never a shortcut</h2>
 *
 * The project is created PROPOSED: a reference, a scope, the S158 origin and the spaces it will
 * touch - and no budget, no approval, no start. Every S176 gate still applies, in the same order, to a
 * project that arrived this way: a project manager registers it (baseline, funding, milestones, work
 * types), an accountable approver who is not that manager signs it off, permits are linked where the
 * work needs them, and only then can it start. S158 having approved the space change is S158's
 * decision about space; it is not a sign-off on cost or on safety.
 *
 * <h2>Who it is recorded against</h2>
 *
 * A platform service account, as {@code AutomatedWorkOrderIntake} does for S153: S158 has already
 * authorised its own officer for its own act, and that officer does not hold an S176 permission and
 * should not need one to create a reference. The requester is kept as {@code registeredBy} so the
 * audit trail answers "who asked for this".
 *
 * <h2>Replay</h2>
 *
 * Idempotent on the space-change request id - V21 has a unique index on it. S158 retrying the hand-off
 * gets the same project back, never a second one.
 */
@Service
public class SpaceChangeProjectIntake implements ConstructionProjectIntake {

    static final SiteScopedPrincipal PLATFORM = new SiteScopedPrincipal("system.construction-intake",
            "S176 construction intake", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final ConstructionContext context;
    private final ConstructionRepository repository;
    private final EstateRegisterPort estate;

    public SpaceChangeProjectIntake(ConstructionContext context, EstateRegisterPort estate) {
        this.context = context;
        this.repository = context.repository();
        this.estate = estate;
    }

    @Override
    @Transactional
    public ProposedProject proposeFromSpaceChange(SpaceChangeProposal proposal) {
        Objects.requireNonNull(proposal, "proposal is required");
        if (proposal.spaceChangeRequestId() == null) {
            throw new FacilitiesException.ValidationFailedException("A proposal must carry its space-change request id.");
        }
        Optional<ConstructionProject> existing = repository.findProjectBySpaceChange(proposal.spaceChangeRequestId());
        if (existing.isPresent()) {
            return summary(existing.get());
        }
        String siteCode = EstateCodes.normalize(proposal.siteCode());
        if (!estate.siteExists(siteCode)) {
            throw new FacilitiesException.InvalidParentReferenceException("Site", siteCode);
        }
        List<UUID> rooms = proposal.roomIds() == null ? List.of() : List.copyOf(proposal.roomIds());
        for (UUID roomId : rooms) {
            EstateRegisterPort.RoomView room = estate.findRoom(roomId)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
            if (!room.siteCode().equals(siteCode)) {
                throw new FacilitiesException.ValidationFailedException("Space " + room.roomCode()
                        + " is at " + room.siteCode() + ", not " + siteCode + ".");
            }
        }
        ActorContext platform = new ActorContext(PLATFORM, "s158-intake:" + proposal.spaceChangeRequestId());
        ConstructionProject project = repository.saveProject(ConstructionProject.propose(UUID.randomUUID(),
                repository.nextProjectReference(siteCode), siteCode, proposal.title(), proposal.scope(),
                proposal.spaceChangeRequestId(), proposal.committedScenarioId(), rooms, proposal.requestingUnit(),
                proposal.justification(), proposal.requestedBy(), platform.actorId(), context.now(),
                SourceChannel.INTEGRATION, platform.correlationId()));
        context.audit().record(platform, SourceChannel.INTEGRATION, AuditAction.PROJECT_REGISTERED,
                "ConstructionProject", project.id().toString(), project.siteCode(), null, project);
        context.publish(ConstructionEvents.PROJECT_REGISTERED, "ConstructionProject", project.id(),
                project.siteCode(), platform, ConstructionEvents.payload("projectId", project.id(),
                        "projectReference", project.projectReference(), "status", project.status(), "origin",
                        project.origin(), "spaceChangeRequestId", project.spaceChangeRequestId(),
                        "committedScenarioId", project.committedScenarioId()));
        return summary(project);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProposedProject> find(UUID projectId) {
        return repository.findProject(projectId).map(SpaceChangeProjectIntake::summary);
    }

    private static ProposedProject summary(ConstructionProject project) {
        return new ProposedProject(project.id(), project.projectReference(), project.status().name());
    }
}
