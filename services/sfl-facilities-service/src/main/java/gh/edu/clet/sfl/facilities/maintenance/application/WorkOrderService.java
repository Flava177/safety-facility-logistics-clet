package gh.edu.clet.sfl.facilities.maintenance.application;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.AuthorizationPolicy;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.FacilityFaultRecord;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.FacilityFaultRepository;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.WorkOrderRecord;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.WorkOrderRepository;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderService {

    private static final Set<SflRole> CREATE_ROLES = Set.of(
            SflRole.SFL_ADMIN,
            SflRole.FACILITIES_MANAGER,
            SflRole.IFIMP_MAINTENANCE_SUPERVISOR);
    private static final Set<SflRole> ASSIGN_ROLES = CREATE_ROLES;
    private static final Set<SflRole> CLOSE_ROLES = Set.of(
            SflRole.SFL_ADMIN,
            SflRole.IFIMP_MAINTENANCE_SUPERVISOR,
            SflRole.IFIMP_TECHNICIAN,
            SflRole.VENDOR_TECHNICIAN);
    private static final Set<SflRole> READ_ROLES = Set.of(
            SflRole.SFL_ADMIN,
            SflRole.FACILITIES_DIRECTOR,
            SflRole.FACILITIES_MANAGER,
            SflRole.IFIMP_MAINTENANCE_SUPERVISOR,
            SflRole.IFIMP_TECHNICIAN,
            SflRole.VENDOR_TECHNICIAN,
            SflRole.AUDITOR,
            SflRole.COMMAND_ROLE);

    private final WorkOrderRepository workOrders;
    private final FacilityFaultRepository facilityFaults;
    private final ServiceOutbox outbox;
    private final Clock clock;
    private final AuthorizationPolicy authorization;

    /**
     * The constructor Spring uses.
     *
     * <p>Explicitly annotated because this class has two constructors and neither was marked. Spring
     * considers non-public constructors as candidates too, so with two unannotated it falls back to a
     * no-arg constructor that does not exist — the service could not start at all. Found the first
     * time the facilities service was run against a real database, during the S152 build.
     */
    @Autowired
    public WorkOrderService(WorkOrderRepository workOrders, FacilityFaultRepository facilityFaults,
            ServiceOutbox outbox, Clock clock) {
        this(workOrders, facilityFaults, outbox, clock, new AuthorizationPolicy());
    }

    /** The seam the unit tests use to inject an authorisation policy. */
    WorkOrderService(WorkOrderRepository workOrders, FacilityFaultRepository facilityFaults,
            ServiceOutbox outbox, Clock clock, AuthorizationPolicy authorization) {
        this.workOrders = workOrders;
        this.facilityFaults = facilityFaults;
        this.outbox = outbox;
        this.clock = clock;
        this.authorization = authorization;
    }

    @Transactional
    public WorkOrder createFromFault(CreateWorkOrderFromFaultCommand command) {
        FacilityFault fault = facilityFaults.findById(command.facilityFaultId())
                .map(FacilityFaultRecord::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Facility fault was not found: "
                        + command.facilityFaultId()));
        authorization.requireAnyRole(command.actor(), CREATE_ROLES);
        authorization.requireSiteAccess(command.actor(), fault.siteCode());
        if (fault.workOrderId() != null) {
            throw new IllegalStateException("Facility fault already has a work order");
        }

        UUID id = UUID.randomUUID();
        WorkOrder workOrder = WorkOrder.createFromFault(id, workOrderNumber(id), fault,
                command.actor().actorId(), clock.instant());
        WorkOrder saved = workOrders.save(WorkOrderRecord.from(workOrder)).toDomain();
        facilityFaults.save(FacilityFaultRecord.from(fault.linkWorkOrder(saved.id())));
        record("sfl.facilities.work-order-created", saved, command.actor());
        return saved;
    }

    @Transactional
    public WorkOrder assign(AssignWorkOrderCommand command) {
        WorkOrder workOrder = requireWorkOrder(command.workOrderId());
        authorization.requireAnyRole(command.actor(), ASSIGN_ROLES);
        authorization.requireSiteAccess(command.actor(), workOrder.siteCode());
        WorkOrder saved = workOrders.save(WorkOrderRecord.from(workOrder.assignTo(command.assignedTo(), clock.instant())))
                .toDomain();
        record("sfl.facilities.work-order-assigned", saved, command.actor());
        return saved;
    }

    @Transactional
    public WorkOrder close(CloseWorkOrderCommand command) {
        WorkOrder workOrder = requireWorkOrder(command.workOrderId());
        authorization.requireAnyRole(command.actor(), CLOSE_ROLES);
        authorization.requireSiteAccess(command.actor(), workOrder.siteCode());
        WorkOrder saved = workOrders.save(WorkOrderRecord.from(workOrder.close(command.closureNotes(), clock.instant())))
                .toDomain();
        record("sfl.facilities.work-order-closed", saved, command.actor());
        return saved;
    }

    @Transactional(readOnly = true)
    public WorkOrder findById(UUID id, ActorContext actor) {
        WorkOrder workOrder = requireWorkOrder(id);
        authorization.requireAnyRole(actor, READ_ROLES);
        authorization.requireSiteAccess(actor, workOrder.siteCode());
        return workOrder;
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> findAll(ActorContext actor) {
        authorization.requireAnyRole(actor, READ_ROLES);
        return workOrders.findAllByOrderByCreatedAtDesc().stream()
                .map(WorkOrderRecord::toDomain)
                .filter(workOrder -> authorization.canAccessSite(actor, workOrder.siteCode()))
                .toList();
    }

    private WorkOrder requireWorkOrder(UUID id) {
        return workOrders.findById(id)
                .map(WorkOrderRecord::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Work order was not found: " + id));
    }

    private void record(String eventType, WorkOrder workOrder, ActorContext actor) {
        outbox.record(eventType, 1, "WorkOrder", workOrder.id(), workOrder.siteCode(), actor.correlationId(),
                actor.actorId(), workOrder);
    }

    private String workOrderNumber(UUID id) {
        return "WO-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}