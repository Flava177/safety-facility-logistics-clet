package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceVendor;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.policy.SlaPolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The work-order lifecycle commands - create, assign, transition, close, cancel, escalate - split
 * out of {@link WorkOrderApplicationService} so that class is a coordinator rather than every rule
 * for every command in one file. See that class's Javadoc for the closure and assignment-scope rules
 * this implements; {@link WorkOrderSupport} carries what this shares with {@link WorkOrderPartCommands}.
 *
 * <p>Package-private and constructed once by {@link WorkOrderApplicationService}, not a Spring bean
 * of its own - the transaction boundary lives on that class's public methods, and a plain method call
 * from an already-transactional method runs in the same transaction with no proxying of its own
 * needed.
 */
final class WorkOrderLifecycleCommands {

    private final MaintenanceRepository maintenance;
    private final FacilitiesRepository facilities;
    private final FacilityFaultService faults;
    private final MaintenanceConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final WorkOrderSupport support;

    WorkOrderLifecycleCommands(MaintenanceRepository maintenance, FacilitiesRepository facilities,
            FacilityFaultService faults, MaintenanceConfiguration configuration,
            FacilitiesAuthorization authorization, AuditPort audit, IdempotencyPort idempotency,
            ServiceOutbox outbox, WorkOrderSupport support) {
        this.maintenance = maintenance;
        this.facilities = facilities;
        this.faults = faults;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.support = support;
    }

    WorkOrder createFromFault(MaintenanceCommands.CreateWorkOrderFromFault command) {
        ActorContext actor = command.actor();
        FacilityFault fault = faults.faultFor(command.faultId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_CREATE, fault.siteCode(),
                command.channel(), "WorkOrder", "new");

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<WorkOrder> replayed = idempotency
                    .findExistingResult("create-work-order", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(maintenance::findWorkOrder);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        maintenance.findWorkOrderForFault(fault.id()).ifPresent(existing -> {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Fault " + fault.faultNumber() + " already has work order " + existing.workOrderNumber()
                            + ".");
        });

        Instant at = support.now();
        MaintenanceVendor vendor = command.vendorId() == null ? null
                : support.requireAssignableVendor(command.vendorId(), fault.siteCode(), at);
        SlaPolicy sla = configuration.slaPolicyFor(fault.siteCode());
        Instant due = sla.resolutionDueFrom(at, fault.priority(), support.operatingModeOf(fault.siteCode()),
                vendor == null ? null : vendor.responseHours());
        int evidenceRequired = configuration.evidenceRequiredFor(fault.siteCode(), fault.priority());
        // The response deadline comes from the same policy, read at the same moment, so the two clocks
        // on one job can never have been set from different configuration versions.
        Instant responseDue = sla.responseDueFrom(at, fault.priority(), support.operatingModeOf(fault.siteCode()));

        WorkOrder order = WorkOrder.fromFault(UUID.randomUUID(),
                maintenance.nextWorkOrderNumber(fault.siteCode()), fault, due, responseDue, evidenceRequired,
                actor.actorId(), at, command.channel(), actor.correlationId());
        if (command.assignTo() != null && !command.assignTo().isBlank()) {
            order = order.assignTo(command.assignTo(), command.vendorId(), actor.actorId(), at,
                    command.channel(), actor.correlationId());
        }

        WorkOrder saved = maintenance.saveWorkOrder(order);
        maintenance.saveFault(fault.linkWorkOrder(saved.id(), actor.actorId(), at, command.channel(),
                actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_CREATED, "WorkOrder",
                saved.id().toString(), saved.siteCode(), null, saved);
        support.publish("sfl.ifimp.work-order-created.v1", saved, actor);
        idempotency.recordResult("create-work-order", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), saved.id(), saved.siteCode(),
                actor.actorId());
        return saved;
    }

    WorkOrder assign(MaintenanceCommands.AssignWorkOrder command) {
        ActorContext actor = command.actor();
        WorkOrder order = support.requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_ASSIGN, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        order.metadata().requireVersion(command.expectedVersion(), "Work order", order.id());

        Instant at = support.now();
        if (command.vendorId() != null) {
            support.requireAssignableVendor(command.vendorId(), order.siteCode(), at);
        }
        WorkOrder assigned = maintenance.saveWorkOrder(order.assignTo(command.assignedTo(), command.vendorId(),
                actor.actorId(), at, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_ASSIGNED, "WorkOrder",
                assigned.id().toString(), assigned.siteCode(), order, assigned);
        support.publish("sfl.ifimp.work-order-assigned.v1", assigned, actor);
        return assigned;
    }

    /** Start, hold, complete and reopen. One method because the guards are identical. */
    WorkOrder transition(MaintenanceCommands.TransitionWorkOrder command) {
        ActorContext actor = command.actor();
        WorkOrder order = support.requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_UPDATE, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        support.assertVisible(actor, order, command.channel());
        order.metadata().requireVersion(command.expectedVersion(), "Work order", order.id());

        Instant at = support.now();
        String correlationId = actor.correlationId();
        WorkOrder moved = switch (command.transition()) {
            case START -> order.start(actor.actorId(), at, command.channel(), correlationId);
            case HOLD -> order.hold(command.notes(), actor.actorId(), at, command.channel(), correlationId);
            case COMPLETE -> order.complete(command.notes(), actor.actorId(), at, command.channel(),
                    correlationId);
            case REOPEN -> reopen(order, command, actor, at, correlationId);
        };
        AuditAction action = switch (command.transition()) {
            case START -> AuditAction.WORK_ORDER_STARTED;
            case HOLD -> AuditAction.WORK_ORDER_HELD;
            case COMPLETE -> AuditAction.WORK_ORDER_COMPLETED;
            case REOPEN -> AuditAction.WORK_ORDER_REOPENED;
        };

        WorkOrder saved = maintenance.saveWorkOrder(moved);
        audit.record(actor, command.channel(), action, "WorkOrder", saved.id().toString(), saved.siteCode(),
                order, saved);
        support.publish("sfl.ifimp.work-order-" + command.transition().name().toLowerCase(Locale.ROOT) + ".v1",
                saved, actor);
        return saved;
    }

    /**
     * Closure: the evidence gate, the fault, and the asset's service date.
     *
     * <p>Three things happen and all three are part of one transaction, because a closure that
     * recorded the service but left the fault open - or the other way round - would leave the estate
     * disagreeing with itself in a way nobody would notice until an examination.
     */
    WorkOrder close(MaintenanceCommands.CloseWorkOrder command) {
        ActorContext actor = command.actor();
        WorkOrder order = support.requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_CLOSE, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        support.assertVisible(actor, order, command.channel());
        order.metadata().requireVersion(command.expectedVersion(), "Work order", order.id());

        int attached = maintenance.countClosureEvidence(order.id());
        Instant at = support.now();
        WorkOrder closed = maintenance.saveWorkOrder(order.close(command.closureNotes(), attached,
                actor.actorId(), at, command.channel(), actor.correlationId()));

        if (closed.facilityFaultId() != null) {
            FacilityFault fault = faults.faultFor(closed.facilityFaultId());
            if (fault.status().isOpen()) {
                faults.resolveFromWorkOrder(fault,
                        "Closed by work order " + closed.workOrderNumber() + ": " + closed.closureNotes(),
                        actor, command.channel());
            }
        }
        if (closed.recordsServiceOnClosure()) {
            recordServiceOn(closed, actor, command.channel(), at);
        }

        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_CLOSED, "WorkOrder",
                closed.id().toString(), closed.siteCode(), order, closed);
        support.publish("sfl.ifimp.work-order-closed.v1", closed, actor);
        return closed;
    }

    WorkOrder cancel(MaintenanceCommands.CancelWorkOrder command) {
        ActorContext actor = command.actor();
        WorkOrder order = support.requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_CANCEL, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        order.metadata().requireVersion(command.expectedVersion(), "Work order", order.id());

        WorkOrder cancelled = maintenance.saveWorkOrder(order.cancel(command.reason(), actor.actorId(),
                support.now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_CANCELLED, "WorkOrder",
                cancelled.id().toString(), cancelled.siteCode(), order, cancelled);
        support.publish("sfl.ifimp.work-order-cancelled.v1", cancelled, actor);
        return cancelled;
    }

    /** Escalation, applied by the scheduled evaluator. */
    WorkOrder applyEscalation(WorkOrder order, int level, ActorContext actor, SourceChannel channel) {
        WorkOrder escalated = order.escalateTo(level, actor.actorId(), support.now(), channel,
                actor.correlationId());
        if (escalated == order) {
            return order;
        }
        WorkOrder saved = maintenance.saveWorkOrder(escalated);
        audit.record(actor, channel, AuditAction.WORK_ORDER_ESCALATED, "WorkOrder", saved.id().toString(),
                saved.siteCode(), order, saved);
        support.publish("sfl.ifimp.work-order-escalated.v1", saved, actor);
        return saved;
    }

    private WorkOrder reopen(WorkOrder order, MaintenanceCommands.TransitionWorkOrder command,
            ActorContext actor, Instant at, String correlationId) {
        // Reopening reverses somebody's judgement that the work was finished, so it takes the closing
        // permission rather than the updating one. SRS-SFL-S153-02: "Only authorised roles may
        // approve, override, cancel or reopen workflow items."
        if (!authorization.has(actor, SflPermission.FACILITIES_WORK_ORDER_CLOSE)) {
            audit.recordDenial(actor, command.channel(), "WorkOrder", order.id().toString(), order.siteCode(),
                    "Reopening a completed work order requires FACILITIES_WORK_ORDER_CLOSE");
            throw new FacilitiesException.UnauthorizedApprovalException(
                    "Reopening a completed work order requires the closing permission.");
        }
        return order.reopen(command.notes(), actor.actorId(), at, command.channel(), correlationId);
    }

    /**
     * Records the service against the asset a preventive order covered.
     *
     * <p>This is the loop S152 could not close: the interval and the last-service date were on the
     * asset, the dashboard counted what was overdue from them, and nothing could move them except
     * editing the asset by hand.
     */
    private void recordServiceOn(WorkOrder order, ActorContext actor, SourceChannel channel, Instant at) {
        Optional<FacilityAsset> maybeAsset = facilities.findAsset(order.assetId());
        if (maybeAsset.isEmpty()) {
            return;
        }
        LocalDate servicedOn = at.atZone(ZoneOffset.UTC).toLocalDate();
        FacilityAsset asset = maybeAsset.get();
        FacilityAsset serviced = facilities.saveAsset(asset.recordService(servicedOn, actor.actorId(), at,
                channel, actor.correlationId()));
        audit.record(actor, channel, AuditAction.FACILITY_ASSET_UPDATED, "FacilityAsset",
                serviced.id().toString(), serviced.siteCode(), asset, serviced);
        outbox.record("sfl.ifimp.facility-asset-serviced.v1", 1, "FacilityAsset", serviced.id(), serviced.siteCode(),
                actor.correlationId(), actor.actorId(), serviced);
    }
}
