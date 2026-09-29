package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.DeliveryOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventReconciliationLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
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

/** JPA mapping for {@link EventReconciliationLine}. Append-only. */
@Entity
@Table(name = "event_reconciliation_lines", schema = "facilities")
public class EventReconciliationLineRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "setup_task_id", nullable = false)
    private UUID setupTaskId;
    @Column(name = "resource_request_id", nullable = false)
    private UUID resourceRequestId;
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private EventResourceType resourceType;
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 30)
    private ResourceRequestStatus requestStatus;
    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;
    @Column(name = "delivered_quantity")
    private Integer deliveredQuantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryOutcome outcome;
    @Column(length = 2000)
    private String notes;
    @Column(name = "recorded_by", nullable = false, length = 160)
    private String recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EventReconciliationLineRecord() {
    }

    static EventReconciliationLineRecord from(EventReconciliationLine line) {
        EventReconciliationLineRecord record = new EventReconciliationLineRecord();
        record.id = line.id();
        record.siteCode = line.siteCode();
        record.setupTaskId = line.setupTaskId();
        record.resourceRequestId = line.resourceRequestId();
        record.resourceType = line.resourceType();
        record.requestStatus = line.requestStatusAtReconciliation();
        record.requestedQuantity = line.requestedQuantity();
        record.deliveredQuantity = line.deliveredQuantity();
        record.outcome = line.outcome();
        record.notes = EventResourceRequestRecord.truncate(line.notes(), 2000);
        record.recordedBy = line.recordedBy();
        record.recordedAt = line.recordedAt();
        record.metadata = RecordMetadataEmbeddable.from(RecordMetadata.createdBy(line.recordedBy(), line.recordedAt(),
                SourceChannel.WEB, line.correlationId()));
        return record;
    }

    EventReconciliationLine toDomain() {
        return new EventReconciliationLine(id, siteCode, setupTaskId, resourceRequestId, resourceType, requestStatus,
                requestedQuantity, deliveredQuantity, outcome, notes, recordedBy, recordedAt,
                metadata.toDomain(recordVersion()).correlationId());
    }
}
