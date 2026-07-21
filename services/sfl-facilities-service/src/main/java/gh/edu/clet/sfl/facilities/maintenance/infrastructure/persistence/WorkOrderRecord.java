package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_orders", schema = "facilities")
public class WorkOrderRecord {

    @Id
    private UUID id;
    @Column(name = "work_order_number", nullable = false, length = 40, unique = true)
    private String workOrderNumber;
    @Column(name = "facility_fault_id", nullable = false)
    private UUID facilityFaultId;
    @Column(name = "fault_number", nullable = false, length = 40)
    private String faultNumber;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "location_code", nullable = false, length = 80)
    private String locationCode;
    @Column(nullable = false, length = 200)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FaultPriority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkOrderStatus status;
    @Column(name = "assigned_to", length = 160)
    private String assignedTo;
    @Column(name = "closure_notes", length = 2000)
    private String closureNotes;
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "assigned_at")
    private Instant assignedAt;
    @Column(name = "closed_at")
    private Instant closedAt;

    protected WorkOrderRecord() {
    }

    private WorkOrderRecord(WorkOrder workOrder) {
        id = workOrder.id();
        workOrderNumber = workOrder.workOrderNumber();
        facilityFaultId = workOrder.facilityFaultId();
        faultNumber = workOrder.faultNumber();
        siteCode = workOrder.siteCode();
        locationCode = workOrder.locationCode();
        title = workOrder.title();
        priority = workOrder.priority();
        status = workOrder.status();
        assignedTo = workOrder.assignedTo();
        closureNotes = workOrder.closureNotes();
        createdBy = workOrder.createdBy();
        createdAt = workOrder.createdAt();
        assignedAt = workOrder.assignedAt();
        closedAt = workOrder.closedAt();
    }

    public static WorkOrderRecord from(WorkOrder workOrder) {
        return new WorkOrderRecord(workOrder);
    }

    public WorkOrder toDomain() {
        return new WorkOrder(id, workOrderNumber, facilityFaultId, faultNumber, siteCode, locationCode, title,
                priority, status, assignedTo, closureNotes, createdBy, createdAt, assignedAt, closedAt);
    }
}