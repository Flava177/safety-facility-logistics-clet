package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link CleaningTask}. Column names match V19 exactly. */
@Entity
@Table(name = "cleaning_tasks", schema = "facilities")
public class CleaningTaskRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "task_number", nullable = false, length = 40)
    private String taskNumber;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 40)
    private SpaceType spaceType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskOrigin origin;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 2000)
    private String description;
    @Column(name = "schedule_id")
    private UUID scheduleId;
    @Column(name = "occurrence_start")
    private Instant occurrenceStart;
    @Column(name = "booking_id")
    private UUID bookingId;
    @Column(name = "booking_reference", length = 40)
    private String bookingReference;
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Column(name = "event_reference", length = 120)
    private String eventReference;
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    @Column(name = "due_by", nullable = false)
    private Instant dueBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type", length = 20)
    private AssigneeType assigneeType;
    @Column(name = "assigned_to", length = 160)
    private String assignedTo;
    @Column(name = "vendor_id")
    private UUID vendorId;
    @Column(name = "assigned_at")
    private Instant assignedAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "completed_by", length = 160)
    private String completedBy;
    @Column(name = "completion_notes", length = 2000)
    private String completionNotes;
    @Column(name = "vendor_reported_completed_at")
    private Instant vendorReportedCompletedAt;
    @Column(name = "completion_discrepancy_seconds")
    private Long completionDiscrepancySeconds;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "cancellation_reason", length = 2000)
    private String cancellationReason;
    @Column(name = "checklist_template_id")
    private UUID checklistTemplateId;
    @Column(name = "checklist_template_version")
    private Integer checklistTemplateVersion;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningTaskRecord() {
    }

    static CleaningTaskRecord empty() {
        return new CleaningTaskRecord();
    }

    public void apply(CleaningTask task) {
        id = task.id();
        taskNumber = task.taskNumber();
        siteCode = task.siteCode();
        roomId = task.roomId();
        roomCode = task.roomCode();
        spaceType = task.spaceType();
        origin = task.origin();
        title = task.title();
        description = task.description();
        scheduleId = task.scheduleId();
        occurrenceStart = task.occurrenceStart();
        bookingId = task.bookingId();
        bookingReference = task.bookingReference();
        reservationId = task.reservationId();
        eventReference = task.eventReference();
        windowStart = task.windowStart();
        dueBy = task.dueBy();
        status = task.status();
        requestedBy = task.requestedBy();
        requestedAt = task.requestedAt();
        assigneeType = task.assigneeType();
        assignedTo = task.assignedTo();
        vendorId = task.vendorId();
        assignedAt = task.assignedAt();
        startedAt = task.startedAt();
        completedAt = task.completedAt();
        completedBy = task.completedBy();
        completionNotes = task.completionNotes();
        vendorReportedCompletedAt = task.vendorReportedCompletedAt();
        completionDiscrepancySeconds = task.completionDiscrepancySeconds();
        cancelledAt = task.cancelledAt();
        cancellationReason = task.cancellationReason();
        checklistTemplateId = task.checklistTemplateId();
        checklistTemplateVersion = task.checklistTemplateVersion();
        metadata = RecordMetadataEmbeddable.from(task.metadata());
    }

    public CleaningTask toDomain() {
        return new CleaningTask(id, taskNumber, siteCode, roomId, roomCode, spaceType, origin, title, description,
                scheduleId, occurrenceStart, bookingId, bookingReference, reservationId, eventReference, windowStart,
                dueBy, status, requestedBy, requestedAt, assigneeType, assignedTo, vendorId, assignedAt, startedAt,
                completedAt, completedBy, completionNotes, vendorReportedCompletedAt, completionDiscrepancySeconds,
                cancelledAt, cancellationReason, checklistTemplateId, checklistTemplateVersion,
                metadata.toDomain(recordVersion()));
    }
}
