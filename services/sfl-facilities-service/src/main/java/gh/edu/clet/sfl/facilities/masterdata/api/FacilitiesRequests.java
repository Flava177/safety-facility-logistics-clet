package gh.edu.clet.sfl.facilities.masterdata.api;

import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request bodies for the estate endpoints.
 *
 * <p>Bean Validation covers what a request can be checked for without touching the database — required
 * fields, lengths, ranges. Everything that needs the estate's current state (does this parent exist, is
 * this code taken, is the caller's version current) is a domain rule and lives behind the application
 * boundary, so it cannot be bypassed by a caller that skips this layer.
 *
 * <p>Sizes match the column widths declared in V2 and V6. Where they disagree the database wins and a
 * request that slipped through would fail at the constraint, so they are kept identical deliberately.
 */
public final class FacilitiesRequests {

    private FacilitiesRequests() {
    }

    public record CreateSite(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description) {
    }

    public record UpdateSite(
            @Size(max = 160) String name,
            @Size(max = 1000) String description,
            Long expectedVersion) {
    }

    public record ChangeLifecycle(
            @NotNull RecordLifecycleStatus status,
            Long expectedVersion) {
    }

    public record ChangeOperatingMode(
            @NotNull OperatingMode operatingMode,
            @Size(max = 500) String reason) {
    }

    public record CreateBuilding(
            @NotNull UUID siteId,
            @NotBlank @Size(max = 60) String buildingCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description) {
    }

    public record CreateFloor(
            @NotNull UUID buildingId,
            @NotBlank @Size(max = 60) String floorCode,
            @NotBlank @Size(max = 160) String name,
            Integer levelNumber) {
    }

    public record CreateRoom(
            @NotNull UUID floorId,
            @NotBlank @Size(max = 80) String roomCode,
            @NotBlank @Size(max = 160) String name,
            SpaceType spaceType,
            @Min(0) Integer capacity,
            @PositiveOrZero BigDecimal areaSqm,
            @Size(max = 60) String costCentre,
            Boolean bookable,
            Boolean examinationCapable) {
    }

    public record UpdateRoom(
            @Size(max = 160) String name,
            SpaceType spaceType,
            @Min(0) Integer capacity,
            @PositiveOrZero BigDecimal areaSqm,
            @Size(max = 60) String costCentre,
            Boolean bookable,
            Boolean examinationCapable,
            Long expectedVersion) {
    }

    public record UpdateRoomReadiness(
            @NotNull LocationReadinessStatus status,
            @Size(max = 1000) String notes) {
    }

    public record CreateZone(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 60) String zoneCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 300) String purpose,
            UUID parentZoneId) {
    }

    public record AddZoneMember(
            @NotNull ZoneMemberType memberType,
            @NotNull UUID memberId) {
    }

    public record RegisterDeviceReference(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String deviceCode,
            @NotBlank @Size(max = 160) String name,
            @NotNull DeviceReferenceType type,
            UUID roomId,
            @Size(max = 120) String locationCode,
            @Size(max = 160) String vendor,
            @Size(max = 160) String externalReference) {
    }

    public record RegisterAsset(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String assetCode,
            @NotBlank @Size(max = 200) String name,
            @NotNull AssetCategory category,
            AssetCriticality criticality,
            UUID roomId,
            @Size(max = 120) String locationCode,
            @Size(max = 160) String manufacturer,
            @Size(max = 120) String modelNumber,
            @Size(max = 120) String serialNumber,
            LocalDate installedOn,
            LocalDate warrantyExpiresOn,
            @Min(1) Integer serviceIntervalDays,
            @Size(max = 160) String custodian,
            UUID deviceReferenceId,
            UUID assetReferenceId) {
    }

    public record UpdateAsset(
            @Size(max = 200) String name,
            AssetCategory category,
            AssetCriticality criticality,
            @Size(max = 160) String manufacturer,
            @Size(max = 120) String modelNumber,
            @Size(max = 120) String serialNumber,
            LocalDate warrantyExpiresOn,
            @Min(1) Integer serviceIntervalDays,
            @Size(max = 160) String custodian,
            Long expectedVersion) {
    }

    public record ChangeAssetStatus(
            @NotNull AssetOperationalStatus operationalStatus,
            @Size(max = 1000) String notes,
            Long expectedVersion) {
    }

    public record RelocateAsset(
            UUID roomId,
            @Size(max = 120) String locationCode,
            Long expectedVersion) {
    }
}
