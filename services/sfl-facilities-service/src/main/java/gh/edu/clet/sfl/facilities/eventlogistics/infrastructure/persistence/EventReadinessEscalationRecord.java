package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessEscalation;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
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

/** JPA mapping for {@link ReadinessEscalation}. Append-only. */
@Entity
@Table(name = "event_readiness_escalations", schema = "facilities")
public class EventReadinessEscalationRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "setup_task_id", nullable = false)
    private UUID setupTaskId;
    @Column(name = "resource_request_id", nullable = false)
    private UUID resourceRequestId;
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 30)
    private ResourceRequestStatus requestStatus;
    @Column(name = "event_starts_at", nullable = false)
    private Instant eventStartsAt;
    @Column(name = "escalated_at", nullable = false)
    private Instant escalatedAt;
    @Column(name = "window_minutes", nullable = false)
    private long windowMinutes;
    @Column(name = "notified_to", nullable = false, length = 160)
    private String notifiedTo;
    @Column(name = "notification_status", nullable = false, length = 20)
    private String notificationStatus;
    @Column(name = "before_event_start", nullable = false)
    private boolean beforeEventStart;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EventReadinessEscalationRecord() {
    }

    static EventReadinessEscalationRecord from(ReadinessEscalation escalation) {
        EventReadinessEscalationRecord record = new EventReadinessEscalationRecord();
        record.id = escalation.id();
        record.siteCode = escalation.siteCode();
        record.setupTaskId = escalation.setupTaskId();
        record.resourceRequestId = escalation.resourceRequestId();
        record.requestStatus = escalation.requestStatus();
        record.eventStartsAt = escalation.eventStartsAt();
        record.escalatedAt = escalation.escalatedAt();
        record.windowMinutes = escalation.windowMinutes();
        record.notifiedTo = escalation.notifiedTo();
        record.notificationStatus = escalation.notificationStatus();
        record.beforeEventStart = escalation.beforeEventStart();
        record.metadata = RecordMetadataEmbeddable.from(RecordMetadata.createdBy(escalation.recordedBy(),
                escalation.escalatedAt(), SourceChannel.SCHEDULER, escalation.correlationId()));
        return record;
    }

    ReadinessEscalation toDomain() {
        RecordMetadata provenance = metadata.toDomain(recordVersion());
        return new ReadinessEscalation(id, siteCode, setupTaskId, resourceRequestId, requestStatus, eventStartsAt,
                escalatedAt, windowMinutes, notifiedTo, notificationStatus, beforeEventStart, provenance.createdBy(),
                provenance.correlationId());
    }
}
