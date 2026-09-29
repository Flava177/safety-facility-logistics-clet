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
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.SpacePlanningRepository;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ComplianceStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyOverride;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyStandard;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.RoomCompliance;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioAllocation;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.policy.OccupancyCompliancePolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Occupancy standards, compliance and overrides - SRS-SFL-S158-02.
 *
 * <p>"Allocation recorded or changed -> checked against occupancy standard -> compliance status computed
 * -> flagged if non-compliant -> override recorded with reason if accepted." The check itself is
 * {@link OccupancyCompliancePolicy}; this class finds the standard that applies, records overrides, and
 * decides who may approve one.
 *
 * <h2>Why a non-holder's approval is SPACE_OVERRIDE_INCOMPLETE and not a plain 403</h2>
 *
 * The SRS phrases the rule as a property of the override - it "must carry ... an accountable approver" -
 * so an approval by somebody who cannot be accountable for it leaves the override incomplete, and that is
 * the code the planner's screen needs in order to say what is missing. The refusal is still audited as a
 * denial, exactly as a permission refusal would be.
 */
@Service
public class OccupancyStandardService {

    static final String EVENT_OVERRIDE_RECORDED = "sfl.ifimp.occupancy-override-recorded.v1";

    private final SpacePlanningRepository repository;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public OccupancyStandardService(SpacePlanningRepository repository, FacilitiesRepository facilities,
            FacilitiesAuthorization authorization, AuditPort audit, ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.facilities = facilities;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Defines the next version of a space type's standard, superseding the active one. */
    @Transactional
    public OccupancyStandard define(SpacePlanningCommands.DefineStandard command) {
        ActorContext actor = command.actor();
        authorization.require(actor, SflPermission.FACILITIES_OCCUPANCY_STANDARD_MANAGE, command.siteCode(),
                command.channel(), "OccupancyStandard", String.valueOf(command.spaceType()));
        if (command.spaceType() == null) {
            throw new FacilitiesException.ValidationFailedException("A standard applies to a space type.");
        }
        facilities.findSiteByCode(command.siteCode()).orElseThrow(
                () -> new FacilitiesException.InvalidParentReferenceException("Site", command.siteCode()));
        Instant at = clock.instant();
        OccupancyStandard previous = repository.findActiveStandard(command.siteCode(), command.spaceType())
                .orElse(null);
        if (previous != null) {
            repository.saveStandard(previous.supersede(actor.actorId(), at, command.channel(), actor.correlationId()));
        }
        OccupancyStandard created = repository.saveStandard(OccupancyStandard.define(UUID.randomUUID(),
                command.siteCode(), command.spaceType(),
                repository.maxStandardVersion(command.siteCode(), command.spaceType()) + 1,
                command.maxCapacityPercent(), command.minAreaPerPersonSqm(), command.note(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.OCCUPANCY_STANDARD_VERSION_CREATED, "OccupancyStandard",
                created.id().toString(), created.siteCode(), previous, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<OccupancyStandard> standards(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_READ, siteCode, channel,
                "OccupancyStandard", "list");
        return authorization.filterBySite(actor, repository.findStandards(siteCode), OccupancyStandard::siteCode);
    }

    /**
     * The first step of an override: the planner asks, with the reason (S158-02 validation).
     *
     * <p>Only for a room that is actually non-compliant in the scenario. An override of a compliant room
     * would be a standing exemption waiting for the allocation to grow into it.
     */
    @Transactional
    public OccupancyOverride requestOverride(SpacePlanningCommands.RequestOverride command) {
        ActorContext actor = command.actor();
        AllocationScenario scenario = requireScenario(command.scenarioId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_MANAGE, scenario.siteCode(),
                command.channel(), "OccupancyOverride", scenario.id().toString());
        if (scenario.status() == ScenarioStatus.DISCARDED || scenario.status() == ScenarioStatus.HANDED_OVER) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "An override cannot be requested on a " + scenario.status() + " scenario.");
        }
        List<ScenarioAllocation> lines = repository.findLines(scenario.id()).stream()
                .filter(line -> line.roomId().equals(command.roomId())).toList();
        if (lines.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException("The scenario does not allocate that space.");
        }
        FacilityRoom room = requireRoom(command.roomId());
        RoomCompliance compliance = evaluate(room, totalHeadcount(lines));
        if (compliance.status() != ComplianceStatus.NON_COMPLIANT) {
            throw new FacilitiesException.ValidationFailedException(room.roomCode() + " is "
                    + compliance.status() + " in this scenario; there is nothing to override.");
        }
        boolean alreadyLive = repository.findOverrides(scenario.id()).stream()
                .anyMatch(existing -> existing.roomId().equals(room.id()) && existing.isLive());
        if (alreadyLive) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "An override for " + room.roomCode() + " in this scenario is already pending or approved.");
        }
        OccupancyOverride requested = repository.saveOverride(OccupancyOverride.request(UUID.randomUUID(),
                scenario.siteCode(), scenario.id(), room.id(), room.roomCode(), compliance.totalHeadcount(),
                compliance.summary(), command.reason(), actor.actorId(), clock.instant(), command.channel(),
                actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.OCCUPANCY_OVERRIDE_REQUESTED, "OccupancyOverride",
                requested.id().toString(), requested.siteCode(), null, requested);
        return requested;
    }

    /** The second step: an accountable approver, who is not the requester, records the override. */
    @Transactional
    public OccupancyOverride approveOverride(SpacePlanningCommands.ApproveOverride command) {
        ActorContext actor = command.actor();
        OccupancyOverride pending = repository.findOverride(command.overrideId()).orElseThrow(
                () -> new FacilitiesException.RecordNotFoundException("OccupancyOverride", command.overrideId()));
        authorization.requireSite(actor, pending.siteCode(), command.channel(), "OccupancyOverride",
                pending.id().toString());
        if (!authorization.has(actor, SflPermission.FACILITIES_OCCUPANCY_OVERRIDE_APPROVE)) {
            audit.recordDenial(actor, command.channel(), "OccupancyOverride", pending.id().toString(),
                    pending.siteCode(), "Approving an occupancy override requires FACILITIES_OCCUPANCY_OVERRIDE_APPROVE");
            throw OccupancyOverride.incomplete(
                    "The approver must hold FACILITIES_OCCUPANCY_OVERRIDE_APPROVE to be accountable for it.");
        }
        if (actor.actorId().equals(pending.requestedBy())) {
            audit.recordDenial(actor, command.channel(), "OccupancyOverride", pending.id().toString(),
                    pending.siteCode(), "The requester of an occupancy override cannot approve it");
        }
        OccupancyOverride approved = repository.saveOverride(pending.approve(actor.actorId(), clock.instant(),
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.OCCUPANCY_OVERRIDE_RECORDED, "OccupancyOverride",
                approved.id().toString(), approved.siteCode(), pending, approved);
        outbox.record(EVENT_OVERRIDE_RECORDED, 1, "OccupancyOverride", approved.id(), approved.siteCode(),
                actor.correlationId(), actor.actorId(), new OverrideEvent(approved.id(), approved.scenarioId(),
                        approved.roomId(), approved.roomCode(), approved.siteCode(), approved.headcountCovered(),
                        approved.requestedBy(), approved.approvedBy(), approved.approvedAt()));
        return approved;
    }

    @Transactional(readOnly = true)
    public List<OccupancyOverride> overrides(UUID scenarioId, ActorContext actor, SourceChannel channel) {
        AllocationScenario scenario = requireScenario(scenarioId);
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_READ, scenario.siteCode(), channel,
                "OccupancyOverride", scenarioId.toString());
        return repository.findOverrides(scenarioId);
    }

    // ---- shared with the other S158 services ------------------------------------------------------

    /** The room's compliance at this headcount, against the active standard for its type and site. */
    RoomCompliance evaluate(FacilityRoom room, int headcount) {
        OccupancyStandard standard = repository.findActiveStandard(room.siteCode(), room.spaceType()).orElse(null);
        return OccupancyCompliancePolicy.evaluate(room.id(), room.roomCode(), room.spaceType(), room.capacity(),
                room.areaSqm(), headcount, standard);
    }

    /**
     * Withdraws every live override for a room in a scenario, because its allocation just changed.
     * Audited, so the approver can see their approval no longer stands.
     */
    void withdrawForRoom(UUID scenarioId, UUID roomId, ActorContext actor, SourceChannel channel) {
        Instant at = clock.instant();
        for (OccupancyOverride existing : repository.findOverrides(scenarioId)) {
            if (existing.roomId().equals(roomId) && existing.isLive()) {
                OccupancyOverride withdrawn = repository.saveOverride(existing.withdraw(actor.actorId(), at, channel,
                        actor.correlationId()));
                audit.record(actor, channel, AuditAction.OCCUPANCY_OVERRIDE_WITHDRAWN, "OccupancyOverride",
                        withdrawn.id().toString(), withdrawn.siteCode(), existing, withdrawn);
            }
        }
    }

    static int totalHeadcount(List<ScenarioAllocation> lines) {
        return lines.stream().mapToInt(ScenarioAllocation::headcount).sum();
    }

    private AllocationScenario requireScenario(UUID id) {
        return repository.findScenario(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("AllocationScenario", id));
    }

    private FacilityRoom requireRoom(UUID roomId) {
        return facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
    }

    /** References and people only - the reason is free text and stays in the audit trail. */
    record OverrideEvent(UUID overrideId, UUID scenarioId, UUID roomId, String roomCode, String siteCode,
            int headcountCovered, String requestedBy, String approvedBy, Instant approvedAt) {
    }
}
