package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderType;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link WorkOrder}. Column names match V9 exactly. */
@Entity
@Table(name = "work_orders", schema = "facilities")
public class WorkOrderRecord {

    @Id
    private UUID id;
    @Column(name = "work_order_number", nullable = false, length = 40)
    private String workOrderNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "work_order_type", nullable = false, length = 20)
    private WorkOrderType workOrderType;
    @Column(name = "facility_fault_id")
    private UUID facilityFaultId;
    @Column(name = "fault_number", length = 40)
    private String faultNumber;
    @Column(name = "schedule_id")
    private UUID scheduleId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "location_code", length = 120)
    private String locationCode;
    @Column(name = "asset_id")
    private UUID assetId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 4000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FaultPriority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkOrderStatus status;
    @Column(name = "assigned_to", length = 160)
    private String assignedTo;
    @Column(name = "vendor_id")
    private UUID vendorId;
    @Column(name = "assigned_at")
    private Instant assignedAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "hold_reason", length = 1000)
    private String holdReason;
    @Column(name = "held_at")
    private Instant heldAt;
    @Column(name = "total_held_seconds", nullable = false)
    private long totalHeldSeconds;
    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    /** When somebody should have started. Null on rows migrated before V12, deliberately. */
    @Column(name = "response_due_at")
    private Instant responseDueAt;

    /** Set once, by the sweep, so an unstarted job is not re-raised every fifteen minutes. */
    @Column(name = "response_escalated_at")
    private Instant responseEscalatedAt;
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;
    @Column(name = "escalated_at")
    private Instant escalatedAt;
    @Column(name = "evidence_required", nullable = false)
    private int evidenceRequired;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "completion_notes", length = 2000)
    private String completionNotes;
    @Column(name = "closure_notes", length = 2000)
    private String closureNotes;
    @Column(name = "closed_by", length = 160)
    private String closedBy;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "cancellation_reason", length = 2000)
    private String cancellationReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected WorkOrderRecord() {
    }

    public static WorkOrderRecord from(WorkOrder order) {
        WorkOrderRecord record = new WorkOrderRecord();
        record.apply(order);
        return record;
    }

    public void apply(WorkOrder order) {
        id = order.id();
        workOrderNumber = order.workOrderNumber();
        workOrderType = order.workOrderType();
        facilityFaultId = order.facilityFaultId();
        faultNumber = order.faultNumber();
        scheduleId = order.scheduleId();
        siteCode = order.siteCode();
        roomId = order.roomId();
        locationCode = order.locationCode();
        assetId = order.assetId();
        title = order.title();
        description = order.description();
        priority = order.priority();
        status = order.status();
        assignedTo = order.assignedTo();
        vendorId = order.vendorId();
        assignedAt = order.assignedAt();
        startedAt = order.startedAt();
        holdReason = order.holdReason();
        heldAt = order.heldAt();
        totalHeldSeconds = order.totalHeldSeconds();
        slaDueAt = order.slaDueAt();
        responseDueAt = order.responseDueAt();
        responseEscalatedAt = order.responseEscalatedAt();
        escalationLevel = order.escalationLevel();
        escalatedAt = order.escalatedAt();
        evidenceRequired = order.evidenceRequired();
        completedAt = order.completedAt();
        completionNotes = order.completionNotes();
        closureNotes = order.closureNotes();
        closedBy = order.closedBy();
        closedAt = order.closedAt();
        cancellationReason = order.cancellationReason();
        lifecycleStatus = order.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(order.metadata());
    }

    public WorkOrder toDomain() {
        return new WorkOrder(id, workOrderNumber, workOrderType, facilityFaultId, faultNumber, scheduleId,
                siteCode, roomId, locationCode, assetId, title, description, priority, status, assignedTo,
                vendorId, assignedAt, startedAt, holdReason, heldAt, totalHeldSeconds, slaDueAt,
                responseDueAt, responseEscalatedAt,
                escalationLevel, escalatedAt, evidenceRequired, completedAt, completionNotes, closureNotes,
                closedBy, closedAt, cancellationReason, lifecycleStatus, metadata.toDomain());
    }

    public UUID getId() {
        return id;
    }
}
