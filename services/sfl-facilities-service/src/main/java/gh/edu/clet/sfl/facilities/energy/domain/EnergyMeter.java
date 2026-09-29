package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A utility meter, resolved to an S152 site and building (and optionally a room) - SRS-SFL-S157-01
 * "resolved to S152 site/building references".
 *
 * <p>The AVAMP identity is the physical device's one identity, shared with S156 (S157-04 validation).
 * Whether the meter may be registered at all given that identity is decided by the service, which can
 * ask S156; this record only enforces what is true of every meter regardless of what S156 knows.
 *
 * @param bmsDeviceId the S156 device the stream delivers this meter's readings under, held by value;
 *        present exactly when the source is {@link MeterSource#BMS_STREAM}
 * @param expectedIntervalMinutes how often a reading is expected - the denominator of S157-03's
 *        data-completeness indicator
 */
public record EnergyMeter(
        UUID id,
        String siteCode,
        String buildingCode,
        UUID roomId,
        String meterCode,
        String name,
        Utility utility,
        MeterSource source,
        String avampAssetId,
        String vendorMeterRef,
        UUID bmsDeviceId,
        int expectedIntervalMinutes,
        MeterStatus status,
        Instant retiredAt,
        String retiredReason,
        RecordMetadata metadata) {

    public EnergyMeter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(utility, "utility");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        if (expectedIntervalMinutes <= 0) {
            throw new FacilitiesException.ValidationFailedException(
                    "The expected reading interval must be a positive number of minutes.");
        }
        if (source.requiresAssetIdentity() && (avampAssetId == null || avampAssetId.isBlank())) {
            throw new FacilitiesException.ValidationFailedException(
                    "A " + source + " meter must carry its AVAMP asset identity - one identity per physical "
                            + "meter/device, referenced by both S156 and S157.");
        }
        if (source == MeterSource.AMI && (vendorMeterRef == null || vendorMeterRef.isBlank())) {
            throw new FacilitiesException.ValidationFailedException(
                    "An AMI meter needs the vendor's meter reference, which is how a gateway message names it.");
        }
        if (source == MeterSource.BMS_STREAM && bmsDeviceId == null) {
            throw new FacilitiesException.ValidationFailedException(
                    "A BMS_STREAM meter must reference the S156 device whose stream it consumes.");
        }
    }

    public static EnergyMeter register(UUID id, String siteCode, String buildingCode, UUID roomId, String meterCode,
            String name, Utility utility, MeterSource source, String avampAssetId, String vendorMeterRef,
            UUID bmsDeviceId, int expectedIntervalMinutes, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new EnergyMeter(id, siteCode, buildingCode, roomId, meterCode, name.strip(), utility, source,
                blankToNull(avampAssetId), blankToNull(vendorMeterRef), bmsDeviceId, expectedIntervalMinutes,
                MeterStatus.ACTIVE, null, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Renames, re-times, or moves the meter onto S156's stream.
     *
     * <p>The one source change allowed is onto {@link MeterSource#BMS_STREAM}: that is the repair when a
     * meter first registered as AMI is later enrolled in S156 as an IoT device (S157-04 - "consumes the
     * existing normalised stream rather than opening a second vendor connection"). Every other change of
     * source would re-interpret the meter's history - a register read differenced as if it were interval
     * consumption - and is a new meter, not an edit.
     */
    public EnergyMeter update(String name, Integer expectedIntervalMinutes, MeterSource newSource, UUID newDeviceId,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireActive();
        MeterSource source = this.source;
        UUID device = this.bmsDeviceId;
        String vendorRef = this.vendorMeterRef;
        if (newSource != null && newSource != this.source) {
            if (newSource != MeterSource.BMS_STREAM) {
                throw new FacilitiesException.InvalidStateTransitionException(
                        "A meter's source can only be moved onto BMS_STREAM (S157-04). Register a new meter "
                                + "for any other change: it would re-interpret this meter's history.");
            }
            source = MeterSource.BMS_STREAM;
            device = newDeviceId;
            vendorRef = null;
        }
        return new EnergyMeter(id, siteCode, buildingCode, roomId, meterCode,
                name == null || name.isBlank() ? this.name : name.strip(), utility, source, avampAssetId, vendorRef,
                device, expectedIntervalMinutes == null ? this.expectedIntervalMinutes : expectedIntervalMinutes,
                status, retiredAt, retiredReason, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public EnergyMeter retire(String reason, String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireActive();
        if (reason == null || reason.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("Retiring a meter requires a reason.");
        }
        return new EnergyMeter(id, siteCode, buildingCode, roomId, meterCode, name, utility, source, avampAssetId,
                vendorMeterRef, bmsDeviceId, expectedIntervalMinutes, MeterStatus.RETIRED, at, reason.strip(),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isActive() {
        return status == MeterStatus.ACTIVE;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Meter " + meterCode + " is retired and can no longer be changed.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
