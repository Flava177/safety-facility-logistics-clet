package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.PreventiveMaintenanceSchedule;
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
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link PreventiveMaintenanceSchedule}. */
@Entity
@Table(name = "preventive_schedules", schema = "facilities")
public class PreventiveScheduleRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "schedule_code", nullable = false, length = 80)
    private String scheduleCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 2000)
    private String description;
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "interval_days", nullable = false)
    private int intervalDays;
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FaultPriority priority;
    @Enumerated(EnumType.STRING)
    @Column(name = "work_order_type", nullable = false, length = 20)
    private WorkOrderType workOrderType;
    @Column(name = "next_due_on", nullable = false)
    private LocalDate nextDueOn;
    @Column(name = "last_generated_for")
    private LocalDate lastGeneratedFor;
    @Column(name = "last_generated_at")
    private Instant lastGeneratedAt;
    @Column(name = "last_work_order_id")
    private UUID lastWorkOrderId;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected PreventiveScheduleRecord() {
    }

    public static PreventiveScheduleRecord from(PreventiveMaintenanceSchedule schedule) {
        PreventiveScheduleRecord record = new PreventiveScheduleRecord();
        record.apply(schedule);
        return record;
    }

    public void apply(PreventiveMaintenanceSchedule schedule) {
        id = schedule.id();
        siteCode = schedule.siteCode();
        scheduleCode = schedule.scheduleCode();
        name = schedule.name();
        description = schedule.description();
        assetId = schedule.assetId();
        roomId = schedule.roomId();
        intervalDays = schedule.intervalDays();
        leadTimeDays = schedule.leadTimeDays();
        priority = schedule.priority();
        workOrderType = schedule.workOrderType();
        nextDueOn = schedule.nextDueOn();
        lastGeneratedFor = schedule.lastGeneratedFor();
        lastGeneratedAt = schedule.lastGeneratedAt();
        lastWorkOrderId = schedule.lastWorkOrderId();
        lifecycleStatus = schedule.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(schedule.metadata());
    }

    public PreventiveMaintenanceSchedule toDomain() {
        return new PreventiveMaintenanceSchedule(id, siteCode, scheduleCode, name, description, assetId, roomId,
                intervalDays, leadTimeDays, priority, workOrderType, nextDueOn, lastGeneratedFor,
                lastGeneratedAt, lastWorkOrderId, lifecycleStatus, metadata.toDomain());
    }
}
