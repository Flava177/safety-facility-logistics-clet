package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Every write command the estate accepts, in one file.
 *
 * <p>Twenty small records in twenty files would be twenty files of ceremony. They share one shape —
 * the payload, then {@code actor}, {@code channel} and {@code idempotencyKey} — and reading them
 * together is how the API surface stays legible.
 *
 * <p>{@code idempotencyPayload()} on the create commands returns the fields that make a request
 * <em>the same request</em>, deliberately excluding the actor and the correlation ID: the same site
 * submitted twice by two officers is still one site, and including who asked would make every retry
 * look like a new request.
 */
public final class FacilitiesCommands {

    private FacilitiesCommands() {
    }

    // ---- sites --------------------------------------------------------------------------------

    public record CreateSite(
            String siteCode,
            String name,
            String description,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", nullSafe(siteCode), nullSafe(name), nullSafe(description));
        }
    }

    public record UpdateSite(
            UUID siteId,
            String name,
            String description,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeSiteLifecycle(
            UUID siteId,
            RecordLifecycleStatus status,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeOperatingMode(
            UUID siteId,
            OperatingMode operatingMode,
            String reason,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- buildings, floors --------------------------------------------------------------------

    public record CreateBuilding(
            UUID siteId,
            String buildingCode,
            String name,
            String description,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", String.valueOf(siteId), nullSafe(buildingCode), nullSafe(name));
        }
    }

    public record CreateFloor(
            UUID buildingId,
            String floorCode,
            String name,
            Integer levelNumber,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", String.valueOf(buildingId), nullSafe(floorCode), nullSafe(name));
        }
    }

    // ---- spaces -------------------------------------------------------------------------------

    public record CreateRoom(
            UUID floorId,
            String roomCode,
            String name,
            SpaceType spaceType,
            Integer capacity,
            BigDecimal areaSqm,
            String costCentre,
            Boolean bookable,
            Boolean examinationCapable,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", String.valueOf(floorId), nullSafe(roomCode), nullSafe(name),
                    String.valueOf(spaceType), String.valueOf(capacity));
        }
    }

    public record UpdateRoom(
            UUID roomId,
            String name,
            SpaceType spaceType,
            Integer capacity,
            BigDecimal areaSqm,
            String costCentre,
            Boolean bookable,
            Boolean examinationCapable,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeRoomLifecycle(
            UUID roomId,
            RecordLifecycleStatus status,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    /**
     * A readiness status set by hand rather than derived from an assessment.
     *
     * <p>Kept because an officer standing in a room sometimes knows something the checklist does not.
     * It is still subject to the critical-blocker rule — {@code ReadinessPolicy.requireReadyPermitted}
     * runs before it applies — so it is an override of the *process*, never of the *invariant*.
     */
    public record UpdateRoomReadiness(
            UUID roomId,
            LocationReadinessStatus status,
            String notes,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- zones --------------------------------------------------------------------------------

    public record CreateZone(
            String siteCode,
            String zoneCode,
            String name,
            String purpose,
            UUID parentZoneId,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", nullSafe(siteCode), nullSafe(zoneCode), nullSafe(name));
        }
    }

    public record AddZoneMember(
            UUID zoneId,
            ZoneMemberType memberType,
            UUID memberId,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record RemoveZoneMember(
            UUID zoneId,
            ZoneMemberType memberType,
            UUID memberId,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- device references --------------------------------------------------------------------

    public record RegisterDeviceReference(
            String siteCode,
            String deviceCode,
            String name,
            DeviceReferenceType type,
            UUID roomId,
            String locationCode,
            String vendor,
            String externalReference,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", nullSafe(siteCode), nullSafe(deviceCode), nullSafe(name),
                    String.valueOf(type));
        }
    }

    // ---- facility assets ------------------------------------------------------------------------

    public record RegisterAsset(
            String siteCode,
            String assetCode,
            String name,
            AssetCategory category,
            AssetCriticality criticality,
            UUID roomId,
            String locationCode,
            String manufacturer,
            String modelNumber,
            String serialNumber,
            LocalDate installedOn,
            LocalDate warrantyExpiresOn,
            Integer serviceIntervalDays,
            String custodian,
            UUID deviceReferenceId,
            UUID assetReferenceId,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey) {

        public Object idempotencyPayload() {
            return String.join("|", nullSafe(siteCode), nullSafe(assetCode), nullSafe(name),
                    String.valueOf(category));
        }
    }

    public record UpdateAsset(
            UUID assetId,
            String name,
            AssetCategory category,
            AssetCriticality criticality,
            String manufacturer,
            String modelNumber,
            String serialNumber,
            LocalDate warrantyExpiresOn,
            Integer serviceIntervalDays,
            String custodian,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeAssetStatus(
            UUID assetId,
            AssetOperationalStatus operationalStatus,
            String notes,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record RelocateAsset(
            UUID assetId,
            UUID roomId,
            String locationCode,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
