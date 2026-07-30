package gh.edu.clet.sfl.facilities.masterdata.api;

import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMembership;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The API's view of the estate.
 *
 * <p>Explicit response types rather than serialising the aggregates. The pre-S152 controller returned
 * domain records straight onto the wire, which meant every field added to an aggregate silently became
 * part of the public contract — and the aggregates have just grown a great deal.
 *
 * <p>{@code createdAt} and {@code active} are preserved on the shapes that had them so the existing
 * facilities dashboard page keeps working against the same JSON.
 */
public final class FacilitiesResponses {

    private FacilitiesResponses() {
    }

    /** The system-managed fields SRS-SFL-S152-01 requires, exposed once and reused. */
    public record Metadata(
            String createdBy,
            Instant createdAt,
            String lastModifiedBy,
            Instant lastModifiedAt,
            long version,
            SourceChannel sourceChannel,
            String correlationId) {

        public static Metadata from(RecordMetadata metadata) {
            return new Metadata(metadata.createdBy(), metadata.createdAt(), metadata.lastModifiedBy(),
                    metadata.lastModifiedAt(), metadata.version(), metadata.sourceChannel(),
                    metadata.correlationId());
        }
    }

    public record SiteResponse(
            UUID id,
            String siteCode,
            String name,
            String description,
            boolean active,
            RecordLifecycleStatus lifecycleStatus,
            OperatingMode operatingMode,
            Instant operatingModeChangedAt,
            String operatingModeChangedBy,
            Instant createdAt,
            Metadata metadata) {

        public static SiteResponse from(Site site) {
            return new SiteResponse(site.id(), site.siteCode(), site.name(), site.description(), site.active(),
                    site.lifecycleStatus(), site.operatingMode(), site.operatingModeChangedAt(),
                    site.operatingModeChangedBy(), site.metadata().createdAt(),
                    Metadata.from(site.metadata()));
        }
    }

    public record BuildingResponse(
            UUID id,
            UUID siteId,
            String siteCode,
            String buildingCode,
            String name,
            String description,
            RecordLifecycleStatus lifecycleStatus,
            Instant createdAt,
            Metadata metadata) {

        public static BuildingResponse from(Building building) {
            return new BuildingResponse(building.id(), building.siteId(), building.siteCode(),
                    building.buildingCode(), building.name(), building.description(),
                    building.lifecycleStatus(), building.createdAt(), Metadata.from(building.metadata()));
        }
    }

    public record FloorResponse(
            UUID id,
            UUID buildingId,
            String siteCode,
            String floorCode,
            String name,
            Integer levelNumber,
            RecordLifecycleStatus lifecycleStatus,
            Instant createdAt,
            Metadata metadata) {

        public static FloorResponse from(FacilityFloor floor) {
            return new FloorResponse(floor.id(), floor.buildingId(), floor.siteCode(), floor.floorCode(),
                    floor.name(), floor.levelNumber(), floor.lifecycleStatus(), floor.createdAt(),
                    Metadata.from(floor.metadata()));
        }
    }

    /**
     * A space.
     *
     * <p>{@code availableForBooking} and {@code availableForExamination} are derived and returned rather
     * than left for the caller to recompute — the rule combines three fields, and a client that got it
     * subtly wrong would offer a blocked hall for an examination.
     */
    public record RoomResponse(
            UUID id,
            UUID floorId,
            String siteCode,
            String roomCode,
            String name,
            SpaceType spaceType,
            String roomType,
            Integer capacity,
            BigDecimal areaSqm,
            String costCentre,
            boolean bookable,
            boolean examinationCapable,
            boolean availableForBooking,
            boolean availableForExamination,
            LocationReadinessStatus readinessStatus,
            String readinessNotes,
            Instant readinessUpdatedAt,
            boolean readinessLocked,
            String readinessLockedBy,
            Instant readinessLockedAt,
            RecordLifecycleStatus lifecycleStatus,
            Instant createdAt,
            Metadata metadata) {

        public static RoomResponse from(FacilityRoom room) {
            return new RoomResponse(room.id(), room.floorId(), room.siteCode(), room.roomCode(), room.name(),
                    room.spaceType(), room.roomType(), room.capacity(), room.areaSqm(), room.costCentre(),
                    room.bookable(), room.examinationCapable(), room.availableForBooking(),
                    room.availableForExamination(), room.readinessStatus(), room.readinessNotes(),
                    room.readinessUpdatedAt(), room.readinessLocked(), room.readinessLockedBy(),
                    room.readinessLockedAt(), room.lifecycleStatus(), room.createdAt(),
                    Metadata.from(room.metadata()));
        }
    }

    public record ZoneResponse(
            UUID id,
            String siteCode,
            String zoneCode,
            String name,
            String purpose,
            UUID parentZoneId,
            RecordLifecycleStatus lifecycleStatus,
            Instant createdAt,
            Metadata metadata) {

        public static ZoneResponse from(Zone zone) {
            return new ZoneResponse(zone.id(), zone.siteCode(), zone.zoneCode(), zone.name(), zone.purpose(),
                    zone.parentZoneId(), zone.lifecycleStatus(), zone.createdAt(),
                    Metadata.from(zone.metadata()));
        }
    }

    public record ZoneMemberResponse(
            UUID id,
            UUID zoneId,
            ZoneMemberType memberType,
            UUID memberId,
            String siteCode,
            String addedBy,
            Instant addedAt) {

        public static ZoneMemberResponse from(ZoneMembership membership) {
            return new ZoneMemberResponse(membership.id(), membership.zoneId(), membership.memberType(),
                    membership.memberId(), membership.siteCode(), membership.addedBy(), membership.addedAt());
        }
    }

    public record DeviceReferenceResponse(
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
            Instant createdAt,
            Metadata metadata) {

        public static DeviceReferenceResponse from(DeviceReference device) {
            return new DeviceReferenceResponse(device.id(), device.siteCode(), device.deviceCode(),
                    device.name(), device.type(), device.status(), device.roomId(), device.locationCode(),
                    device.vendor(), device.externalReference(), device.statusReportedAt(),
                    device.lifecycleStatus(), device.createdAt(), Metadata.from(device.metadata()));
        }
    }

    /** {@code serviceDueOn} is derived from the interval and the last service, so callers cannot skew it. */
    public record AssetResponse(
            UUID id,
            String siteCode,
            String assetCode,
            String name,
            AssetCategory category,
            AssetCriticality criticality,
            AssetOperationalStatus operationalStatus,
            UUID roomId,
            String locationCode,
            String manufacturer,
            String modelNumber,
            String serialNumber,
            LocalDate installedOn,
            LocalDate warrantyExpiresOn,
            Integer serviceIntervalDays,
            LocalDate lastServicedOn,
            LocalDate serviceDueOn,
            String custodian,
            UUID deviceReferenceId,
            UUID assetReferenceId,
            String statusNotes,
            Instant statusChangedAt,
            boolean impairsReadiness,
            RecordLifecycleStatus lifecycleStatus,
            Instant createdAt,
            Metadata metadata) {

        public static AssetResponse from(FacilityAsset asset) {
            return new AssetResponse(asset.id(), asset.siteCode(), asset.assetCode(), asset.name(),
                    asset.category(), asset.criticality(), asset.operationalStatus(), asset.roomId(),
                    asset.locationCode(), asset.manufacturer(), asset.modelNumber(), asset.serialNumber(),
                    asset.installedOn(), asset.warrantyExpiresOn(), asset.serviceIntervalDays(),
                    asset.lastServicedOn(), asset.serviceDueOn(), asset.custodian(), asset.deviceReferenceId(),
                    asset.assetReferenceId(), asset.statusNotes(), asset.statusChangedAt(),
                    asset.impairsReadiness(), asset.lifecycleStatus(), asset.createdAt(),
                    Metadata.from(asset.metadata()));
        }
    }
}
