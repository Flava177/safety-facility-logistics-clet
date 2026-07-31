package gh.edu.clet.sfl.facilities.masterdata.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A stable reference to a device a vendor system operates (SRS-SFL-S152-01, -04).
 *
 * <p>S152 does not run cameras, readers or panels — those are Buy-and-Integrate systems. What it owns
 * is the <em>identity and location</em> of each device, so that a CCTV event, an access denial or a
 * fire alarm can be placed in a room and a zone without every consuming system inventing its own
 * device registry.
 *
 * <p>{@code externalReference} is the vendor's own identifier. Held as a value, never as a foreign
 * key: the vendor's database is not ours to join against, and a renumbering on their side must not
 * break our estate.
 */
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
        String externalReference,
        Instant statusReportedAt,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public DeviceReference {
        Objects.requireNonNull(id, "id is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(deviceCode, "deviceCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /** Registers a device. Status starts {@code UNKNOWN} — no vendor feed has reported on it yet. */
    public static DeviceReference register(UUID id, String siteCode, String deviceCode, String name,
            DeviceReferenceType type, UUID roomId, String locationCode, String vendor, String externalReference,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new DeviceReference(id, Site.normalizeCode(siteCode), Site.normalizeCode(deviceCode), name.strip(),
                type, DeviceOperationalStatus.UNKNOWN, roomId, Site.blankToNull(locationCode),
                Site.blankToNull(vendor), Site.blankToNull(externalReference), null,
                RecordLifecycleStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Records what a vendor feed reported.
     *
     * <p>{@code statusReportedAt} is the vendor's own observation time where supplied, not our receipt
     * time — the dashboard's staleness warning is about how old the *observation* is, and using
     * receipt time would report a six-hour-old reading as fresh.
     */
    public DeviceReference reportStatus(DeviceOperationalStatus newStatus, Instant reportedAt, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        Objects.requireNonNull(newStatus, "status is required");
        return new DeviceReference(id, siteCode, deviceCode, name, type, newStatus, roomId, locationCode, vendor,
                externalReference, reportedAt == null ? at : reportedAt, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Moves the device to another space, or off the estate map when {@code newRoomId} is null. */
    public DeviceReference relocate(UUID newRoomId, String newLocationCode, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new DeviceReference(id, siteCode, deviceCode, name, type, status, newRoomId,
                newLocationCode == null ? locationCode : Site.blankToNull(newLocationCode), vendor,
                externalReference, statusReportedAt, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public DeviceReference changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new DeviceReference(id, siteCode, deviceCode, name, type, status, roomId, locationCode, vendor,
                externalReference, statusReportedAt, lifecycleStatus.transitionTo(target, "Device reference"),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Creation time, preserved for the pre-S152 API shape. */
    public Instant createdAt() {
        return metadata.createdAt();
    }
}
