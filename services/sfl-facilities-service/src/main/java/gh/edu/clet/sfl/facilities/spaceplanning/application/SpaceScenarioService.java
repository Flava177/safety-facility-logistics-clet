package gh.edu.clet.sfl.facilities.spaceplanning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.SpaceAllocationService;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
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
import gh.edu.clet.sfl.facilities.spaceplanning.domain.CommitOutcome;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ComplianceStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyOverride;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.RoomCompliance;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioAllocation;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioUncommittedException;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UnitHeadcount;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocation scenarios - SRS-SFL-S158-01.
 *
 * <p>"Officer creates scenario against current register -> models allocation/occupancy -> compares
 * scenarios -> commits chosen scenario -> triggers S152 update or S176 project."
 *
 * <h2>The rule the whole class exists to hold</h2>
 *
 * A draft never becomes current state by any route but {@link #commit}. Concretely:
 * <ul>
 *   <li>No method here writes to the S152 register except {@link #commit} (like-for-like) and
 *       {@link #confirmHandover} (physical works, at S176 handover), and both only for a committed
 *       scenario.</li>
 *   <li>Every operational path that could be handed a draft id and read it as current -
 *       {@link #applyToRegister}, {@link #currentAllocations}, S176's {@code confirmHandover}, and S158-04
 *       resolution - refuses it with {@code SPACE_SCENARIO_UNCOMMITTED} and audits the attempt as
 *       {@code SPACE_SCENARIO_USE_AS_CURRENT_REFUSED}. Those methods declare {@code noRollbackFor} the
 *       refusal so its audit record survives; the check runs before anything is written, so nothing else
 *       can be committed alongside it.</li>
 * </ul>
 * So the acceptance criterion - two drafts, neither committed, S152 unaffected - holds by construction,
 * and {@code S158MandatoryScenariosTest} proves it against the register.
 *
 * <h2>The commit</h2>
 *
 * Explicit and named: it requires {@code FACILITIES_SPACE_PLAN_COMMIT}, records who, when and which
 * outcome on the scenario itself, is audited and published. Like-for-like applies the allocations to
 * S152 in the same transaction; physical works proposes an S176 project in the same transaction and waits
 * for S176 to hand over. Non-compliant rooms do not block a commit (S158-02 "flagged, not blocked") - the
 * commit's audit record and event list them.
 */
@Service
public class SpaceScenarioService {

    static final String EVENT_COMMITTED = "sfl.ifimp.space-scenario-committed.v1";
    static final String EVENT_HANDED_OVER = "sfl.ifimp.space-scenario-handed-over.v1";

    private final SpacePlanningRepository repository;
    private final FacilitiesRepository facilities;
    private final SpaceAllocationService register;
    private final OccupancyStandardService standards;
    private final ConstructionHandoffPort construction;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public SpaceScenarioService(SpacePlanningRepository repository, FacilitiesRepository facilities,
            SpaceAllocationService register, OccupancyStandardService standards, ConstructionHandoffPort construction,
            FacilitiesAuthorization authorization, AuditPort audit, ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.facilities = facilities;
        this.register = register;
        this.standards = standards;
        this.construction = construction;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    // =============================================================================================
    // Modelling
    // =============================================================================================

    @Transactional
    public AllocationScenario create(SpacePlanningCommands.CreateScenario command) {
        ActorContext actor = command.actor();
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_MANAGE, command.siteCode(),
                command.channel(), "AllocationScenario", "create");
        String siteCode = command.siteCode().strip().toUpperCase(Locale.ROOT);
        facilities.findSiteByCode(siteCode)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Site", siteCode));
        if (command.spaceChangeRequestId() != null) {
            SpaceChangeRequest request = repository.findRequest(command.spaceChangeRequestId()).orElseThrow(
                    () -> new FacilitiesException.InvalidParentReferenceException("SpaceChangeRequest",
                            command.spaceChangeRequestId()));
            if (!request.siteCode().equals(siteCode)) {
                throw new FacilitiesException.ValidationFailedException(
                        "A scenario can only answer a space-change request at its own site.");
            }
        }
        String reference = "SP-" + siteCode + "-" + String.format(Locale.ROOT, "%06d", repository.nextPlanNumber());
        AllocationScenario created = repository.saveScenario(AllocationScenario.create(UUID.randomUUID(), siteCode,
                reference, command.name(), command.description(), command.spaceChangeRequestId(), actor.actorId(),
                clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.SPACE_SCENARIO_CREATED, "AllocationScenario",
                created.id().toString(), created.siteCode(), null, created);
        return created;
    }

    /** The next version of a plan, as a new draft carrying the source version's allocations. */
    @Transactional
    public AllocationScenario revise(SpacePlanningCommands.ReviseScenario command) {
        ActorContext actor = command.actor();
        AllocationScenario source = requireScenario(command.scenarioId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_MANAGE, source.siteCode(),
                command.channel(), "AllocationScenario", source.id().toString());
        Instant at = clock.instant();
        AllocationScenario revised = repository.saveScenario(source.revise(UUID.randomUUID(),
                repository.maxVersionOfPlan(source.planReference()) + 1, command.name(), command.description(),
                actor.actorId(), at, command.channel(), actor.correlationId()));
        Map<UUID, List<ScenarioAllocation>> byRoom = linesByRoom(repository.findLines(source.id()));
        for (Map.Entry<UUID, List<ScenarioAllocation>> room : byRoom.entrySet()) {
            FacilityRoom space = requireRoom(room.getKey());
            RoomCompliance compliance = standards.evaluate(space, OccupancyStandardService.totalHeadcount(room.getValue()));
            for (ScenarioAllocation line : room.getValue()) {
                repository.saveLine(ScenarioAllocation.line(UUID.randomUUID(), revised.id(), revised.siteCode(),
                        space.id(), space.roomCode(), line.allocatedUnit(), line.headcount(), compliance,
                        actor.actorId(), at, command.channel(), actor.correlationId()));
            }
        }
        audit.record(actor, command.channel(), AuditAction.SPACE_SCENARIO_CREATED, "AllocationScenario",
                revised.id().toString(), revised.siteCode(), source, revised);
        return revised;
    }

    @Transactional
    public AllocationScenario rename(SpacePlanningCommands.RenameScenario command) {
        ActorContext actor = command.actor();
        AllocationScenario scenario = requireScenario(command.scenarioId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_MANAGE, scenario.siteCode(),
                command.channel(), "AllocationScenario", scenario.id().toString());
        scenario.metadata().requireVersion(command.expectedVersion(), "AllocationScenario", scenario.id());
        AllocationScenario renamed = repository.saveScenario(scenario.rename(command.name(), command.description(),
                actor.actorId(), clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.SPACE_SCENARIO_UPDATED, "AllocationScenario",
                renamed.id().toString(), renamed.siteCode(), scenario, renamed);
        return renamed;
    }

    /**
     * Sets one room's allocation in a draft, replacing whatever the draft had for it. An empty unit list
     * vacates the room in the scenario.
     *
     * <p>Compliance is computed here - "allocation recorded or changed -> checked against occupancy
     * standard" - and a non-compliant result is saved and returned flagged, never refused (S158-02). Any
     * override for the room is withdrawn: it approved a different allocation.
     */
    @Transactional
    public RoomPlan setRoomAllocation(SpacePlanningCommands.SetRoomAllocation command) {
        ActorContext actor = command.actor();
        AllocationScenario scenario = requireScenario(command.scenarioId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_MANAGE, scenario.siteCode(),
                command.channel(), "AllocationScenario", scenario.id().toString());
        scenario.metadata().requireVersion(command.expectedVersion(), "AllocationScenario", scenario.id());
        FacilityRoom room = requireRoom(command.roomId());
        requireAllocatable(scenario, room);
        Instant at = clock.instant();
        AllocationScenario changed = scenario.allocationsChanged(actor.actorId(), at, command.channel(),
                actor.correlationId());

        List<ScenarioAllocation> before = linesFor(scenario.id(), room.id());
        repository.deleteLinesForRoom(scenario.id(), room.id());
        Map<String, Integer> merged = merge(command.units());
        RoomCompliance compliance = standards.evaluate(room, merged.values().stream().mapToInt(Integer::intValue).sum());
        List<ScenarioAllocation> after = new ArrayList<>();
        if (merged.isEmpty()) {
            after.add(repository.saveLine(ScenarioAllocation.line(UUID.randomUUID(), scenario.id(),
                    scenario.siteCode(), room.id(), room.roomCode(), null, 0, compliance, actor.actorId(), at,
                    command.channel(), actor.correlationId())));
        }
        for (Map.Entry<String, Integer> unit : merged.entrySet()) {
            after.add(repository.saveLine(ScenarioAllocation.line(UUID.randomUUID(), scenario.id(),
                    scenario.siteCode(), room.id(), room.roomCode(), unit.getKey(), unit.getValue(), compliance,
                    actor.actorId(), at, command.channel(), actor.correlationId())));
        }
        standards.withdrawForRoom(scenario.id(), room.id(), actor, command.channel());
        AllocationScenario saved = repository.saveScenario(changed);
        audit.record(actor, command.channel(), AuditAction.SPACE_SCENARIO_ALLOCATION_CHANGED, "AllocationScenario",
                scenario.id().toString(), scenario.siteCode(), describe(before), new AllocationChange(room.roomCode(),
                        describe(after), compliance.status(), compliance.reasonCode(), compliance.flagged()));
        return new RoomPlan(saved, room.id(), room.roomCode(), after, compliance);
    }

    @Transactional
    public AllocationScenario removeRoom(SpacePlanningCommands.RemoveRoom command) {
        ActorContext actor = command.actor();
        AllocationScenario scenario = requireScenario(command.scenarioId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_MANAGE, scenario.siteCode(),
                command.channel(), "AllocationScenario", scenario.id().toString());
        scenario.metadata().requireVersion(command.expectedVersion(), "AllocationScenario", scenario.id());
        List<ScenarioAllocation> before = linesFor(scenario.id(), command.roomId());
        AllocationScenario changed = scenario.allocationsChanged(actor.actorId(), clock.instant(), command.channel(),
                actor.correlationId());
        repository.deleteLinesForRoom(scenario.id(), command.roomId());
        standards.withdrawForRoom(scenario.id(), command.roomId(), actor, command.channel());
        AllocationScenario saved = repository.saveScenario(changed);
        audit.record(actor, command.channel(), AuditAction.SPACE_SCENARIO_ALLOCATION_CHANGED, "AllocationScenario",
                scenario.id().toString(), scenario.siteCode(), describe(before), "room removed from scenario");
        return saved;
    }

    @Transactional
    public AllocationScenario discard(SpacePlanningCommands.DiscardScenario command) {
        ActorContext actor = command.actor();
        AllocationScenario scenario = requireScenario(command.scenarioId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_MANAGE, scenario.siteCode(),
                command.channel(), "AllocationScenario", scenario.id().toString());
        scenario.metadata().requireVersion(command.expectedVersion(), "AllocationScenario", scenario.id());
        AllocationScenario discarded = repository.saveScenario(scenario.discard(command.reason(), actor.actorId(),
                clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.SPACE_SCENARIO_DISCARDED, "AllocationScenario",
                discarded.id().toString(), discarded.siteCode(), scenario, discarded);
        return discarded;
    }

    // =============================================================================================
    // Commit and handover
    // =============================================================================================

    /** The explicit, audited, named commit - SRS-SFL-S158-01 validation. */
    @Transactional
    public CommitResult commit(SpacePlanningCommands.CommitScenario command) {
        ActorContext actor = command.actor();
        AllocationScenario scenario = requireScenario(command.scenarioId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_COMMIT, scenario.siteCode(),
                command.channel(), "AllocationScenario", scenario.id().toString());
        scenario.metadata().requireVersion(command.expectedVersion(), "AllocationScenario", scenario.id());
        if (command.outcome() == null) {
            throw new FacilitiesException.ValidationFailedException(
                    "A commit must state its outcome: LIKE_FOR_LIKE or PHYSICAL_WORKS.");
        }
        if (!scenario.status().isDraft()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only a draft can be committed; this scenario is " + scenario.status() + ".");
        }
        List<ScenarioAllocation> lines = repository.findLines(scenario.id());
        if (lines.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException("A scenario with no allocations cannot be committed.");
        }
        Instant at = clock.instant();

        // Re-checked at commit, not trusted from the last save: the standard may have been revised since,
        // and the commit's record should say what was true when the decision was taken.
        List<RoomCompliance> compliance = new ArrayList<>();
        for (Map.Entry<UUID, List<ScenarioAllocation>> room : linesByRoom(lines).entrySet()) {
            FacilityRoom space = requireRoom(room.getKey());
            requireAllocatable(scenario, space);
            RoomCompliance evaluated = withOverrides(scenario.id(),
                    standards.evaluate(space, OccupancyStandardService.totalHeadcount(room.getValue())));
            compliance.add(evaluated);
            for (ScenarioAllocation line : room.getValue()) {
                repository.saveLine(line.restamp(evaluated, actor.actorId(), at, command.channel(),
                        actor.correlationId()));
            }
        }

        AllocationScenario committed = scenario.commit(command.outcome(), command.note(), actor.actorId(), at,
                command.channel(), actor.correlationId());
        SpaceAllocationService.AppliedChange applied = null;
        if (command.outcome() == CommitOutcome.LIKE_FOR_LIKE) {
            applied = applyToS152(committed, lines, actor, command.channel());
            committed = committed.markApplied(at);
        } else {
            ConstructionHandoffPort.ProposedProject project = propose(committed, lines, command.projectTitle(),
                    command.projectScope(), actor);
            committed = committed.linkProject(project.projectId(), project.projectReference());
        }
        AllocationScenario saved = repository.saveScenario(committed);
        List<String> flagged = compliance.stream().filter(RoomCompliance::flagged).map(RoomCompliance::roomCode).toList();
        audit.record(actor, command.channel(), AuditAction.SPACE_SCENARIO_COMMITTED, "AllocationScenario",
                saved.id().toString(), saved.siteCode(), scenario, new CommitRecord(saved.displayReference(),
                        saved.commitOutcome(), saved.committedBy(), saved.committedAt(), saved.linkedProjectReference(),
                        flagged));
        outbox.record(EVENT_COMMITTED, 1, "AllocationScenario", saved.id(), saved.siteCode(), actor.correlationId(),
                actor.actorId(), new CommittedEvent(saved.id(), saved.planReference(), saved.versionNumber(),
                        saved.siteCode(), saved.commitOutcome(), saved.committedBy(), saved.committedAt(),
                        roomIds(lines), flagged.size(), saved.linkedProjectId(), saved.linkedProjectReference(),
                        saved.spaceChangeRequestId()));
        linkRequestToProject(saved, actor, command.channel());
        return new CommitResult(saved, compliance, applied);
    }

    /**
     * S176's handover confirmation, through {@code ScenarioHandover} - SRS-SFL-S176-04 / S158-01.
     *
     * <p>Idempotent: a scenario already handed over for this project is returned unchanged, so a retried
     * S176 handover does no harm. A different project, or a like-for-like scenario (never waiting for
     * works), is refused as an invalid transition. A draft is refused as "Uncommitted Scenario
     * Referenced" - S176 treating a draft as the plan it realised is exactly the error state.
     */
    @Transactional(noRollbackFor = ScenarioUncommittedException.class)
    public AllocationScenario confirmHandover(UUID scenarioId, UUID projectId, String projectReference,
            ActorContext actor) {
        AllocationScenario scenario = requireScenario(scenarioId);
        SourceChannel channel = SourceChannel.INTEGRATION;
        if (scenario.status().isDraft()) {
            refuse(scenario, "confirm an S176 handover against it", actor, channel);
        }
        if (scenario.status() == ScenarioStatus.HANDED_OVER) {
            if (Objects.equals(scenario.linkedProjectId(), projectId)) {
                return scenario;
            }
            throw new FacilitiesException.InvalidStateTransitionException(
                    "This scenario was handed over under a different S176 project.");
        }
        if (!scenario.awaitingHandover()) {
            throw new FacilitiesException.InvalidStateTransitionException("A " + scenario.status() + " "
                    + scenario.commitOutcome() + " scenario is not waiting for an S176 handover.");
        }
        if (scenario.linkedProjectId() != null && !scenario.linkedProjectId().equals(projectId)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "This scenario is waiting for S176 project " + scenario.linkedProjectReference()
                            + ", not the one confirming it.");
        }
        List<ScenarioAllocation> lines = repository.findLines(scenario.id());
        applyToS152(scenario, lines, actor, channel);
        Instant at = clock.instant();
        AllocationScenario handedOver = scenario.linkProject(projectId,
                projectReference == null ? scenario.linkedProjectReference() : projectReference)
                .handOver(actor.actorId(), at, channel, actor.correlationId());
        AllocationScenario saved = repository.saveScenario(handedOver);
        audit.record(actor, channel, AuditAction.SPACE_SCENARIO_HANDOVER_CONFIRMED, "AllocationScenario",
                saved.id().toString(), saved.siteCode(), scenario, saved);
        outbox.record(EVENT_HANDED_OVER, 1, "AllocationScenario", saved.id(), saved.siteCode(), actor.correlationId(),
                actor.actorId(), new HandedOverEvent(saved.id(), saved.planReference(), saved.versionNumber(),
                        saved.siteCode(), saved.linkedProjectId(), saved.linkedProjectReference(), saved.handedOverBy(),
                        saved.handedOverAt(), roomIds(lines)));
        return saved;
    }

    /**
     * An explicit "apply this scenario to the register" - the operational path that must never accept a
     * draft. Idempotent for a committed like-for-like scenario (it was applied at commit); a physical-works
     * scenario is applied at S176 handover and nowhere else.
     */
    @Transactional(noRollbackFor = ScenarioUncommittedException.class)
    public List<SpaceAllocation> applyToRegister(UUID scenarioId, ActorContext actor, SourceChannel channel) {
        AllocationScenario scenario = requireScenario(scenarioId);
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_COMMIT, scenario.siteCode(), channel,
                "AllocationScenario", scenario.id().toString());
        if (scenario.status().isDraft()) {
            refuse(scenario, "apply it to the S152 register", actor, channel);
        }
        if (scenario.status() == ScenarioStatus.DISCARDED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A discarded scenario is never applied to the register.");
        }
        if (scenario.awaitingHandover()) {
            throw new FacilitiesException.InvalidStateTransitionException("A physical-works scenario is applied "
                    + "when S176 confirms handover of " + scenario.linkedProjectReference() + ", not before.");
        }
        return register.currentFromScenario(scenario.siteCode(), scenario.id(), actor, channel);
    }

    /**
     * The S152 current-state register at a site, or the part of it a given committed scenario put there.
     * Handed a draft id, it refuses rather than answering with the draft's allocations.
     */
    @Transactional(readOnly = false, noRollbackFor = ScenarioUncommittedException.class)
    public List<SpaceAllocation> currentAllocations(String siteCode, UUID scenarioId, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_READ, siteCode, channel, "SpaceAllocation",
                scenarioId == null ? "list" : scenarioId.toString());
        if (scenarioId == null) {
            return register.current(siteCode, actor, channel);
        }
        AllocationScenario scenario = requireScenario(scenarioId);
        authorization.requireSite(actor, scenario.siteCode(), channel, "AllocationScenario", scenario.id().toString());
        if (!scenario.status().isCommitted()) {
            if (scenario.status().isDraft()) {
                refuse(scenario, "read it as current allocation", actor, channel);
            }
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A discarded scenario has no current allocations.");
        }
        return register.currentFromScenario(scenario.siteCode(), scenario.id(), actor, channel);
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public AllocationScenario find(UUID id, ActorContext actor, SourceChannel channel) {
        AllocationScenario scenario = requireScenario(id);
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_READ, scenario.siteCode(), channel,
                "AllocationScenario", id.toString());
        return scenario;
    }

    @Transactional(readOnly = true)
    public List<AllocationScenario> search(String siteCode, ScenarioStatus status, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_READ, siteCode, channel,
                "AllocationScenario", "list");
        return authorization.filterBySite(actor, repository.findScenarios(siteCode, status),
                AllocationScenario::siteCode);
    }

    @Transactional(readOnly = true)
    public List<ScenarioAllocation> lines(UUID scenarioId, ActorContext actor, SourceChannel channel) {
        return repository.findLines(find(scenarioId, actor, channel).id());
    }

    /** Compliance per room in the scenario, computed now against the active standards. */
    @Transactional(readOnly = true)
    public List<RoomCompliance> compliance(UUID scenarioId, ActorContext actor, SourceChannel channel) {
        AllocationScenario scenario = find(scenarioId, actor, channel);
        return complianceOf(scenario, repository.findLines(scenario.id()));
    }

    /**
     * Compares scenarios room by room, against the current register and against each other - "Multiple
     * scenarios can exist concurrently for comparison."
     *
     * <p>A room a scenario does not mention is shown as unchanged in it, which is what not mentioning a
     * room means. Scenarios must be at one site: a comparison across sites compares nothing.
     */
    @Transactional(readOnly = true)
    public ScenarioComparison compare(List<UUID> scenarioIds, ActorContext actor, SourceChannel channel) {
        Set<UUID> ids = new LinkedHashSet<>(scenarioIds == null ? List.of() : scenarioIds);
        if (ids.isEmpty() || ids.size() > 10) {
            throw new FacilitiesException.ValidationFailedException("Compare between one and ten scenarios.");
        }
        List<AllocationScenario> scenarios = new ArrayList<>();
        for (UUID id : ids) {
            scenarios.add(find(id, actor, channel));
        }
        String siteCode = scenarios.get(0).siteCode();
        if (scenarios.stream().anyMatch(scenario -> !scenario.siteCode().equals(siteCode))) {
            throw new FacilitiesException.ValidationFailedException("Scenarios can only be compared within one site.");
        }
        Map<UUID, List<ScenarioAllocation>> linesByScenario = new LinkedHashMap<>();
        Set<UUID> roomIds = new LinkedHashSet<>();
        for (AllocationScenario scenario : scenarios) {
            List<ScenarioAllocation> lines = repository.findLines(scenario.id());
            linesByScenario.put(scenario.id(), lines);
            lines.forEach(line -> roomIds.add(line.roomId()));
        }
        Map<UUID, List<SpaceAllocation>> currentByRoom = register.current(siteCode, actor, channel).stream()
                .collect(Collectors.groupingBy(SpaceAllocation::roomId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, Map<UUID, RoomCompliance>> complianceByScenario = new LinkedHashMap<>();
        for (AllocationScenario scenario : scenarios) {
            Map<UUID, RoomCompliance> byRoom = new LinkedHashMap<>();
            complianceOf(scenario, linesByScenario.get(scenario.id())).forEach(c -> byRoom.put(c.roomId(), c));
            complianceByScenario.put(scenario.id(), byRoom);
        }

        List<RoomComparison> rooms = new ArrayList<>();
        for (UUID roomId : roomIds) {
            FacilityRoom room = requireRoom(roomId);
            Map<String, Integer> current = currentUnits(currentByRoom.getOrDefault(roomId, List.of()));
            List<RoomInScenario> planned = new ArrayList<>();
            Set<Map<String, Integer>> distinct = new LinkedHashSet<>();
            for (AllocationScenario scenario : scenarios) {
                List<ScenarioAllocation> lines = linesByScenario.get(scenario.id()).stream()
                        .filter(line -> line.roomId().equals(roomId)).toList();
                boolean covers = !lines.isEmpty();
                Map<String, Integer> units = covers ? scenarioUnits(lines) : current;
                distinct.add(units);
                RoomCompliance compliance = complianceByScenario.get(scenario.id()).get(roomId);
                planned.add(new RoomInScenario(scenario.id(), covers, toList(units), !units.equals(current),
                        compliance == null ? null : compliance.status(), compliance != null && compliance.flagged()));
            }
            rooms.add(new RoomComparison(roomId, room.roomCode(), room.spaceType().name(), room.capacity(),
                    toList(current), planned, distinct.size() == 1));
        }

        List<ScenarioTotals> totals = new ArrayList<>();
        for (AllocationScenario scenario : scenarios) {
            Map<UUID, RoomCompliance> byRoom = complianceByScenario.get(scenario.id());
            int changed = (int) rooms.stream().flatMap(room -> room.scenarios().stream())
                    .filter(entry -> entry.scenarioId().equals(scenario.id()) && entry.differsFromCurrent()).count();
            totals.add(new ScenarioTotals(scenario.id(), scenario.displayReference(), scenario.name(), scenario.status(),
                    OccupancyStandardService.totalHeadcount(linesByScenario.get(scenario.id())), changed,
                    (int) byRoom.values().stream().filter(RoomCompliance::flagged).count(),
                    (int) byRoom.values().stream().filter(c -> c.status() == ComplianceStatus.NOT_EVALUATED).count()));
        }
        return new ScenarioComparison(siteCode, totals, rooms);
    }

    // =============================================================================================
    // Internals shared with the other S158 services
    // =============================================================================================

    /**
     * Audits and throws "Uncommitted Scenario Referenced". The caller's method declares
     * {@code noRollbackFor} {@link ScenarioUncommittedException} and calls this before writing anything.
     */
    void refuse(AllocationScenario scenario, String attempted, ActorContext actor, SourceChannel channel) {
        audit.record(actor, channel, AuditAction.SPACE_SCENARIO_USE_AS_CURRENT_REFUSED, "AllocationScenario",
                scenario.id().toString(), scenario.siteCode(), null,
                new Refusal(scenario.displayReference(), scenario.status(), attempted,
                        FacilitiesErrorCode.SPACE_SCENARIO_UNCOMMITTED.name()));
        throw new ScenarioUncommittedException(scenario.displayReference() + " is a draft; cannot " + attempted + ".");
    }

    List<RoomCompliance> complianceOf(AllocationScenario scenario, List<ScenarioAllocation> lines) {
        List<RoomCompliance> result = new ArrayList<>();
        for (Map.Entry<UUID, List<ScenarioAllocation>> room : linesByRoom(lines).entrySet()) {
            FacilityRoom space = requireRoom(room.getKey());
            result.add(withOverrides(scenario.id(),
                    standards.evaluate(space, OccupancyStandardService.totalHeadcount(room.getValue()))));
        }
        return result;
    }

    AllocationScenario requireScenario(UUID id) {
        return repository.findScenario(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("AllocationScenario", id));
    }

    private RoomCompliance withOverrides(UUID scenarioId, RoomCompliance compliance) {
        boolean approved = repository.findOverrides(scenarioId).stream()
                .anyMatch(override -> override.roomId().equals(compliance.roomId())
                        && override.status() == OccupancyOverride.OverrideStatus.APPROVED);
        return compliance.withOverride(approved);
    }

    private SpaceAllocationService.AppliedChange applyToS152(AllocationScenario scenario,
            List<ScenarioAllocation> lines, ActorContext actor, SourceChannel channel) {
        List<SpaceAllocationService.RoomChange> rooms = new ArrayList<>();
        for (Map.Entry<UUID, List<ScenarioAllocation>> room : linesByRoom(lines).entrySet()) {
            rooms.add(new SpaceAllocationService.RoomChange(room.getKey(), room.getValue().stream()
                    .filter(line -> !line.isVacate())
                    .map(line -> new SpaceAllocationService.UnitHeadcount(line.allocatedUnit(), line.headcount()))
                    .toList()));
        }
        return register.applyCommittedChange(new SpaceAllocationService.ApplyCommittedChange(scenario.siteCode(),
                scenario.id(), scenario.displayReference(), rooms, actor, channel));
    }

    private ConstructionHandoffPort.ProposedProject propose(AllocationScenario scenario, List<ScenarioAllocation> lines,
            String title, String scope, ActorContext actor) {
        SpaceChangeRequest request = scenario.spaceChangeRequestId() == null ? null
                : repository.findRequest(scenario.spaceChangeRequestId()).orElse(null);
        try {
            return construction.propose(new ConstructionHandoffPort.Proposal(scenario.spaceChangeRequestId(),
                    scenario.siteCode(), title == null || title.isBlank() ? scenario.name() : title.strip(),
                    scope == null || scope.isBlank() ? "Physical works to realise space plan "
                            + scenario.displayReference() : scope.strip(),
                    request == null ? null : request.requestingUnit(),
                    request == null ? scenario.description() : request.justification(), scenario.id(),
                    List.copyOf(roomIds(lines)), actor.actorId()));
        } catch (ConstructionHandoffPort.ConstructionUnavailableException unavailable) {
            throw new FacilitiesException(FacilitiesErrorCode.SPACE_CONSTRUCTION_INTAKE_UNAVAILABLE);
        }
    }

    /** A physical-works commit of a scenario that answers a request links the project back to the request. */
    private void linkRequestToProject(AllocationScenario scenario, ActorContext actor, SourceChannel channel) {
        if (scenario.spaceChangeRequestId() == null || scenario.linkedProjectId() == null) {
            return;
        }
        Optional<SpaceChangeRequest> found = repository.findRequest(scenario.spaceChangeRequestId());
        if (found.isEmpty() || found.get().linkedProjectId() != null) {
            return;
        }
        SpaceChangeRequest request = found.get();
        if (request.status() != SpaceChangeRequest.Status.APPROVED && request.status() != SpaceChangeRequest.Status.ACTIONED) {
            return;
        }
        SpaceChangeRequest linked = repository.saveRequest(request.linkProject(scenario.linkedProjectId(),
                scenario.linkedProjectReference(), SpaceChangeRequest.OutcomeType.SCENARIO, actor.actorId(),
                clock.instant(), channel, actor.correlationId()));
        audit.record(actor, channel, AuditAction.SPACE_CHANGE_REQUEST_LINKED, "SpaceChangeRequest",
                linked.id().toString(), linked.siteCode(), request, linked);
    }

    private void requireAllocatable(AllocationScenario scenario, FacilityRoom room) {
        if (!room.siteCode().equals(scenario.siteCode())) {
            throw new FacilitiesException.ValidationFailedException(room.roomCode() + " is registered at "
                    + room.siteCode() + " and cannot be planned in a " + scenario.siteCode() + " scenario.");
        }
        if (!room.lifecycleStatus().isOperational()) {
            throw new FacilitiesException.ValidationFailedException(
                    room.roomCode() + " is " + room.lifecycleStatus() + " and cannot be allocated.");
        }
    }

    private FacilityRoom requireRoom(UUID roomId) {
        return facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
    }

    private List<ScenarioAllocation> linesFor(UUID scenarioId, UUID roomId) {
        return repository.findLines(scenarioId).stream().filter(line -> line.roomId().equals(roomId)).toList();
    }

    private static Map<UUID, List<ScenarioAllocation>> linesByRoom(List<ScenarioAllocation> lines) {
        return lines.stream().collect(Collectors.groupingBy(ScenarioAllocation::roomId, LinkedHashMap::new,
                Collectors.toList()));
    }

    private static Set<UUID> roomIds(List<ScenarioAllocation> lines) {
        return lines.stream().map(ScenarioAllocation::roomId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<String, Integer> merge(List<UnitHeadcount> units) {
        Map<String, Integer> merged = new TreeMap<>();
        units.forEach(unit -> merged.merge(unit.allocatedUnit(), unit.headcount(), Integer::sum));
        return merged;
    }

    private static Map<String, Integer> currentUnits(List<SpaceAllocation> rows) {
        Map<String, Integer> units = new TreeMap<>();
        rows.forEach(row -> units.merge(row.allocatedUnit(), row.headcount(), Integer::sum));
        return units;
    }

    private static Map<String, Integer> scenarioUnits(List<ScenarioAllocation> lines) {
        Map<String, Integer> units = new TreeMap<>();
        lines.stream().filter(line -> !line.isVacate())
                .forEach(line -> units.merge(line.allocatedUnit(), line.headcount(), Integer::sum));
        return units;
    }

    private static List<UnitHeadcount> toList(Map<String, Integer> units) {
        return units.entrySet().stream().map(entry -> new UnitHeadcount(entry.getKey(), entry.getValue())).toList();
    }

    private static List<String> describe(List<ScenarioAllocation> lines) {
        return lines.stream().map(line -> (line.isVacate() ? "(vacant)" : line.allocatedUnit()) + "=" + line.headcount())
                .toList();
    }

    // =============================================================================================
    // Results
    // =============================================================================================

    public record RoomPlan(AllocationScenario scenario, UUID roomId, String roomCode, List<ScenarioAllocation> lines,
            RoomCompliance compliance) {
    }

    /** @param applied the S152 register change, for a like-for-like commit; {@code null} for physical works */
    public record CommitResult(AllocationScenario scenario, List<RoomCompliance> compliance,
            SpaceAllocationService.AppliedChange applied) {
    }

    public record ScenarioComparison(String siteCode, List<ScenarioTotals> scenarios, List<RoomComparison> rooms) {
    }

    public record ScenarioTotals(UUID scenarioId, String reference, String name, ScenarioStatus status,
            int totalHeadcount, int roomsChangedFromCurrent, int flaggedRooms, int notEvaluatedRooms) {
    }

    /** @param scenariosAgree every compared scenario plans the same units for this room */
    public record RoomComparison(UUID roomId, String roomCode, String spaceType, Integer capacity,
            List<UnitHeadcount> current, List<RoomInScenario> scenarios, boolean scenariosAgree) {
    }

    /** @param covers the scenario mentions this room; when it does not, {@code planned} is the current state */
    public record RoomInScenario(UUID scenarioId, boolean covers, List<UnitHeadcount> planned,
            boolean differsFromCurrent, ComplianceStatus compliance, boolean flagged) {
    }

    record AllocationChange(String roomCode, List<String> lines, ComplianceStatus compliance, String reasonCode,
            boolean flagged) {
    }

    record CommitRecord(String reference, CommitOutcome outcome, String committedBy, Instant committedAt,
            String projectReference, List<String> flaggedRooms) {
    }

    record Refusal(String reference, ScenarioStatus status, String attempted, String errorCode) {
    }

    record CommittedEvent(UUID scenarioId, String planReference, int versionNumber, String siteCode,
            CommitOutcome outcome, String committedBy, Instant committedAt, Set<UUID> roomIds, int flaggedRooms,
            UUID projectId, String projectReference, UUID spaceChangeRequestId) {
    }

    record HandedOverEvent(UUID scenarioId, String planReference, int versionNumber, String siteCode, UUID projectId,
            String projectReference, String confirmedBy, Instant handedOverAt, Set<UUID> roomIds) {
    }
}
