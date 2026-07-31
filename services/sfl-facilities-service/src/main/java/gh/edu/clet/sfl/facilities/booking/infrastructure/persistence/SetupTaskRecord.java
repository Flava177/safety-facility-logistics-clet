package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.domain.SetupTask;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link SetupTask}. Column names match V10 exactly. */
@Entity
@Table(name = "booking_setup_tasks", schema = "facilities")
public class SetupTaskRecord {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(name = "due_by", nullable = false)
    private Instant dueBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SetupTaskStatus status;
    @Column(name = "assigned_to", length = 160)
    private String assignedTo;
    @Column(name = "completed_by", length = 160)
    private String completedBy;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(length = 2000)
    private String notes;

    protected SetupTaskRecord() {
    }

    public void apply(SetupTask task) {
        id = task.id();
        bookingId = task.bookingId();
        roomId = task.roomId();
        siteCode = task.siteCode();
        description = task.description();
        dueBy = task.dueBy();
        status = task.status();
        assignedTo = task.assignedTo();
        completedBy = task.completedBy();
        completedAt = task.completedAt();
        notes = task.notes();
    }

    public SetupTask toDomain() {
        return new SetupTask(id, bookingId, roomId, siteCode, description, dueBy, status, assignedTo,
                completedBy, completedAt, notes);
    }
}
