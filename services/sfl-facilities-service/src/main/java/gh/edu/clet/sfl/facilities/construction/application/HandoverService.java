package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.application.ports.DefectWorkOrderPort;
import gh.edu.clet.sfl.facilities.construction.application.ports.EstateRegisterPort;
import gh.edu.clet.sfl.facilities.construction.application.ports.ScenarioConfirmationPort;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.Handover;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.construction.domain.RegisterChange;
import gh.edu.clet.sfl.facilities.construction.domain.policy.HandoverCompletenessPolicy;
import gh.edu.clet.sfl.facilities.construction.domain.policy.ProjectClosurePolicy;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Practical completion, handover, defects liability and closure - SRS-SFL-S176-04.
 *
 * <h2>Handover writes the register, in the same transaction</h2>
 *
 * "Given a project reaches practical completion, when handover is recorded, then the S152 facilities
 * register reflects the new/changed space without a separate manual update." The handover command
 * lists the register changes; each is applied through S152's own service as the handover actor, and
 * recorded as a {@link RegisterChange}, in one transaction with the handover itself. If S152 refuses
 * any of them - a duplicate room code, a permission the actor lacks - the whole handover rolls back:
 * there is no half-handed-over building.
 *
 * <p>A handover listing no change, or not covering a space the project declared it would change, is
 * <em>flagged</em>: an INCOMPLETE handover record is written, {@code PROJECT_HANDOVER_FLAGGED_INCOMPLETE}
 * is audited, the project stays at practical completion, and the caller is refused with
 * {@code PROJECT_HANDOVER_INCOMPLETE}. The flag survives the refusal.
 *
 * <h2>S158</h2>
 *
 * A project carrying a committed S158 scenario asks S158 to confirm it. If S158 does not recognise the
 * scenario (the case for every scenario until S158 is merged) the handover still completes - S152 is
 * the authoritative register and it is correct - and the confirmation is recorded UNRESOLVED, shown on
 * the dashboard and retryable through {@link #retryScenarioConfirmation}.
 *
 * <h2>Defects</h2>
 *
 * Raised only during the liability period, only against a contractor assigned to the project, as an
 * S153 work order through {@link DefectWorkOrderPort} with category {@code CONSTRUCTION_DEFECT} and the
 * project and contractor in its origin reference. A defect closes when S153 reports its work order
 * CLOSED - read on every close attempt and by the defect-sync sweep - or when somebody with close
 * authority defers it with a reason.
 */
@Service
public class HandoverService {

    private final ConstructionContext context;
    private final ConstructionRepository repository;
    private final AuditPort audit;
    private final EstateRegisterPort estate;
    private final ScenarioConfirmationPort scenarios;
    private final DefectWorkOrderPort workOrders;

    public HandoverService(ConstructionContext context, EstateRegisterPort estate, ScenarioConfirmationPort scenarios,
            DefectWorkOrderPort workOrders) {
        this.context = context;
        this.repository = context.repository();
        this.audit = context.audit();
        this.estate = estate;
        this.scenarios = scenarios;
        this.workOrders = workOrders;
    }

    // =============================================================================================
    // Practical completion and handover
    // =============================================================================================

    @Transactional
    public ConstructionProject recordPracticalCompletion(ConstructionCommands.RecordPracticalCompletion command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        project.metadata().requireVersion(command.expectedVersion(), "Construction project", project.id());
        ConstructionProject completed = repository.saveProject(project.recordPracticalCompletion(actor.actorId(),
                context.now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_PRACTICAL_COMPLETION_RECORDED,
                "ConstructionProject", completed.id().toString(), completed.siteCode(), project, completed);
        return completed;
    }

    @Transactional(noRollbackFor = ConstructionRefusal.class)
    public Handover handover(ConstructionCommands.RecordHandover command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_HANDOVER, project.siteCode(),
                command.channel(), "ConstructionProject", project.id().toString());
        project.metadata().requireVersion(command.expectedVersion(), "Construction project", project.id());
        if (project.status() != ProjectStatus.PRACTICAL_COMPLETION) {
            throw new FacilitiesException.InvalidStateTransitionException("Handover follows practical completion; "
                    + project.projectReference() + " is " + project.status() + ".");
        }
        Instant at = context.now();
        LocalDate date = command.handoverDate() == null ? context.today() : command.handoverDate();
        List<ConstructionCommands.RoomChange> changes = command.roomChanges() == null ? List.of()
                : command.roomChanges();
        validateChanges(project, changes);

        Set<UUID> updated = new HashSet<>();
        changes.stream().filter(change -> change.action() == ConstructionCommands.RoomChange.Action.UPDATE)
                .forEach(change -> updated.add(change.roomId()));
        HandoverCompletenessPolicy.Completeness completeness = HandoverCompletenessPolicy.evaluate(changes.size(),
                updated, project.affectedRoomIds());
        if (!completeness.complete()) {
            Handover flagged = repository.saveHandover(Handover.incomplete(project, date, command.notes(),
                    completeness.reason(), actor.actorId(), at, command.channel(), actor.correlationId()));
            audit.record(actor, command.channel(), AuditAction.PROJECT_HANDOVER_FLAGGED_INCOMPLETE, "ConstructionProject",
                    project.id().toString(), project.siteCode(), null, flagged);
            throw new ConstructionRefusal(FacilitiesErrorCode.PROJECT_HANDOVER_INCOMPLETE, completeness.reason());
        }

        UUID handoverId = UUID.randomUUID();
        List<RegisterChange> applied = new ArrayList<>();
        for (ConstructionCommands.RoomChange change : changes) {
            applied.add(apply(handoverId, project, change, actor, command.channel(), at));
        }
        ScenarioConfirmationPort.Result confirmation = confirmScenario(project, actor);
        Handover handover = repository.saveHandover(Handover.complete(handoverId, project, date, command.notes(),
                applied.size(), scenarioState(project, confirmation), confirmation == null ? null
                        : confirmation.detail(), actor.actorId(), at, command.channel(), actor.correlationId()));
        applied.forEach(repository::saveRegisterChange);

        ConstructionProject handedOver = repository.saveProject(project.handOver(
                date.plusDays(context.configuration().defectsLiabilityDays(project.siteCode())), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_HANDOVER_RECORDED, "ConstructionProject",
                handedOver.id().toString(), handedOver.siteCode(), project, Map.of("handover", handover,
                        "registerChanges", applied));
        context.publish(ConstructionEvents.PROJECT_HANDED_OVER, "ConstructionProject", handedOver.id(),
                handedOver.siteCode(), actor, ConstructionEvents.payload("projectId", handedOver.id(),
                        "projectReference", handedOver.projectReference(), "handoverId", handover.id(),
                        "handoverDate", date, "roomIds", applied.stream().map(c -> c.roomId().toString()).toList(),
                        "committedScenarioId", handedOver.committedScenarioId(), "scenarioConfirmation",
                        handover.scenarioConfirmation(), "defectsLiabilityEndsOn",
                        handedOver.defectsLiabilityEndsOn()));
        return handover;
    }

    /** Re-asks S158 to confirm an UNRESOLVED scenario on the project's complete handover. */
    @Transactional
    public Handover retryScenarioConfirmation(ConstructionCommands.RetryScenarioConfirmation command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_HANDOVER, project.siteCode(),
                command.channel(), "ConstructionProject", project.id().toString());
        Handover handover = repository.findHandovers(project.id()).stream()
                .filter(h -> h.outcome() == Handover.Outcome.COMPLETE).findFirst()
                .orElseThrow(() -> new FacilitiesException.InvalidStateTransitionException(
                        "Project " + project.projectReference() + " has no completed handover."));
        if (handover.scenarioConfirmation() != Handover.ScenarioConfirmation.UNRESOLVED) {
            return handover;
        }
        ScenarioConfirmationPort.Result result = confirmScenario(project, actor);
        Handover updated = repository.saveHandover(handover.withScenarioConfirmation(scenarioState(project, result),
                result.detail(), actor.actorId(), context.now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_SCENARIO_CONFIRMATION_RECORDED, "ConstructionProject",
                project.id().toString(), project.siteCode(), handover, updated);
        return updated;
    }

    // =============================================================================================
    // Defects liability
    // =============================================================================================

    @Transactional
    public DefectItem raiseDefect(ConstructionCommands.RaiseDefect command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        if (!context.authorization().has(actor, SflPermission.FACILITIES_PROJECT_HANDOVER)) {
            context.requireManage(actor, project, command.channel());
        }
        context.authorization().requireSite(actor, project.siteCode(), command.channel(), "ConstructionProject",
                project.id().toString());
        LocalDate today = context.today();
        if (!project.inLiabilityPeriod(today)) {
            throw new FacilitiesException.InvalidStateTransitionException(project.status() == ProjectStatus.HANDED_OVER
                    ? "The defects-liability period ended on " + project.defectsLiabilityEndsOn() + "."
                    : "Defects-liability items are raised after handover; " + project.projectReference() + " is "
                            + project.status() + ".");
        }
        boolean assigned = repository.findAssignments(project.id()).stream()
                .anyMatch(assignment -> assignment.contractorId().equals(command.contractorId()));
        if (!assigned) {
            throw new FacilitiesException.ValidationFailedException(
                    "A defect is tagged to a contractor assigned to the project.");
        }
        Contractor contractor = context.requireContractor(command.contractorId());
        if (command.roomId() != null) {
            EstateRegisterPort.RoomView room = estate.findRoom(command.roomId())
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", command.roomId()));
            if (!room.siteCode().equals(project.siteCode())) {
                throw new FacilitiesException.ValidationFailedException("Space " + room.roomCode() + " is not at "
                        + project.siteCode() + ".");
            }
        }
        Instant at = context.now();
        DefectItem defect = repository.saveDefect(DefectItem.raise(UUID.randomUUID(),
                repository.nextDefectReference(project.siteCode()), project, contractor.id(), command.description(),
                command.roomId(), command.locationCode(), command.priority(), actor.actorId(), at, command.channel(),
                actor.correlationId()));
        DefectWorkOrderPort.RaisedDefectWorkOrder order = workOrders.raise(defect, project, contractor,
                actor.actorId(), actor.correlationId());
        DefectItem linked = repository.saveDefect(defect.withWorkOrder(order.workOrderId(), order.workOrderNumber(),
                order.faultNumber(), order.status()));
        audit.record(actor, command.channel(), AuditAction.DEFECT_RAISED, "DefectItem", linked.id().toString(),
                linked.siteCode(), null, linked);
        context.publish(ConstructionEvents.PROJECT_DEFECT_RAISED, "DefectItem", linked.id(), linked.siteCode(), actor,
                ConstructionEvents.payload("projectId", project.id(), "projectReference", project.projectReference(),
                        "defectId", linked.id(), "defectReference", linked.defectReference(), "contractorId",
                        contractor.id(), "contractorCode", contractor.contractorCode(), "priority", linked.priority(),
                        "workOrderId", linked.workOrderId(), "workOrderNumber", linked.workOrderNumber(),
                        "category", "CONSTRUCTION_DEFECT"));
        return linked;
    }

    @Transactional
    public DefectItem deferDefect(ConstructionCommands.DeferDefect command) {
        ActorContext actor = command.actor();
        DefectItem defect = repository.findDefect(command.defectId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Defect", command.defectId()));
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_CLOSE, defect.siteCode(),
                command.channel(), "DefectItem", defect.id().toString());
        DefectItem deferred = repository.saveDefect(defect.defer(command.reason(), actor.actorId(), context.now(),
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.DEFECT_DEFERRED, "DefectItem", deferred.id().toString(),
                deferred.siteCode(), defect, deferred);
        return deferred;
    }

    @Transactional(noRollbackFor = ConstructionRefusal.class)
    public ConstructionProject close(ConstructionCommands.CloseProject command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_CLOSE, project.siteCode(),
                command.channel(), "ConstructionProject", project.id().toString());
        project.metadata().requireVersion(command.expectedVersion(), "Construction project", project.id());
        List<DefectItem> defects = repository.findDefects(project.id()).stream()
                .map(defect -> refresh(defect, actor, command.channel())).toList();
        ProjectClosurePolicy.Decision decision = ProjectClosurePolicy.evaluate(project, defects, context.today());
        if (!decision.allowed()) {
            if (decision.refusal() == FacilitiesErrorCode.INVALID_STATE_TRANSITION) {
                throw new FacilitiesException.InvalidStateTransitionException(decision.reason());
            }
            audit.record(actor, command.channel(), AuditAction.PROJECT_CLOSE_REFUSED, "ConstructionProject",
                    project.id().toString(), project.siteCode(), project.status(),
                    Map.of("code", decision.refusal().name(), "reason", decision.reason()));
            throw new ConstructionRefusal(decision.refusal(), decision.reason());
        }
        ConstructionProject closed = repository.saveProject(project.close(actor.actorId(), context.now(),
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PROJECT_CLOSED, "ConstructionProject",
                closed.id().toString(), closed.siteCode(), project, closed);
        context.publish(ConstructionEvents.PROJECT_CLOSED, "ConstructionProject", closed.id(), closed.siteCode(), actor,
                ConstructionEvents.payload("projectId", closed.id(), "projectReference", closed.projectReference(),
                        "closedAt", closed.closedAt(), "defectsClosed", defects.stream()
                                .filter(d -> d.status() == DefectItem.Status.CLOSED).count(),
                        "defectsDeferred", defects.stream().filter(d -> d.status() == DefectItem.Status.DEFERRED)
                                .count()));
        return closed;
    }

    /** Reads S153 for every open defect and closes those whose work order is closed. */
    @Transactional
    public DefectSync syncDefects(ActorContext actor) {
        List<DefectItem> open = repository.findOpenDefects(context.configuration().sweepBatchSize());
        int closed = 0;
        for (DefectItem defect : open) {
            if (refresh(defect, actor, SourceChannel.SCHEDULER).status() == DefectItem.Status.CLOSED) {
                closed++;
            }
        }
        return new DefectSync(open.size(), closed, context.now());
    }

    public record DefectSync(int examined, int closed, Instant evaluatedAt) {
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<DefectItem> defects(UUID projectId, ActorContext actor, SourceChannel channel) {
        ConstructionProject project = context.requireProject(projectId);
        context.requireRead(actor, project.siteCode(), channel, "ConstructionProject", projectId.toString());
        return repository.findDefects(project.id()).stream().sorted(Comparator.comparing(DefectItem::raisedAt)).toList();
    }

    public record HandoverView(Handover handover, List<RegisterChange> registerChanges) {
    }

    @Transactional(readOnly = true)
    public List<HandoverView> handovers(UUID projectId, ActorContext actor, SourceChannel channel) {
        ConstructionProject project = context.requireProject(projectId);
        context.requireRead(actor, project.siteCode(), channel, "ConstructionProject", projectId.toString());
        List<RegisterChange> changes = repository.findRegisterChanges(project.id());
        return repository.findHandovers(project.id()).stream()
                .sorted(Comparator.comparing(Handover::recordedAt))
                .map(handover -> new HandoverView(handover, changes.stream()
                        .filter(change -> change.handoverId().equals(handover.id())).toList()))
                .toList();
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    private void validateChanges(ConstructionProject project, List<ConstructionCommands.RoomChange> changes) {
        for (ConstructionCommands.RoomChange change : changes) {
            if (change == null || change.action() == null) {
                throw new FacilitiesException.ValidationFailedException("Each register change must say CREATE or UPDATE.");
            }
            if (change.action() == ConstructionCommands.RoomChange.Action.CREATE) {
                if (change.floorId() == null || change.roomCode() == null || change.roomCode().isBlank()
                        || change.name() == null || change.spaceType() == null) {
                    throw new FacilitiesException.ValidationFailedException(
                            "A new space needs a floor, a room code, a name and a space type.");
                }
                String site = estate.siteOfFloor(change.floorId())
                        .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Floor",
                                change.floorId()));
                if (!site.equals(project.siteCode())) {
                    throw new FacilitiesException.ValidationFailedException(
                            "A handover can change the register only at the project's own site, " + project.siteCode() + ".");
                }
            } else {
                if (change.roomId() == null) {
                    throw new FacilitiesException.ValidationFailedException("An update names the space it changes.");
                }
                EstateRegisterPort.RoomView room = estate.findRoom(change.roomId())
                        .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space",
                                change.roomId()));
                if (!room.siteCode().equals(project.siteCode())) {
                    throw new FacilitiesException.ValidationFailedException(
                            "A handover can change the register only at the project's own site, " + project.siteCode() + ".");
                }
            }
        }
    }

    private RegisterChange apply(UUID handoverId, ConstructionProject project, ConstructionCommands.RoomChange change,
            ActorContext actor, SourceChannel channel, Instant at) {
        if (change.action() == ConstructionCommands.RoomChange.Action.CREATE) {
            EstateRegisterPort.RoomView room = estate.createRoom(change.floorId(), change.roomCode(), change.name(),
                    change.spaceType(), change.capacity(), change.areaSqm(), change.costCentre(), change.bookable(),
                    change.examinationCapable(), actor, channel,
                    "construction-handover:" + project.id() + ":" + change.roomCode());
            return RegisterChange.of(handoverId, project, RegisterChange.Action.CREATED, room.id(), room.roomCode(),
                    room.version(), actor.actorId(), at, channel, actor.correlationId());
        }
        EstateRegisterPort.RoomView room = estate.updateRoom(change.roomId(), change.name(), change.spaceType(),
                change.capacity(), change.areaSqm(), change.costCentre(), change.bookable(),
                change.examinationCapable(), actor, channel);
        return RegisterChange.of(handoverId, project, RegisterChange.Action.UPDATED, room.id(), room.roomCode(),
                room.version(), actor.actorId(), at, channel, actor.correlationId());
    }

    private ScenarioConfirmationPort.Result confirmScenario(ConstructionProject project, ActorContext actor) {
        if (project.committedScenarioId() == null) {
            return null;
        }
        return scenarios.confirm(project.committedScenarioId(), project.id(), project.projectReference(),
                actor.actorId());
    }

    private static Handover.ScenarioConfirmation scenarioState(ConstructionProject project,
            ScenarioConfirmationPort.Result result) {
        if (project.committedScenarioId() == null || result == null) {
            return Handover.ScenarioConfirmation.NOT_APPLICABLE;
        }
        return result.confirmed() ? Handover.ScenarioConfirmation.CONFIRMED : Handover.ScenarioConfirmation.UNRESOLVED;
    }

    /** Brings one defect's state into line with its S153 work order. */
    private DefectItem refresh(DefectItem defect, ActorContext actor, SourceChannel channel) {
        if (!defect.isOpen() || defect.workOrderId() == null) {
            return defect;
        }
        Optional<DefectWorkOrderPort.RaisedDefectWorkOrder> order = workOrders.find(defect.workOrderId());
        if (order.isEmpty()) {
            return defect;
        }
        if (order.get().closed()) {
            DefectItem closed = repository.saveDefect(defect.closeFromWorkOrder(order.get().status(), actor.actorId(),
                    context.now(), channel, actor.correlationId()));
            audit.record(actor, channel, AuditAction.DEFECT_CLOSED, "DefectItem", closed.id().toString(),
                    closed.siteCode(), defect, closed);
            return closed;
        }
        if (!order.get().status().equals(defect.workOrderStatus())) {
            return repository.saveDefect(defect.withWorkOrderState(order.get().status()));
        }
        return defect;
    }
}
