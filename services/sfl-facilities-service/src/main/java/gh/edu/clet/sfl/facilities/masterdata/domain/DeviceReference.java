package gh.edu.clet.sfl.facilities.masterdata.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeviceReference(
        UUID id,
        String siteCode,
        String deviceCode,
        String name,
        DeviceReferenceType type,
        DeviceOperationalStatus status,
        UUID roomId,
        String locationCode,
        String vendor,
        Instant createdAt) {

    public DeviceReference {
        Objects.requireNonNull(id, "id is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(deviceCode, "deviceCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static DeviceReference register(UUID id, String siteCode, String deviceCode, String name,
            DeviceReferenceType type, UUID roomId, String locationCode, String vendor, Instant createdAt) {
        return new DeviceReference(id, Site.normalizeCode(siteCode), Site.normalizeCode(deviceCode), name.strip(),
                type, DeviceOperationalStatus.UNKNOWN, roomId, Site.blankToNull(locationCode),
                Site.blankToNull(vendor), createdAt);
    }
}
