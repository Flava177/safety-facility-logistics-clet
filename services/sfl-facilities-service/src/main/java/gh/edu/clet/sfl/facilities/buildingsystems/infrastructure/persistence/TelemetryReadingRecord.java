package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.TelemetryReading;
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

/** JPA mapping for {@link TelemetryReading}. Column names match V16. */
@Entity
@Table(name = "bms_telemetry_readings", schema = "facilities")
public class TelemetryReadingRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;
    @Column(name = "avamp_asset_id", nullable = false, length = 120)
    private String avampAssetId;
    @Column(name = "device_code", nullable = false, length = 120)
    private String deviceCode;
    @Column(name = "building_code", nullable = false, length = 80)
    private String buildingCode;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "location_code", nullable = false, length = 80)
    private String locationCode;
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
    @Column(name = "released_from_quarantine_id")
    private UUID releasedFromQuarantineId;
    @Column(name = "evidence_hold", nullable = false)
    private boolean evidenceHold;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected TelemetryReadingRecord() {
    }

    static TelemetryReadingRecord of(TelemetryReading reading) {
        TelemetryReadingRecord record = new TelemetryReadingRecord();
        record.id = reading.id();
        record.siteCode = reading.siteCode();
        record.deviceId = reading.deviceId();
        record.avampAssetId = reading.avampAssetId();
        record.deviceCode = reading.deviceCode();
        record.buildingCode = reading.buildingCode();
        record.roomId = reading.roomId();
        record.locationCode = reading.locationCode();
        record.channel = reading.channel();
        record.quantity = reading.quantity();
        record.value = reading.value();
        record.observedAt = reading.observedAt();
        record.receivedAt = reading.receivedAt();
        record.sourceId = reading.sourceId();
        record.format = reading.format();
        record.idempotencyKey = reading.idempotencyKey();
        record.itemIndex = reading.itemIndex();
        record.inboxId = reading.inboxId();
        record.releasedFromQuarantineId = reading.releasedFromQuarantineId();
        record.evidenceHold = reading.evidenceHold();
        record.metadata = RecordMetadataEmbeddable.from(reading.metadata());
        return record;
    }

    void holdAsEvidence() {
        evidenceHold = true;
    }

    TelemetryReading toDomain() {
        return new TelemetryReading(id, siteCode, deviceId, avampAssetId, deviceCode, buildingCode, roomId,
                locationCode, channel, quantity, value, observedAt, receivedAt, sourceId, format, idempotencyKey,
                itemIndex, inboxId, releasedFromQuarantineId, evidenceHold, metadata.toDomain(recordVersion()));
    }
}
