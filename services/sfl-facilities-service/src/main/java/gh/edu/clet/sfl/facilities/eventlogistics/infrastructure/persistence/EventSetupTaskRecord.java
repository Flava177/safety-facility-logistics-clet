package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventDetails;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus;
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

/** JPA mapping for {@link EventSetupTask}. Column names match V20 exactly. */
@Entity
@Table(name = "event_setup_tasks", schema = "facilities")
public class EventSetupTaskRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "task_reference", nullable = false, length = 40)
    private String taskReference;
    @Column(name = "s078_event_reference", nullable = false, length = 120)
    private String s078EventReference;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(name = "event_category", nullable = false, length = 60)
    private String eventCategory;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Column(name = "expected_attendance", nullable = false)
    private int expectedAttendance;
    @Column(name = "stated_requirements", length = 4000)
    private String statedRequirements;
    @Column(name = "external_contractors", nullable = false)
    private boolean externalContractors;
    @Column(name = "temporary_structures", nullable = false)
    private boolean temporaryStructures;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventSetupTaskStatus status;
    @Column(name = "coordinator_id", length = 160)
    private String coordinatorId;
    @Column(name = "risk_assessment_id", length = 120)
    private String riskAssessmentId;
    @Column(name = "risk_assessment_version")
    private Integer riskAssessmentVersion;
    @Column(name = "risk_assessment_linked_by", length = 160)
    private String riskAssessmentLinkedBy;
    @Column(name = "risk_assessment_linked_at")
    private Instant riskAssessmentLinkedAt;
    @Column(name = "confirmed_by", length = 160)
    private String confirmedBy;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "completed_by", length = 160)
    private String completedBy;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "closure_reason", length = 2000)
    private String closureReason;
    @Column(name = "handoff_count", nullable = false)
    private int handoffCount;
    @Column(name = "last_handoff_at", nullable = false)
    private Instant lastHandoffAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EventSetupTaskRecord() {
    }

    static EventSetupTaskRecord empty() {
        return new EventSetupTaskRecord();
    }

    void apply(EventSetupTask task) {
        id = task.id();
        siteCode = task.siteCode();
        taskReference = task.taskReference();
        s078EventReference = task.s078EventReference();
        EventDetails details = task.details();
        title = details.title();
        eventCategory = details.eventCategory();
        startsAt = details.startsAt();
        endsAt = details.endsAt();
        roomId = details.roomId();
        roomCode = details.roomCode();
        expectedAttendance = details.expectedAttendance();
        statedRequirements = details.statedRequirements();
        externalContractors = details.externalContractors();
        temporaryStructures = details.temporaryStructures();
        status = task.status();
        coordinatorId = task.coordinatorId();
        riskAssessmentId = task.riskAssessmentId();
        riskAssessmentVersion = task.riskAssessmentVersion();
        riskAssessmentLinkedBy = task.riskAssessmentLinkedBy();
        riskAssessmentLinkedAt = task.riskAssessmentLinkedAt();
        confirmedBy = task.confirmedBy();
        confirmedAt = task.confirmedAt();
        completedBy = task.completedBy();
        completedAt = task.completedAt();
        closureReason = task.closureReason();
        handoffCount = task.handoffCount();
        lastHandoffAt = task.lastHandoffAt();
        metadata = RecordMetadataEmbeddable.from(task.metadata());
    }

    EventSetupTask toDomain() {
        return new EventSetupTask(id, siteCode, taskReference, s078EventReference,
                new EventDetails(title, eventCategory, startsAt, endsAt, roomId, roomCode, expectedAttendance,
                        statedRequirements, externalContractors, temporaryStructures),
                status, coordinatorId, riskAssessmentId, riskAssessmentVersion, riskAssessmentLinkedBy,
                riskAssessmentLinkedAt, confirmedBy, confirmedAt, completedBy, completedAt, closureReason,
                handoffCount, lastHandoffAt, metadata.toDomain(recordVersion()));
    }
}
