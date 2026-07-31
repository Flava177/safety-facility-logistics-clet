package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceVendor;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderPart;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.policy.SlaPolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The work-order workflow — SRS-SFL-S153-02.
 *
 * <h2>Assignment scope, which is the important part of this class</h2>
 *
 * Site scope is the wrong boundary for a contractor. A vendor technician with
 * {@code X-SFL-Sites: CLET-HQ} and the pre-S153 permission set could read every work order, every
 * fault and every asset condition at the site — including which security equipment was broken.
 *
 * <p>So {@link SflRole#VENDOR_TECHNICIAN} is narrowed per record, not per site: a vendor sees and
 * touches only the work orders <strong>assigned to them</strong>. That rule cannot live in the
 * permission matrix, because "assigned to me" is a property of the record; it lives in
 * {@link #assertVisible} and {@link #vendorFilter}, and it is applied to every read and every write
 * rather than to a chosen few.
 *
 * <p>The narrowing is by {@code assignedTo} matching the actor's id. A vendor firm with several
 * technicians therefore sees per person, not per firm — which is the stricter reading and the one to
 * keep until CLET says otherwise, because widening later is a decision and narrowing later is a
 * regression somebody has already built a habit around.
 *
 * <h2>Closure</h2>
 *
 * Two gates, both from SRS-SFL-S153-02: a closure reason, and the evidence the configuration required
 * <em>when the order was raised</em>. The count is stored on the order rather than recomputed so an
 * assignee is held to the rule that applied to their job, not one changed while they were working.
 */
@Service
public class WorkOrderApplicationService {

    private final MaintenanceRepository maintenance;
    private final FacilitiesRepository facilities;
    private final FacilityFaultService faults;
    private final MaintenanceConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public WorkOrderApplicationService(MaintenanceRepository maintenance, FacilitiesRepository facilities,
            FacilityFaultService faults, MaintenanceConfiguration configuration,
            FacilitiesAuthorization authorization, AuditPort audit, IdempotencyPort idempotency,
            ServiceOutbox outbox, Clock clock) {
        this.maintenance = maintenance;
        this.facilities = facilities;
        this.faults = faults;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    // =============================================================================================
    // Commands
    // =============================================================================================

    @Transactional
    public WorkOrder createFromFault(MaintenanceCommands.CreateWorkOrderFromFault command) {
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

        Instant at = now();
        MaintenanceVendor vendor = command.vendorId() == null ? null
                : requireAssignableVendor(command.vendorId(), fault.siteCode(), at);
        SlaPolicy sla = configuration.slaPolicyFor(fault.siteCode());
        Instant due = sla.resolutionDueFrom(at, fault.priority(), operatingModeOf(fault.siteCode()),
                vendor == null ? null : vendor.responseHours());
        int evidenceRequired = configuration.evidenceRequiredFor(fault.siteCode(), fault.priority());

        WorkOrder order = WorkOrder.fromFault(UUID.randomUUID(),
                maintenance.nextWorkOrderNumber(fault.siteCode()), fault, due, evidenceRequired,
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
        publish("sfl.ifimp.work-order-created.v1", saved, actor);
        idempotency.recordResult("create-work-order", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), saved.id(), saved.siteCode(),
                actor.actorId());
        return saved;
    }

    @Transactional
    public WorkOrder assign(MaintenanceCommands.AssignWorkOrder command) {
        ActorContext actor = command.actor();
        WorkOrder order = requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_ASSIGN, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        order.metadata().requireVersion(command.expectedVersion(), "Work order", order.id());

        Instant at = now();
        if (command.vendorId() != null) {
            requireAssignableVendor(command.vendorId(), order.siteCode(), at);
        }
        WorkOrder assigned = maintenance.saveWorkOrder(order.assignTo(command.assignedTo(), command.vendorId(),
                actor.actorId(), at, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_ASSIGNED, "WorkOrder",
                assigned.id().toString(), assigned.siteCode(), order, assigned);
        publish("sfl.ifimp.work-order-assigned.v1", assigned, actor);
        return assigned;
    }

    /** Start, hold, complete and reopen. One method because the guards are identical. */
    @Transactional
    public WorkOrder transition(MaintenanceCommands.TransitionWorkOrder command) {
        ActorContext actor = command.actor();
        WorkOrder order = requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_UPDATE, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        assertVisible(actor, order, command.channel());
        order.metadata().requireVersion(command.expectedVersion(), "Work order", order.id());

        Instant at = now();
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
        publish("sfl.ifimp.work-order-" + command.transition().name().toLowerCase(java.util.Locale.ROOT) + ".v1", saved,
                actor);
        return saved;
    }

    /**
     * Closure: the evidence gate, the fault, and the asset's service date.
     *
     * <p>Three things happen and all three are part of one transaction, because a closure that
     * recorded the service but left the fault open — or the other way round — would leave the estate
     * disagreeing with itself in a way nobody would notice until an examination.
     */
    @Transactional
    public WorkOrder close(MaintenanceCommands.CloseWorkOrder command) {
        ActorContext actor = command.actor();
        WorkOrder order = requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_CLOSE, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        assertVisible(actor, order, command.channel());
        order.metadata().requireVersion(command.expectedVersion(), "Work order", order.id());

        int attached = maintenance.countClosureEvidence(order.id());
        Instant at = now();
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
        publish("sfl.ifimp.work-order-closed.v1", closed, actor);
        return closed;
    }

    @Transactional
    public WorkOrder cancel(MaintenanceCommands.CancelWorkOrder command) {
        ActorContext actor = command.actor();
        WorkOrder order = requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_CANCEL, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        order.metadata().requireVersion(command.expectedVersion(), "Work order", order.id());

        WorkOrder cancelled = maintenance.saveWorkOrder(order.cancel(command.reason(), actor.actorId(), now(),
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_CANCELLED, "WorkOrder",
                cancelled.id().toString(), cancelled.siteCode(), order, cancelled);
        publish("sfl.ifimp.work-order-cancelled.v1", cancelled, actor);
        return cancelled;
    }

    // ---- parts ----------------------------------------------------------------------------------

    @Transactional
    public WorkOrderPart recordPart(MaintenanceCommands.RecordPart command) {
        ActorContext actor = command.actor();
        WorkOrder order = requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_UPDATE, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        assertVisible(actor, order, command.channel());
        if (!order.status().isOpen()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Parts cannot be recorded against a " + order.status() + " work order.");
        }

        WorkOrderPart part = maintenance.savePart(WorkOrderPart.record(UUID.randomUUID(), order.id(),
                command.partCode(), command.description(), command.quantity(), command.unitCost(),
                command.currency(), command.supplier(), actor.actorId(), now()));
        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_PART_RECORDED, "WorkOrderPart",
                part.id().toString(), order.siteCode(), null, part);
        return part;
    }

    @Transactional
    public void removePart(MaintenanceCommands.RemovePart command) {
        ActorContext actor = command.actor();
        WorkOrder order = requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_UPDATE, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        assertVisible(actor, order, command.channel());
        if (!order.status().isOpen()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Parts cannot be removed from a " + order.status() + " work order.");
        }
        WorkOrderPart part = maintenance.findParts(order.id()).stream()
                .filter(candidate -> candidate.id().equals(command.partId()))
                .findFirst()
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Work order part",
                        command.partId()));
        maintenance.deletePart(part.id());
        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_PART_REMOVED, "WorkOrderPart",
                part.id().toString(), order.siteCode(), part, null);
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<WorkOrder> search(String siteCode, UUID roomId, UUID assetId, WorkOrderStatus status,
            String assignedTo, UUID vendorId, Boolean openOnly, int limit, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_READ, channel, "WorkOrder", "list",
                siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "WorkOrder");
        String assignee = vendorFilter(actor) != null ? vendorFilter(actor) : assignedTo;
        List<WorkOrder> found = maintenance.findWorkOrders(siteCode, roomId, assetId, status, assignee,
                vendorId, openOnly, limit);
        return authorization.filterBySite(actor, found, WorkOrder::siteCode);
    }

    @Transactional(readOnly = true)
    public WorkOrder findById(UUID id, ActorContext actor, SourceChannel channel) {
        WorkOrder order = requireWorkOrder(id);
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_READ, order.siteCode(), channel,
                "WorkOrder", id.toString());
        assertVisible(actor, order, channel);
        return order;
    }

    @Transactional(readOnly = true)
    public List<WorkOrderPart> parts(UUID workOrderId, ActorContext actor, SourceChannel channel) {
        WorkOrder order = findById(workOrderId, actor, channel);
        return maintenance.findParts(order.id());
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    /** Escalation, applied by the scheduled evaluator. Package-private: not a caller's use case. */
    @Transactional
    WorkOrder applyEscalation(WorkOrder order, int level, ActorContext actor, SourceChannel channel) {
        WorkOrder escalated = order.escalateTo(level, actor.actorId(), now(), channel, actor.correlationId());
        if (escalated == order) {
            return order;
        }
        WorkOrder saved = maintenance.saveWorkOrder(escalated);
        audit.record(actor, channel, AuditAction.WORK_ORDER_ESCALATED, "WorkOrder", saved.id().toString(),
                saved.siteCode(), order, saved);
        publish("sfl.ifimp.work-order-escalated.v1", saved, actor);
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

    private MaintenanceVendor requireAssignableVendor(UUID vendorId, String siteCode, Instant at) {
        MaintenanceVendor vendor = maintenance.findVendor(vendorId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Vendor", vendorId));
        if (!vendor.siteCode().equals(siteCode)) {
            throw new FacilitiesException.ValidationFailedException(
                    "Vendor " + vendor.vendorCode() + " is not registered at site " + siteCode + ".");
        }
        String reason = vendor.unassignableReason(at.atZone(ZoneOffset.UTC).toLocalDate());
        if (reason != null) {
            throw new FacilitiesException.ValidationFailedException(reason);
        }
        return vendor;
    }

    /**
     * The per-record narrowing for contractors. See the class comment.
     *
     * <p>Applied to reads and writes alike. A vendor who could not <em>see</em> an order but could
     * still transition it by guessing its id would make the read-side narrowing decorative.
     */
    private void assertVisible(ActorContext actor, WorkOrder order, SourceChannel channel) {
        String filter = vendorFilter(actor);
        if (filter == null || filter.equals(order.assignedTo())) {
            return;
        }
        audit.recordDenial(actor, channel, "WorkOrder", order.id().toString(), order.siteCode(),
                "A vendor technician may act only on work orders assigned to them");
        throw new FacilitiesException.UnauthorizedScopeException(
                "You may only view work orders assigned to you.");
    }

    /** The {@code assignedTo} a query must be narrowed to, or {@code null} for no narrowing. */
    private String vendorFilter(ActorContext actor) {
        Set<SflRole> roles = actor.principal().roles();
        boolean onlyVendor = roles.contains(SflRole.VENDOR_TECHNICIAN)
                && roles.stream().allMatch(role -> role == SflRole.VENDOR_TECHNICIAN);
        return onlyVendor ? actor.actorId() : null;
    }

    private WorkOrder requireWorkOrder(UUID id) {
        return maintenance.findWorkOrder(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Work order", id));
    }

    private OperatingMode operatingModeOf(String siteCode) {
        return facilities.findSiteByCode(siteCode).map(Site::operatingMode).orElse(OperatingMode.ROUTINE);
    }

    private void publish(String eventType, WorkOrder order, ActorContext actor) {
        outbox.record(eventType, 1, "WorkOrder", order.id(), order.siteCode(), actor.correlationId(),
                actor.actorId(), order);
    }

    private Instant now() {
        return clock.instant();
    }
}
