package gh.edu.clet.sfl.facilities.spaceplanning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.ConstructionHandoffPort;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.SpacePlanningRepository;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioUncommittedException;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Space-change requests and their hand-off - SRS-SFL-S158-04.
 *
 * <p>"Informal space requests become a governed, visible pipeline instead of ad-hoc emails." Submitted by
 * any holder of {@code FACILITIES_SPACE_CHANGE_REQUEST} (a unit head holds it through
 * {@code IFIMP_REQUESTER}); decided by a holder of {@code FACILITIES_SPACE_CHANGE_DECIDE} who did not
 * submit it; actioned into a scenario or an S176 project; resolved only with that outcome linked.
 *
 * <h2>Whose requests a requester can see</h2>
 *
 * An actor holding neither {@code FACILITIES_SPACE_PLAN_READ} nor {@code FACILITIES_SPACE_CHANGE_DECIDE}
 * sees only the requests they submitted - the booking {@code requesterFilter} rule, keyed on authority
 * rather than on one role, so a facilities manager who also requests space still sees the pipeline they
 * decide on. Applied to reads by id as well as to lists.
 *
 * <h2>Who actions an approved request</h2>
 *
 * Either the planner ({@code FACILITIES_SPACE_PLAN_MANAGE}) or the decider ({@code _DECIDE}). The SRS
 * names no role for it; both are the people who would otherwise be forwarding the email.
 */
@Service
public class SpaceChangeRequestService {

    static final String EVENT_SUBMITTED = "sfl.ifimp.space-change-request-submitted.v1";
    static final String EVENT_DECIDED = "sfl.ifimp.space-change-request-decided.v1";
    static final String EVENT_RESOLVED = "sfl.ifimp.space-change-request-resolved.v1";

    private final SpacePlanningRepository repository;
    private final FacilitiesRepository facilities;
    private final SpaceScenarioService scenarios;
    private final ConstructionHandoffPort construction;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public SpaceChangeRequestService(SpacePlanningRepository repository, FacilitiesRepository facilities,
            SpaceScenarioService scenarios, ConstructionHandoffPort construction, FacilitiesAuthorization authorization,
            AuditPort audit, ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.facilities = facilities;
        this.scenarios = scenarios;
        this.construction = construction;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public SpaceChangeRequest submit(SpacePlanningCommands.SubmitRequest command) {
        ActorContext actor = command.actor();
        authorization.require(actor, SflPermission.FACILITIES_SPACE_CHANGE_REQUEST, command.siteCode(),
                command.channel(), "SpaceChangeRequest", "submit");
        String siteCode = command.siteCode().strip().toUpperCase(Locale.ROOT);
        facilities.findSiteByCode(siteCode)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Site", siteCode));
        FacilityRoom target = null;
        if (command.targetRoomId() != null) {
            target = facilities.findRoom(command.targetRoomId()).orElseThrow(
                    () -> new FacilitiesException.InvalidParentReferenceException("Space", command.targetRoomId()));
            if (!target.siteCode().equals(siteCode)) {
                throw new FacilitiesException.ValidationFailedException(target.roomCode() + " is registered at "
                        + target.siteCode() + ", not " + siteCode + ".");
            }
        }
        String reference = "SCR-" + siteCode + "-" + String.format(Locale.ROOT, "%06d", repository.nextRequestNumber());
        SpaceChangeRequest submitted = repository.saveRequest(SpaceChangeRequest.submit(UUID.randomUUID(), reference,
                siteCode, command.requestingUnit(), command.justification(), command.targetRoomId(),
                target == null ? null : target.roomCode(), command.targetAreaDescription(), command.requiredHeadcount(),
                command.urgency(), actor.actorId(), clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.SPACE_CHANGE_REQUEST_SUBMITTED, "SpaceChangeRequest",
                submitted.id().toString(), submitted.siteCode(), null, submitted);
        publish(EVENT_SUBMITTED, submitted, actor);
        return submitted;
    }

    /** Approve or decline. Not by the submitter - a unit head approving their own request is no review. */
    @Transactional
    public SpaceChangeRequest decide(SpacePlanningCommands.DecideRequest command) {
        ActorContext actor = command.actor();
        SpaceChangeRequest request = requireRequest(command.requestId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_CHANGE_DECIDE, request.siteCode(),
                command.channel(), "SpaceChangeRequest", request.id().toString());
        if (actor.actorId().equals(request.requestedBy())) {
            audit.recordDenial(actor, command.channel(), "SpaceChangeRequest", request.id().toString(),
                    request.siteCode(), "The submitter of a space-change request cannot decide it");
            throw new FacilitiesException.UnauthorizedApprovalException(
                    "A space-change request cannot be decided by the person who submitted it.");
        }
        request.metadata().requireVersion(command.expectedVersion(), "SpaceChangeRequest", request.id());
        SpaceChangeRequest decided = repository.saveRequest(request.decide(command.approve(), command.reason(),
                actor.actorId(), clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.SPACE_CHANGE_REQUEST_DECIDED, "SpaceChangeRequest",
                decided.id().toString(), decided.siteCode(), request, decided);
        publish(EVENT_DECIDED, decided, actor);
        return decided;
    }

    /** An approved like-for-like request becomes a scenario (S158-01), linked both ways. */
    @Transactional
    public SpaceChangeRequest linkScenario(SpacePlanningCommands.LinkScenario command) {
        ActorContext actor = command.actor();
        SpaceChangeRequest request = requireRequest(command.requestId());
        requireMayAction(actor, request, command.channel());
        AllocationScenario scenario = scenarios.requireScenario(command.scenarioId());
        if (!scenario.siteCode().equals(request.siteCode())) {
            throw new FacilitiesException.ValidationFailedException(
                    "A request can only be met by a scenario at its own site.");
        }
        if (scenario.status() == ScenarioStatus.DISCARDED) {
            throw new FacilitiesException.InvalidStateTransitionException("A discarded scenario cannot meet a request.");
        }
        Instant at = clock.instant();
        SpaceChangeRequest linked = repository.saveRequest(request.linkScenario(scenario.id(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        if (!request.id().equals(scenario.spaceChangeRequestId())) {
            repository.saveScenario(scenario.linkRequest(request.id(), actor.actorId(), at, command.channel(),
                    actor.correlationId()));
        }
        audit.record(actor, command.channel(), AuditAction.SPACE_CHANGE_REQUEST_LINKED, "SpaceChangeRequest",
                linked.id().toString(), linked.siteCode(), request, linked);
        return linked;
    }

    /**
     * An approved request that needs physical works is handed to S176 - the S158-04 acceptance criterion:
     * "an S176 project reference is created and linked back to the request."
     *
     * <p>Idempotent: a request already linked to a project returns as it is, and S176's intake is itself
     * keyed on the request id.
     */
    @Transactional
    public SpaceChangeRequest handToConstruction(SpacePlanningCommands.HandToConstruction command) {
        ActorContext actor = command.actor();
        SpaceChangeRequest request = requireRequest(command.requestId());
        requireMayAction(actor, request, command.channel());
        if (request.linkedProjectId() != null) {
            return request;
        }
        if (request.status() != SpaceChangeRequest.Status.APPROVED
                && request.status() != SpaceChangeRequest.Status.ACTIONED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only an approved request can be handed to S176; this one is " + request.status() + ".");
        }
        List<UUID> rooms = new ArrayList<>(command.roomIds());
        if (rooms.isEmpty() && request.targetRoomId() != null) {
            rooms.add(request.targetRoomId());
        }
        UUID committedScenario = null;
        if (request.linkedScenarioId() != null) {
            AllocationScenario linked = scenarios.requireScenario(request.linkedScenarioId());
            committedScenario = linked.status().isCommitted() ? linked.id() : null;
        }
        ConstructionHandoffPort.ProposedProject project;
        try {
            project = construction.propose(new ConstructionHandoffPort.Proposal(request.id(), request.siteCode(),
                    command.title() == null || command.title().isBlank()
                            ? "Space change " + request.reference() + " for " + request.requestingUnit()
                            : command.title().strip(),
                    command.scope() == null || command.scope().isBlank() ? request.targetAreaDescription()
                            : command.scope().strip(),
                    request.requestingUnit(), request.justification(), committedScenario, rooms, actor.actorId()));
        } catch (ConstructionHandoffPort.ConstructionUnavailableException unavailable) {
            throw new FacilitiesException(FacilitiesErrorCode.SPACE_CONSTRUCTION_INTAKE_UNAVAILABLE);
        }
        SpaceChangeRequest linked = repository.saveRequest(request.linkProject(project.projectId(),
                project.projectReference(), SpaceChangeRequest.OutcomeType.CONSTRUCTION_PROJECT, actor.actorId(),
                clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.SPACE_CHANGE_REQUEST_LINKED, "SpaceChangeRequest",
                linked.id().toString(), linked.siteCode(), request, linked);
        return linked;
    }

    /**
     * Resolves with a linked outcome, or refuses: "Unlinked Resolution" with none, and "Uncommitted
     * Scenario Referenced" when the only outcome is a scenario still in draft - a draft is not an outcome.
     */
    @Transactional(noRollbackFor = ScenarioUncommittedException.class)
    public SpaceChangeRequest resolve(SpacePlanningCommands.ResolveRequest command) {
        ActorContext actor = command.actor();
        SpaceChangeRequest request = requireRequest(command.requestId());
        requireMayAction(actor, request, command.channel());
        request.metadata().requireVersion(command.expectedVersion(), "SpaceChangeRequest", request.id());
        if (!request.hasLinkedOutcome()) {
            throw new FacilitiesException(FacilitiesErrorCode.SPACE_CHANGE_UNLINKED_RESOLUTION);
        }
        if (request.linkedProjectId() == null) {
            AllocationScenario scenario = scenarios.requireScenario(request.linkedScenarioId());
            if (scenario.status().isDraft()) {
                scenarios.refuse(scenario, "resolve " + request.reference() + " with it", actor, command.channel());
            }
            if (scenario.status() == ScenarioStatus.DISCARDED) {
                throw new FacilitiesException(FacilitiesErrorCode.SPACE_CHANGE_UNLINKED_RESOLUTION,
                        FacilitiesErrorCode.SPACE_CHANGE_UNLINKED_RESOLUTION.defaultMessage()
                                + " The linked scenario was discarded.");
            }
        }
        SpaceChangeRequest resolved = repository.saveRequest(request.resolve(command.note(), actor.actorId(),
                clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.SPACE_CHANGE_REQUEST_RESOLVED, "SpaceChangeRequest",
                resolved.id().toString(), resolved.siteCode(), request, resolved);
        publish(EVENT_RESOLVED, resolved, actor);
        return resolved;
    }

    @Transactional(readOnly = true)
    public List<SpaceChangeRequest> search(String siteCode, SpaceChangeRequest.Status status, ActorContext actor,
            SourceChannel channel) {
        requireMayRead(actor, siteCode, channel, "list");
        return authorization.filterBySite(actor, repository.findRequests(siteCode, requesterFilter(actor), status),
                SpaceChangeRequest::siteCode);
    }

    @Transactional(readOnly = true)
    public SpaceChangeRequest find(UUID id, ActorContext actor, SourceChannel channel) {
        SpaceChangeRequest request = requireRequest(id);
        requireMayRead(actor, request.siteCode(), channel, id.toString());
        String filter = requesterFilter(actor);
        if (filter != null && !filter.equals(request.requestedBy())) {
            audit.recordDenial(actor, channel, "SpaceChangeRequest", id.toString(), request.siteCode(),
                    "A requester may read only the space-change requests they submitted");
            throw new FacilitiesException.UnauthorizedScopeException(
                    "You may only view space-change requests you submitted.");
        }
        return request;
    }

    /** The {@code requestedBy} to narrow to, or {@code null} for the whole pipeline. */
    String requesterFilter(ActorContext actor) {
        boolean seesPipeline = authorization.has(actor, SflPermission.FACILITIES_SPACE_PLAN_READ)
                || authorization.has(actor, SflPermission.FACILITIES_SPACE_CHANGE_DECIDE);
        return seesPipeline ? null : actor.actorId();
    }

    private void requireMayRead(ActorContext actor, String siteCode, SourceChannel channel, String resourceId) {
        SflPermission permission = authorization.has(actor, SflPermission.FACILITIES_SPACE_PLAN_READ)
                ? SflPermission.FACILITIES_SPACE_PLAN_READ : SflPermission.FACILITIES_SPACE_CHANGE_REQUEST;
        authorization.require(actor, permission, siteCode, channel, "SpaceChangeRequest", resourceId);
    }

    private void requireMayAction(ActorContext actor, SpaceChangeRequest request, SourceChannel channel) {
        SflPermission permission = authorization.has(actor, SflPermission.FACILITIES_SPACE_CHANGE_DECIDE)
                ? SflPermission.FACILITIES_SPACE_CHANGE_DECIDE : SflPermission.FACILITIES_SPACE_PLAN_MANAGE;
        authorization.require(actor, permission, request.siteCode(), channel, "SpaceChangeRequest",
                request.id().toString());
    }

    private SpaceChangeRequest requireRequest(UUID id) {
        return repository.findRequest(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("SpaceChangeRequest", id));
    }

    /** References and classifications only: no justification, no unit name, no free text. */
    private void publish(String eventType, SpaceChangeRequest request, ActorContext actor) {
        outbox.record(eventType, 1, "SpaceChangeRequest", request.id(), request.siteCode(), actor.correlationId(),
                actor.actorId(), new RequestEvent(request.id(), request.reference(), request.siteCode(),
                        request.status(), request.urgency(), request.targetRoomId(), request.outcomeType(),
                        request.linkedScenarioId(), request.linkedProjectId(), request.linkedProjectReference(),
                        request.requestedBy(), request.decidedBy(), request.resolvedBy()));
    }

    record RequestEvent(UUID requestId, String reference, String siteCode, SpaceChangeRequest.Status status,
            SpaceChangeRequest.Urgency urgency, UUID targetRoomId, SpaceChangeRequest.OutcomeType outcomeType,
            UUID linkedScenarioId, UUID linkedProjectId, String linkedProjectReference, String requestedBy,
            String decidedBy, String resolvedBy) {
    }
}
