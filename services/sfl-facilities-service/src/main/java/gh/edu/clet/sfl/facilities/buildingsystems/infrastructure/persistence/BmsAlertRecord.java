package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.CriticalFaultType;
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

/** JPA mapping for {@link BmsAlert}. Column names match V16. */
@Entity
@Table(name = "bms_alerts", schema = "facilities")
public class BmsAlertRecord extends VersionedRecord {

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
    @Enumerated(EnumType.STRING)
    @Column(name = "system_type", length = 30)
    private BuildingSystemType systemType;
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertType type;
    @Enumerated(EnumType.STRING)
    @Column(name = "critical_fault", length = 50)
    private CriticalFaultType criticalFault;
    @Column(name = "rule_id")
    private UUID ruleId;
    @Column(name = "rule_version")
    private Integer ruleVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertPriority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;
    @Column(length = 1000)
    private String summary;
    @Column(name = "evidence_reading_ids", columnDefinition = "text")
    private String evidenceReadingIds;
    @Column(nullable = false)
    private int occurrences;
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;
    @Column(name = "last_occurred_at")
    private Instant lastOccurredAt;
    @Column(name = "cleared_at")
    private Instant clearedAt;
    @Column(name = "work_order_id")
    private UUID workOrderId;
    @Column(name = "work_order_number", length = 60)
    private String workOrderNumber;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected BmsAlertRecord() {
    }

    static BmsAlertRecord empty() {
        return new BmsAlertRecord();
    }

    void apply(BmsAlert alert) {
        id = alert.id();
        siteCode = alert.siteCode();
        deviceId = alert.deviceId();
        avampAssetId = alert.avampAssetId();
        deviceCode = alert.deviceCode();
        buildingCode = alert.buildingCode();
        roomId = alert.roomId();
        locationCode = alert.locationCode();
        systemType = alert.systemType();
        type = alert.type();
        criticalFault = alert.criticalFault();
        ruleId = alert.ruleId();
        ruleVersion = alert.ruleVersion();
        priority = alert.priority();
        status = alert.status();
        summary = alert.summary();
        evidenceReadingIds = IdLists.join(alert.evidenceReadingIds());
        occurrences = alert.occurrences();
        raisedAt = alert.raisedAt();
        lastOccurredAt = alert.lastOccurredAt();
        clearedAt = alert.clearedAt();
        workOrderId = alert.workOrderId();
        workOrderNumber = alert.workOrderNumber();
        metadata = RecordMetadataEmbeddable.from(alert.metadata());
    }

    BmsAlert toDomain() {
        return new BmsAlert(id, siteCode, deviceId, avampAssetId, deviceCode, buildingCode, roomId, locationCode,
                systemType, type, criticalFault, ruleId, ruleVersion, priority, status, summary,
                IdLists.ids(evidenceReadingIds), occurrences, raisedAt, lastOccurredAt, clearedAt, workOrderId,
                workOrderNumber, metadata.toDomain(recordVersion()));
    }
}
