package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff;
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

/** JPA mapping for {@link EventHandoff}. Written once and never changed: the register is append-only. */
@Entity
@Table(name = "event_handoffs", schema = "facilities")
public class EventHandoffRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "s078_event_reference", nullable = false, length = 120)
    private String s078EventReference;
    @Column(name = "s078_status", nullable = false, length = 20)
    private String s078Status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventHandoff.Outcome outcome;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EventHandoff.Action action;
    @Column(name = "setup_task_id")
    private UUID setupTaskId;
    @Column(name = "inbox_id")
    private UUID inboxId;
    @Column(name = "source_system", nullable = false, length = 80)
    private String sourceSystem;
    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;
    @Column(name = "rejection_detail", length = 1000)
    private String rejectionDetail;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EventHandoffRecord() {
    }

    static EventHandoffRecord from(EventHandoff handoff) {
        EventHandoffRecord record = new EventHandoffRecord();
        record.id = handoff.id();
        record.siteCode = handoff.siteCode();
        record.s078EventReference = handoff.s078EventReference();
        record.s078Status = handoff.s078Status();
        record.outcome = handoff.outcome();
        record.action = handoff.action();
        record.setupTaskId = handoff.setupTaskId();
        record.inboxId = handoff.inboxId();
        record.sourceSystem = handoff.sourceSystem();
        record.idempotencyKey = handoff.idempotencyKey();
        record.rejectionDetail = EventResourceRequestRecord.truncate(handoff.rejectionDetail(), 1000);
        record.receivedAt = handoff.receivedAt();
        record.metadata = RecordMetadataEmbeddable.from(RecordMetadata.createdBy(handoff.recordedBy(),
                handoff.receivedAt(), SourceChannel.INTEGRATION, handoff.correlationId()));
        return record;
    }

    EventHandoff toDomain() {
        RecordMetadata provenance = metadata.toDomain(recordVersion());
        return new EventHandoff(id, siteCode, s078EventReference, s078Status, outcome, action, setupTaskId, inboxId,
                sourceSystem, idempotencyKey, rejectionDetail, receivedAt, provenance.createdBy(),
                provenance.correlationId());
    }
}
