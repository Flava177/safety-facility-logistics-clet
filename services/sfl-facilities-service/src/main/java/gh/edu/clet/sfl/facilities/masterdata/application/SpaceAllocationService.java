package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.SpaceAllocationRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The S152 current-state allocation register - read it, and apply a committed change to it.
 *
 * <p>Added to S152 for SRS-SFL-S158-01: "a scenario is committed explicitly, at which point it becomes
 * the basis for ... a direct S152 update for a like-for-like reassignment." S152 is the authoritative
 * register; S158 models against it and may only change it by committing.
 *
 * <h2>Why there is no "edit an allocation" method</h2>
 *
 * The only write is {@link #applyCommittedChange}, and it requires a source scenario reference. A
 * register anybody could edit directly would drift from the plans and nobody could say which plan put
 * a unit where it is. That this service cannot itself verify the scenario is committed is a real
 * limit - S152 does not depend on S158, by design - so the gate that refuses a draft lives in S158
 * ({@code SpaceScenarioService}), and {@code SpacePlanningArchitectureTest} holds that nothing outside
 * S152 and S158's register adapter calls this method.
 *
 * <p>Authorised by {@link SflPermission#FACILITIES_SPACE_PLAN_COMMIT}: changing who occupies a space is
 * the commit decision, and the facilities roles holding {@code FACILITIES_SPACE_MANAGE} (editing a
 * room's attributes) were not granted the authority to reorganise units.
 */
@Service
public class SpaceAllocationService {

    static final String EVENT_APPLIED = "sfl.ifimp.space-allocation-applied.v1";

    private final SpaceAllocationRepository allocations;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public SpaceAllocationService(SpaceAllocationRepository allocations, FacilitiesRepository facilities,
            FacilitiesAuthorization authorization, AuditPort audit, ServiceOutbox outbox, Clock clock) {
        this.allocations = allocations;
        this.facilities = facilities;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Every current allocation at a site. */
    @Transactional(readOnly = true)
    public List<SpaceAllocation> current(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, siteCode, channel, "SpaceAllocation",
                "list");
        return authorization.filterBySite(actor, allocations.findCurrentForSite(siteCode),
                SpaceAllocation::siteCode);
    }

    @Transactional(readOnly = true)
    public List<SpaceAllocation> currentForRoom(UUID roomId, ActorContext actor, SourceChannel channel) {
        FacilityRoom room = requireRoom(roomId);
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, room.siteCode(), channel,
                "SpaceAllocation", roomId.toString());
        return allocations.findCurrentForRoom(roomId);
    }

    /** The current allocations a committed scenario put in place. */
    @Transactional(readOnly = true)
    public List<SpaceAllocation> currentFromScenario(String siteCode, UUID scenarioId, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, siteCode, channel, "SpaceAllocation",
                scenarioId.toString());
        return allocations.findCurrentFromScenario(scenarioId).stream()
                .filter(allocation -> allocation.siteCode().equals(siteCode))
                .toList();
    }

    /**
     * Applies a committed scenario's allocations to the register.
     *
     * <p>For each room the change names, every current allocation is ended and the new ones inserted,
     * in one transaction - a room is never briefly empty or double-occupied in the register. A room the
     * change does not name is untouched. Idempotent per scenario: a scenario that has already touched
     * the register returns what it put there and changes nothing, so a retried commit or a repeated
     * S176 handover cannot apply the same plan twice.
     */
    @Transactional
    public AppliedChange applyCommittedChange(ApplyCommittedChange command) {
        Objects.requireNonNull(command, "command is required");
        ActorContext actor = command.actor();
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_COMMIT, command.siteCode(),
                command.channel(), "SpaceAllocation", command.sourceScenarioId().toString());
        if (allocations.anyTouchedByScenario(command.sourceScenarioId())) {
            return new AppliedChange(command.sourceScenarioId(), true,
                    allocations.findCurrentFromScenario(command.sourceScenarioId()), List.of());
        }
        if (command.rooms().isEmpty()) {
            throw new FacilitiesException.ValidationFailedException(
                    "A committed change must name at least one space.");
        }

        Instant at = clock.instant();
        List<SpaceAllocation> started = new ArrayList<>();
        List<SpaceAllocation> ended = new ArrayList<>();
        for (RoomChange change : command.rooms()) {
            FacilityRoom room = requireRoom(change.roomId());
            if (!room.siteCode().equals(command.siteCode())) {
                throw new FacilitiesException.ValidationFailedException(room.roomCode() + " is registered at "
                        + room.siteCode() + " and cannot be allocated from a " + command.siteCode() + " scenario.");
            }
            if (!room.lifecycleStatus().isOperational()) {
                throw new FacilitiesException.ValidationFailedException(room.roomCode() + " is "
                        + room.lifecycleStatus() + " and can no longer be allocated.");
            }
            List<SpaceAllocation> before = allocations.findCurrentForRoom(room.id());
            List<SpaceAllocation> after = new ArrayList<>();
            for (SpaceAllocation current : before) {
                ended.add(allocations.save(current.end(command.sourceScenarioId(), actor.actorId(), at,
                        command.channel(), actor.correlationId())));
            }
            for (Map.Entry<String, Integer> unit : merge(change.units()).entrySet()) {
                SpaceAllocation allocation = allocations.save(SpaceAllocation.allocate(UUID.randomUUID(), room,
                        unit.getKey(), unit.getValue(), command.sourceScenarioId(), command.sourceReference(),
                        actor.actorId(), at, command.channel(), actor.correlationId()));
                after.add(allocation);
                started.add(allocation);
            }
            audit.record(actor, command.channel(), AuditAction.SPACE_ALLOCATION_APPLIED, "SpaceAllocation",
                    room.id().toString(), room.siteCode(), describe(before), describe(after));
        }
        Set<UUID> roomIds = new LinkedHashSet<>();
        command.rooms().forEach(change -> roomIds.add(change.roomId()));
        outbox.record(EVENT_APPLIED, 1, "SpaceAllocationChange", command.sourceScenarioId(), command.siteCode(),
                actor.correlationId(), actor.actorId(), new AppliedEvent(command.sourceScenarioId(),
                        command.sourceReference(), command.siteCode(), List.copyOf(roomIds), started.size(),
                        ended.size(), at));
        return new AppliedChange(command.sourceScenarioId(), false, List.copyOf(started), List.copyOf(ended));
    }

    /**
     * One unit listed twice for the same room is one allocation with the headcounts added - two rows
     * for the same unit in the same room would be two answers to one question.
     */
    private static Map<String, Integer> merge(List<UnitHeadcount> units) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (UnitHeadcount unit : units) {
            if (unit.allocatedUnit() == null || unit.allocatedUnit().isBlank()) {
                continue;
            }
            merged.merge(unit.allocatedUnit().strip(), Math.max(0, unit.headcount()), Integer::sum);
        }
        return merged;
    }

    private static List<String> describe(List<SpaceAllocation> rows) {
        return rows.stream().map(row -> row.allocatedUnit() + "=" + row.headcount()).toList();
    }

    private FacilityRoom requireRoom(UUID roomId) {
        return facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
    }

    /** One unit's headcount in one room. A room listed with no units is vacated by the change. */
    public record UnitHeadcount(String allocatedUnit, int headcount) {
    }

    public record RoomChange(UUID roomId, List<UnitHeadcount> units) {
        public RoomChange {
            Objects.requireNonNull(roomId, "roomId is required");
            units = units == null ? List.of() : List.copyOf(units);
        }
    }

    public record ApplyCommittedChange(String siteCode, UUID sourceScenarioId, String sourceReference,
            List<RoomChange> rooms, ActorContext actor, SourceChannel channel) {
        public ApplyCommittedChange {
            Objects.requireNonNull(sourceScenarioId, "a committed change always names its source scenario");
            Objects.requireNonNull(actor, "actor is required");
            rooms = rooms == null ? List.of() : List.copyOf(rooms);
            channel = channel == null ? SourceChannel.SYSTEM : channel;
        }
    }

    /** @param alreadyApplied the scenario had touched the register before; nothing changed this time */
    public record AppliedChange(UUID sourceScenarioId, boolean alreadyApplied, List<SpaceAllocation> started,
            List<SpaceAllocation> ended) {
    }

    /** The event payload: references and counts only - unit names are organisational, not personal. */
    record AppliedEvent(UUID scenarioId, String scenarioReference, String siteCode, List<UUID> roomIds,
            int allocationsStarted, int allocationsEnded, Instant appliedAt) {
    }
}
