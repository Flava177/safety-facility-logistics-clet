package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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
        createdAt = deviceReference.createdAt();
    }

    public static DeviceReferenceRecord from(DeviceReference deviceReference) {
        return new DeviceReferenceRecord(deviceReference);
    }

    public DeviceReference toDomain() {
        return new DeviceReference(id, siteCode, deviceCode, name, type, status, roomId, locationCode, vendor,
                createdAt);
    }
}
