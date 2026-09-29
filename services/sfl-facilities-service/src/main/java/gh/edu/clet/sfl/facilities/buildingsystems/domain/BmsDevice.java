package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A registered BMS/IoT device or gateway - SRS-SFL-S156-04.
 *
 * <h2>Identity</h2>
 *
 * <p>{@code deviceCode} is the identifier the vendor gateway reports telemetry under - a BACnet device
 * instance, an MQTT topic leaf, a simulator's {@code deviceId}. {@code avampAssetId} is the device's
 * stable AVAMP identity, and it is mandatory: "every BMS/IoT device is registered as an AVAMP asset with a
 * stable identifier". One device per AVAMP asset, for the life of the database - the same identity S157
 * checks before registering a meter, so one physical box never becomes two records (S157-04).
 *
 * <p>The device code is unique among <em>active</em> devices of a site only. When a sensor is swapped,
 * the gateway keeps reporting the same point name; the old device is retired with its history and the
 * replacement registered under the same code, so the telemetry stream carries on without the vendor
 * reconfiguring anything.
 *
 * <h2>Lifecycle data held here, not in AVAMP</h2>
 *
 * <p>Install date, firmware version, warranty expiry and calibration due date belong to the device's
 * AVAMP record in the SRS's picture. AVAMP-Lite as built in FTLMP carries none of them, so S156 holds
 * them - flagged in the S156 gap report rather than silently decided, and the first thing to move if
 * the full S168 lands.
 *
 * <h2>Location</h2>
 *
 * <p>Resolved through S152 (S156-04: "device-to-location mapping is resolved through S152"): a building
 * code always, a room when the device serves one. Held by value and re-checked against the register on
 * every reading, because a room can be archived after a device was mapped to it - which is exactly the
 * "unresolvable location" error state, and the reading is quarantined rather than filed against a room
 * that no longer exists.
 *
 * @param expectedIntervalSeconds how often this device should report. The staleness rule (S156-03) is
 *        measured in multiples of this, so a fifteen-minute meter is not called stale at minute two
 */
public record BmsDevice(
        UUID id,
        String siteCode,
        String deviceCode,
        String avampAssetId,
        String name,
        BuildingSystemType systemType,
        DeviceKind kind,
        String buildingCode,
        UUID roomId,
        String roomCode,
        int expectedIntervalSeconds,
        LocalDate installedOn,
        String firmwareVersion,
        LocalDate firmwareReviewDueOn,
        LocalDate warrantyExpiresOn,
        LocalDate calibrationDueOn,
        DeviceStatus status,
        Instant retiredAt,
        String retiredBy,
        String retirementReason,
        LocalDate calibrationRemindedFor,
        LocalDate firmwareRemindedFor,
        LocalDate warrantyRemindedFor,
        RecordMetadata metadata) {

    public BmsDevice {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        deviceCode = EstateCodes.normalize(deviceCode);
        EstateCodes.require(avampAssetId, "avampAssetId");
        avampAssetId = avampAssetId.strip();
        EstateCodes.require(name, "name");
        name = name.strip();
        Objects.requireNonNull(systemType, "systemType is required");
        Objects.requireNonNull(kind, "kind is required");
        buildingCode = EstateCodes.normalize(buildingCode);
        roomCode = roomCode == null || roomCode.isBlank() ? null : EstateCodes.normalize(roomCode);
        if (expectedIntervalSeconds <= 0) {
            throw new FacilitiesException.ValidationFailedException(
                    "expectedIntervalSeconds must be positive - a device with no expected interval can never be stale");
        }
        firmwareVersion = EstateCodes.blankToNull(firmwareVersion);
        status = status == null ? DeviceStatus.ACTIVE : status;
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static BmsDevice register(UUID id, String siteCode, String deviceCode, String avampAssetId, String name,
            BuildingSystemType systemType, DeviceKind kind, String buildingCode, UUID roomId, String roomCode,
            int expectedIntervalSeconds, LocalDate installedOn, String firmwareVersion, LocalDate firmwareReviewDueOn,
            LocalDate warrantyExpiresOn, LocalDate calibrationDueOn, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new BmsDevice(id, siteCode, deviceCode, avampAssetId, name, systemType, kind, buildingCode, roomId,
                roomCode, expectedIntervalSeconds, installedOn, firmwareVersion, firmwareReviewDueOn, warrantyExpiresOn,
                calibrationDueOn, DeviceStatus.ACTIVE, null, null, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public boolean isActive() {
        return status == DeviceStatus.ACTIVE;
    }

    /** What a technician looks for: the room if there is one, otherwise the building. */
    public String locationCode() {
        return roomCode != null ? roomCode : buildingCode;
    }

    /**
     * Changes the lifecycle facts and, optionally, the S152 location.
     *
     * <p>A moved due date clears the "already reminded" marker for that item, so moving calibration from
     * March to September earns a September reminder rather than being silenced by the March one.
     */
    public BmsDevice revise(String newName, String newBuildingCode, UUID newRoomId, String newRoomCode,
            int newExpectedIntervalSeconds, LocalDate newInstalledOn, String newFirmwareVersion,
            LocalDate newFirmwareReviewDueOn, LocalDate newWarrantyExpiresOn, LocalDate newCalibrationDueOn,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireActive("revise");
        return new BmsDevice(id, siteCode, deviceCode, avampAssetId, newName, systemType, kind, newBuildingCode,
                newRoomId, newRoomCode, newExpectedIntervalSeconds, newInstalledOn, newFirmwareVersion,
                newFirmwareReviewDueOn, newWarrantyExpiresOn, newCalibrationDueOn, status, retiredAt, retiredBy,
                retirementReason,
                Objects.equals(newCalibrationDueOn, calibrationDueOn) ? calibrationRemindedFor : null,
                Objects.equals(newFirmwareReviewDueOn, firmwareReviewDueOn) ? firmwareRemindedFor : null,
                Objects.equals(newWarrantyExpiresOn, warrantyExpiresOn) ? warrantyRemindedFor : null,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Retires the device. Terminal; the record and everything it produced stay. */
    public BmsDevice retire(String reason, String actorId, Instant at, SourceChannel channel, String correlationId) {
        if (!status.canTransitionTo(DeviceStatus.RETIRED)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Device " + deviceCode + " is already retired; a retired device's record is kept, not reopened");
        }
        EstateCodes.require(reason, "reason");
        return new BmsDevice(id, siteCode, deviceCode, avampAssetId, name, systemType, kind, buildingCode, roomId,
                roomCode, expectedIntervalSeconds, installedOn, firmwareVersion, firmwareReviewDueOn, warrantyExpiresOn,
                calibrationDueOn, DeviceStatus.RETIRED, at, actorId, reason.strip(), calibrationRemindedFor,
                firmwareRemindedFor, warrantyRemindedFor, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Records that a reminder for this item's current due date has gone out, so the sweep does not repeat it. */
    public BmsDevice withReminderRaised(LifecycleItem item, LocalDate dueOn, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new BmsDevice(id, siteCode, deviceCode, avampAssetId, name, systemType, kind, buildingCode, roomId,
                roomCode, expectedIntervalSeconds, installedOn, firmwareVersion, firmwareReviewDueOn, warrantyExpiresOn,
                calibrationDueOn, status, retiredAt, retiredBy, retirementReason,
                item == LifecycleItem.CALIBRATION ? dueOn : calibrationRemindedFor,
                item == LifecycleItem.FIRMWARE_REVIEW ? dueOn : firmwareRemindedFor,
                item == LifecycleItem.WARRANTY_EXPIRY ? dueOn : warrantyRemindedFor,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private void requireActive(String action) {
        if (!isActive()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Device " + deviceCode + " is retired and cannot " + action);
        }
    }
}
