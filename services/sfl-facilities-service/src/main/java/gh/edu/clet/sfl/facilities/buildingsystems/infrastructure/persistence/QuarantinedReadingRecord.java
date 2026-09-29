package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineReason;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantinedReading;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link QuarantinedReading}. Column names match V16. */
@Entity
@Table(name = "bms_quarantined_readings", schema = "facilities")
public class QuarantinedReadingRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "source_system", length = 80)
    private String sourceId;
    @Column(name = "message_format", length = 80)
    private String format;
    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;
    @Column(name = "item_index", nullable = false)
    private int itemIndex;
    @Column(name = "inbox_id")
    private UUID inboxId;
    @Column(name = "device_code", nullable = false, length = 120)
    private String deviceCode;
    @Column(nullable = false, length = 160)
    private String channel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MeasuredQuantity quantity;
    @Column(name = "reading_value", nullable = false, precision = 20, scale = 6)
    private BigDecimal value;
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuarantineReason reason;
    @Column(length = 1000)
    private String detail;
    @Column(name = "device_id")
    private UUID deviceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuarantineStatus status;
    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;
    @Column(name = "released_reading_id")
    private UUID releasedReadingId;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected QuarantinedReadingRecord() {
    }

    static QuarantinedReadingRecord empty() {
        return new QuarantinedReadingRecord();
    }

    void apply(QuarantinedReading reading) {
        id = reading.id();
        siteCode = reading.siteCode();
        sourceId = reading.sourceId();
        format = reading.format();
        idempotencyKey = reading.idempotencyKey();
        itemIndex = reading.itemIndex();
        inboxId = reading.inboxId();
        deviceCode = reading.deviceCode();
        channel = reading.channel();
        quantity = reading.quantity();
        value = reading.value();
        observedAt = reading.observedAt();
        receivedAt = reading.receivedAt();
        reason = reading.reason();
        detail = reading.detail();
        deviceId = reading.deviceId();
        status = reading.status();
        resolvedBy = reading.resolvedBy();
        resolvedAt = reading.resolvedAt();
        resolutionNote = reading.resolutionNote();
        releasedReadingId = reading.releasedReadingId();
        metadata = RecordMetadataEmbeddable.from(reading.metadata());
    }

    QuarantinedReading toDomain() {
        return new QuarantinedReading(id, siteCode, sourceId, format, idempotencyKey, itemIndex, inboxId, deviceCode,
                channel, quantity, value, observedAt, receivedAt, reason, detail, deviceId, status, resolvedBy,
                resolvedAt, resolutionNote, releasedReadingId, metadata.toDomain(recordVersion()));
    }
}
