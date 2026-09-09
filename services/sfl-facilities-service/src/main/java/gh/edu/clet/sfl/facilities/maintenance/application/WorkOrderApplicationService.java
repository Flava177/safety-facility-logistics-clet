package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderPart;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The work-order workflow - SRS-SFL-S153-02.
 *
 * <h2>Assignment scope, which is the important part of this class</h2>
 *
 * Site scope is the wrong boundary for a contractor. A vendor technician with
 * {@code X-SFL-Sites: CLET-HQ} and the pre-S153 permission set could read every work order, every
 * fault and every asset condition at the site - including which security equipment was broken.
 *
 * <p>So {@link SflRole#VENDOR_TECHNICIAN} is narrowed per record, not per site: a vendor sees and
 * touches only the work orders <strong>assigned to them</strong>. That rule cannot live in the
 * permission matrix, because "assigned to me" is a property of the record; it lives in
 * {@link WorkOrderSupport#assertVisible} and {@link WorkOrderSupport#vendorFilter}, and it is applied
 * to every read and every write rather than to a chosen few.
 *
 * <p>The narrowing is by {@code assignedTo} matching the actor's id. A vendor firm with several
 * technicians therefore sees per person, not per firm - which is the stricter reading and the one to
 * keep until CLET says otherwise, because widening later is a decision and narrowing later is a
 * regression somebody has already built a habit around.
 *
 * <h2>Closure</h2>
 *
 * Two gates, both from SRS-SFL-S153-02: a closure reason, and the evidence the configuration required
 * <em>when the order was raised</em>. The count is stored on the order rather than recomputed so an
 * assignee is held to the rule that applied to their job, not one changed while they were working.
 *
 * <h2>Why this class is thin</h2>
 *
 * <p>This class exists to be the one thing a controller and {@code MaintenanceEscalationService}
 * depend on, at the {@code @Transactional} boundary Spring's proxy needs a public method for. The
 * actual command bodies live in {@link WorkOrderLifecycleCommands} and {@link WorkOrderPartCommands};
 * {@link WorkOrderSupport} is what those two and the queries here share. Splitting stopped at "each
 * file is one cohesive concern", not at one-class-per-method - a search this small does not earn a
 * fourth file.
 */
@Service
public class WorkOrderApplicationService {

    private final MaintenanceRepository maintenance;
    private final FacilitiesAuthorization authorization;
    private final WorkOrderSupport support;
    private final WorkOrderLifecycleCommands lifecycle;
    private final WorkOrderPartCommands parts;

    public WorkOrderApplicationService(MaintenanceRepository maintenance, FacilitiesRepository facilities,
            FacilityFaultService faults, MaintenanceConfiguration configuration,
            FacilitiesAuthorization authorization, AuditPort audit, IdempotencyPort idempotency,
            ServiceOutbox outbox, Clock clock) {
        this.maintenance = maintenance;
        this.authorization = authorization;
        this.support = new WorkOrderSupport(maintenance, facilities, audit, outbox, clock);
        this.lifecycle = new WorkOrderLifecycleCommands(maintenance, facilities, faults, configuration,
                authorization, audit, idempotency, outbox, support);
        this.parts = new WorkOrderPartCommands(maintenance, authorization, audit, support);
    }

    // =============================================================================================
    // Commands
    // =============================================================================================

    @Transactional
    public WorkOrder createFromFault(MaintenanceCommands.CreateWorkOrderFromFault command) {
        return lifecycle.createFromFault(command);
    }

    @Transactional
    public WorkOrder assign(MaintenanceCommands.AssignWorkOrder command) {
        return lifecycle.assign(command);
    }

    /** Start, hold, complete and reopen. One method because the guards are identical. */
    @Transactional
    public WorkOrder transition(MaintenanceCommands.TransitionWorkOrder command) {
        return lifecycle.transition(command);
    }

    /**
     * Closure: the evidence gate, the fault, and the asset's service date.
     *
     * <p>Three things happen and all three are part of one transaction, because a closure that
     * recorded the service but left the fault open - or the other way round - would leave the estate
     * disagreeing with itself in a way nobody would notice until an examination.
     */
    @Transactional
    public WorkOrder close(MaintenanceCommands.CloseWorkOrder command) {
        return lifecycle.close(command);
    }

    @Transactional
    public WorkOrder cancel(MaintenanceCommands.CancelWorkOrder command) {
        return lifecycle.cancel(command);
    }

    // ---- parts ----------------------------------------------------------------------------------

    @Transactional
    public WorkOrderPart recordPart(MaintenanceCommands.RecordPart command) {
        return parts.record(command);
    }

    @Transactional
    public void removePart(MaintenanceCommands.RemovePart command) {
        parts.remove(command);
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public RepositoryPage<WorkOrder> search(String siteCode, UUID roomId, UUID assetId, WorkOrderStatus status,
            String assignedTo, UUID vendorId, Boolean openOnly, int page, int size, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_READ, channel, "WorkOrder", "list",
                siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "WorkOrder");
        String assignee = support.vendorFilter(actor) != null ? support.vendorFilter(actor) : assignedTo;
        RepositoryPage<WorkOrder> found = maintenance.findWorkOrders(siteCode, roomId, assetId, status, assignee,
                vendorId, openOnly, page, size);
        List<WorkOrder> visible = authorization.filterBySite(actor, found.items(), WorkOrder::siteCode);
        // When filtering removed rows, the total is reported as what remains: a total counting records
        // the caller may not see would let them infer another site's estate size.
        return visible.size() == found.items().size()
                ? found
                : RepositoryPage.of(visible, visible.size(), found.page(), found.size());
    }

    @Transactional(readOnly = true)
    public WorkOrder findById(UUID id, ActorContext actor, SourceChannel channel) {
        WorkOrder order = support.requireWorkOrder(id);
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_READ, order.siteCode(), channel,
                "WorkOrder", id.toString());
        support.assertVisible(actor, order, channel);
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
        return lifecycle.applyEscalation(order, level, actor, channel);
    }
}
