package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceKind;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
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
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link BmsDevice}. Column names match V16. */
@Entity
@Table(name = "bms_devices", schema = "facilities")
public class BmsDeviceRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "device_code", nullable = false, length = 120)
    private String deviceCode;
    @Column(name = "avamp_asset_id", nullable = false, length = 120)
    private String avampAssetId;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "system_type", nullable = false, length = 30)
    private BuildingSystemType systemType;
    @Enumerated(EnumType.STRING)
    @Column(name = "device_kind", nullable = false, length = 20)
    private DeviceKind kind;
    @Column(name = "building_code", nullable = false, length = 80)
    private String buildingCode;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "room_code", length = 80)
    private String roomCode;
    @Column(name = "expected_interval_seconds", nullable = false)
    private int expectedIntervalSeconds;
    @Column(name = "installed_on")
    private LocalDate installedOn;
    @Column(name = "firmware_version", length = 80)
    private String firmwareVersion;
    @Column(name = "firmware_review_due_on")
    private LocalDate firmwareReviewDueOn;
    @Column(name = "warranty_expires_on")
    private LocalDate warrantyExpiresOn;
    @Column(name = "calibration_due_on")
    private LocalDate calibrationDueOn;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "retired_by", length = 160)
    private String retiredBy;
    @Column(name = "retirement_reason", length = 1000)
    private String retirementReason;
    @Column(name = "calibration_reminded_for")
    private LocalDate calibrationRemindedFor;
    @Column(name = "firmware_reminded_for")
    private LocalDate firmwareRemindedFor;
    @Column(name = "warranty_reminded_for")
    private LocalDate warrantyRemindedFor;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected BmsDeviceRecord() {
    }

    static BmsDeviceRecord empty() {
        return new BmsDeviceRecord();
    }

    void apply(BmsDevice device) {
        id = device.id();
        siteCode = device.siteCode();
        deviceCode = device.deviceCode();
        avampAssetId = device.avampAssetId();
        name = device.name();
        systemType = device.systemType();
        kind = device.kind();
        buildingCode = device.buildingCode();
        roomId = device.roomId();
        roomCode = device.roomCode();
        expectedIntervalSeconds = device.expectedIntervalSeconds();
        installedOn = device.installedOn();
        firmwareVersion = device.firmwareVersion();
        firmwareReviewDueOn = device.firmwareReviewDueOn();
        warrantyExpiresOn = device.warrantyExpiresOn();
        calibrationDueOn = device.calibrationDueOn();
        status = device.status();
        retiredAt = device.retiredAt();
        retiredBy = device.retiredBy();
        retirementReason = device.retirementReason();
        calibrationRemindedFor = device.calibrationRemindedFor();
        firmwareRemindedFor = device.firmwareRemindedFor();
        warrantyRemindedFor = device.warrantyRemindedFor();
        metadata = RecordMetadataEmbeddable.from(device.metadata());
    }

    BmsDevice toDomain() {
        return new BmsDevice(id, siteCode, deviceCode, avampAssetId, name, systemType, kind, buildingCode, roomId,
                roomCode, expectedIntervalSeconds, installedOn, firmwareVersion, firmwareReviewDueOn, warrantyExpiresOn,
                calibrationDueOn, status, retiredAt, retiredBy, retirementReason, calibrationRemindedFor,
                firmwareRemindedFor, warrantyRemindedFor, metadata.toDomain(recordVersion()));
    }
}
