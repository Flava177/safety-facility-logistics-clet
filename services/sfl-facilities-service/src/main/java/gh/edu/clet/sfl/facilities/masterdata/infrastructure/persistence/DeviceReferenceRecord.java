package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_references", schema = "facilities")
public class DeviceReferenceRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "device_code", nullable = false, length = 80)
    private String deviceCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DeviceReferenceType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DeviceOperationalStatus status;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "location_code", length = 120)
    private String locationCode;
    @Column(length = 160)
    private String vendor;
    @Column(name = "external_reference", length = 160)
    private String externalReference;
    @Column(name = "status_reported_at")
    private Instant statusReportedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected DeviceReferenceRecord() {
    }

    private DeviceReferenceRecord(DeviceReference deviceReference) {
        id = deviceReference.id();
        siteCode = deviceReference.siteCode();
        deviceCode = deviceReference.deviceCode();
        name = deviceReference.name();
        type = deviceReference.type();
        status = deviceReference.status();
        roomId = deviceReference.roomId();
        locationCode = deviceReference.locationCode();
        vendor = deviceReference.vendor();
        externalReference = deviceReference.externalReference();
        statusReportedAt = deviceReference.statusReportedAt();
        lifecycleStatus = deviceReference.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(deviceReference.metadata());
    }

    public static DeviceReferenceRecord from(DeviceReference deviceReference) {
        return new DeviceReferenceRecord(deviceReference);
    }

    public DeviceReference toDomain() {
        return new DeviceReference(id, siteCode, deviceCode, name, type, status, roomId, locationCode, vendor,
                externalReference, statusReportedAt, lifecycleStatus, metadata.toDomain());
    }
}
