package gh.edu.clet.sfl.facilities.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.AuthorizationException;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.FacilityFaultRecord;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.FacilityFaultRepository;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.WorkOrderRecord;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.WorkOrderRepository;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    private static final UUID FAULT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private WorkOrderRepository workOrders;
    @Mock
    private FacilityFaultRepository facilityFaults;
    @Mock
    private ServiceOutbox outbox;

    private WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(workOrders, facilityFaults, outbox,
                Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void authorized_supervisor_can_create_work_order_from_fault() {
        FacilityFault fault = fault();
        when(facilityFaults.findById(FAULT_ID)).thenReturn(Optional.of(FacilityFaultRecord.from(fault)));
        when(workOrders.save(any(WorkOrderRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(facilityFaults.save(any(FacilityFaultRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ActorContext supervisor = actor(SflRole.IFIMP_MAINTENANCE_SUPERVISOR, "MAIN");

        WorkOrder workOrder = service.createFromFault(new CreateWorkOrderFromFaultCommand(FAULT_ID, supervisor));

        assertThat(workOrder.status()).isEqualTo(WorkOrderStatus.OPEN);
        assertThat(workOrder.facilityFaultId()).isEqualTo(FAULT_ID);
        assertThat(workOrder.workOrderNumber()).startsWith("WO-");
        ArgumentCaptor<FacilityFaultRecord> linkedFault = ArgumentCaptor.forClass(FacilityFaultRecord.class);
        verify(facilityFaults).save(linkedFault.capture());
        assertThat(linkedFault.getValue().toDomain().workOrderId()).isEqualTo(workOrder.id());
        verify(outbox).record("sfl.facilities.work-order-created", 1, "WorkOrder", workOrder.id(), "MAIN",
                "corr-1", "actor@sfl.local", workOrder);
    }

    @Test
    void authorized_supervisor_can_assign_work_order() {
        WorkOrder workOrder = workOrder(WorkOrderStatus.OPEN, null);
        when(workOrders.findById(WORK_ORDER_ID)).thenReturn(Optional.of(WorkOrderRecord.from(workOrder)));
        when(workOrders.save(any(WorkOrderRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder assigned = service.assign(new AssignWorkOrderCommand(WORK_ORDER_ID, "tech@sfl.local",
                actor(SflRole.IFIMP_MAINTENANCE_SUPERVISOR, "MAIN")));

        assertThat(assigned.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
        assertThat(assigned.assignedTo()).isEqualTo("tech@sfl.local");
    }

    @Test
    void technician_cannot_assign_work_order() {
        when(workOrders.findById(WORK_ORDER_ID)).thenReturn(Optional.of(WorkOrderRecord.from(workOrder(WorkOrderStatus.OPEN, null))));

        assertThatThrownBy(() -> service.assign(new AssignWorkOrderCommand(WORK_ORDER_ID, "tech@sfl.local",
                actor(SflRole.IFIMP_TECHNICIAN, "MAIN"))))
                .isInstanceOf(AuthorizationException.class)
                .hasMessage("Actor does not have a required role");
    }

    @Test
    void technician_can_close_work_order() {
        when(workOrders.findById(WORK_ORDER_ID)).thenReturn(Optional.of(WorkOrderRecord.from(
                workOrder(WorkOrderStatus.ASSIGNED, "tech@sfl.local"))));
        when(workOrders.save(any(WorkOrderRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder closed = service.close(new CloseWorkOrderCommand(WORK_ORDER_ID, "Repaired and verified",
                actor(SflRole.IFIMP_TECHNICIAN, "MAIN")));

        assertThat(closed.status()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(closed.closureNotes()).isEqualTo("Repaired and verified");
    }

    @Test
    void requester_cannot_create_work_order() {
        when(facilityFaults.findById(FAULT_ID)).thenReturn(Optional.of(FacilityFaultRecord.from(fault())));

        assertThatThrownBy(() -> service.createFromFault(new CreateWorkOrderFromFaultCommand(FAULT_ID,
                actor(SflRole.IFIMP_REQUESTER, "MAIN"))))
                .isInstanceOf(AuthorizationException.class)
                .hasMessage("Actor does not have a required role");
    }

    @Test
    void user_without_site_access_is_rejected() {
        when(facilityFaults.findById(FAULT_ID)).thenReturn(Optional.of(FacilityFaultRecord.from(fault())));

        assertThatThrownBy(() -> service.createFromFault(new CreateWorkOrderFromFaultCommand(FAULT_ID,
                actor(SflRole.IFIMP_MAINTENANCE_SUPERVISOR, "HQ"))))
                .isInstanceOf(AuthorizationException.class)
                .hasMessage("Actor cannot access site: MAIN");
    }

    private FacilityFault fault() {
        return FacilityFault.report(FAULT_ID, "FLT-0001", "MAIN", "BLD-A-101",
                "Air conditioner failed", "The unit is not cooling", "HVAC", FaultPriority.HIGH,
                "staff@sfl.local", Instant.parse("2026-07-13T07:30:00Z"));
    }

    private WorkOrder workOrder(WorkOrderStatus status, String assignedTo) {
        WorkOrder open = WorkOrder.createFromFault(WORK_ORDER_ID, "WO-0001", fault(), "supervisor@sfl.local",
                Instant.parse("2026-07-13T08:00:00Z"));
        if (status == WorkOrderStatus.OPEN) {
            return open;
        }
        WorkOrder assigned = open.assignTo(assignedTo == null ? "tech@sfl.local" : assignedTo,
                Instant.parse("2026-07-13T09:00:00Z"));
        if (status == WorkOrderStatus.ASSIGNED) {
            return assigned;
        }
        return assigned.close("Done", Instant.parse("2026-07-13T10:00:00Z"));
    }

    private ActorContext actor(SflRole role, String site) {
        return new ActorContext(new SiteScopedPrincipal("actor@sfl.local", "Actor", Set.of(role), Set.of(site), false),
                "corr-1");
    }
}