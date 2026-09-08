package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderPart;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.UUID;

/**
 * Parts consumed on a work order - recording and removing them. Split out of
 * {@link WorkOrderApplicationService} for the same reason {@link WorkOrderLifecycleCommands} was;
 * see that class and {@link WorkOrderSupport} for the shared pieces.
 */
final class WorkOrderPartCommands {

    private final MaintenanceRepository maintenance;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final WorkOrderSupport support;

    WorkOrderPartCommands(MaintenanceRepository maintenance, FacilitiesAuthorization authorization,
            AuditPort audit, WorkOrderSupport support) {
        this.maintenance = maintenance;
        this.authorization = authorization;
        this.audit = audit;
        this.support = support;
    }

    WorkOrderPart record(MaintenanceCommands.RecordPart command) {
        ActorContext actor = command.actor();
        WorkOrder order = support.requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_UPDATE, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        support.assertVisible(actor, order, command.channel());
        if (!order.status().isOpen()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Parts cannot be recorded against a " + order.status() + " work order.");
        }

        WorkOrderPart part = maintenance.savePart(WorkOrderPart.record(UUID.randomUUID(), order.id(),
                command.partCode(), command.description(), command.quantity(), command.unitCost(),
                command.currency(), command.supplier(), actor.actorId(), support.now()));
        audit.record(actor, command.channel(), AuditAction.WORK_ORDER_PART_RECORDED, "WorkOrderPart",
                part.id().toString(), order.siteCode(), null, part);
        return part;
    }

    void remove(MaintenanceCommands.RemovePart command) {
        ActorContext actor = command.actor();
        WorkOrder order = support.requireWorkOrder(command.workOrderId());
        authorization.require(actor, SflPermission.FACILITIES_WORK_ORDER_UPDATE, order.siteCode(),
                command.channel(), "WorkOrder", order.id().toString());
        support.assertVisible(actor, order, command.channel());
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
}
