package gh.edu.clet.sfl.facilities.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkOrderTest {

    @Test
    void creates_work_order_from_facility_fault_in_open_state() {
        FacilityFault fault = fault();

        WorkOrder workOrder = WorkOrder.createFromFault(UUID.randomUUID(), "WO-0001", fault,
                "supervisor@sfl.local", Instant.parse("2026-07-13T08:00:00Z"));

        assertThat(workOrder.facilityFaultId()).isEqualTo(fault.id());
        assertThat(workOrder.siteCode()).isEqualTo("MAIN");
        assertThat(workOrder.status()).isEqualTo(WorkOrderStatus.OPEN);
        assertThat(workOrder.assignedTo()).isNull();
    }

    @Test
    void assigns_and_closes_work_order() {
        WorkOrder workOrder = WorkOrder.createFromFault(UUID.randomUUID(), "WO-0001", fault(),
                "supervisor@sfl.local", Instant.parse("2026-07-13T08:00:00Z"));

        WorkOrder assigned = workOrder.assignTo("technician@sfl.local", Instant.parse("2026-07-13T09:00:00Z"));
        WorkOrder closed = assigned.close("AC unit repaired and tested", Instant.parse("2026-07-13T10:00:00Z"));

        assertThat(assigned.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
        assertThat(assigned.assignedTo()).isEqualTo("technician@sfl.local");
        assertThat(closed.status()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(closed.closureNotes()).isEqualTo("AC unit repaired and tested");
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    void does_not_assign_closed_work_order() {
        WorkOrder closed = WorkOrder.createFromFault(UUID.randomUUID(), "WO-0001", fault(),
                "supervisor@sfl.local", Instant.parse("2026-07-13T08:00:00Z"))
                .close("Done", Instant.parse("2026-07-13T10:00:00Z"));

        assertThatThrownBy(() -> closed.assignTo("technician@sfl.local", Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Closed work orders cannot be assigned");
    }

    private FacilityFault fault() {
        return FacilityFault.report(UUID.randomUUID(), "FLT-0001", "MAIN", "BLD-A-101",
                "Air conditioner failed", "The unit is not cooling", "HVAC", FaultPriority.HIGH,
                "staff@sfl.local", Instant.parse("2026-07-13T07:30:00Z"));
    }
}