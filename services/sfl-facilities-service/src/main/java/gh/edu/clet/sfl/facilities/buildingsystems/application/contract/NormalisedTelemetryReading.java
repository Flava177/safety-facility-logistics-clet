package gh.edu.clet.sfl.facilities.buildingsystems.application.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One accepted, normalised, location-resolved S156 reading - the stream S157 consumes (SRS-SFL-S157-04).
 *
 * <p>Only readings that passed authentication, schema validation, device registration and location
 * resolution are ever published in this shape. A quarantined or rejected reading never reaches a
 * consumer, which is what lets S157 treat this stream as fact.
 *
 * @param avampAssetId the AVAMP identity of the physical device - one per device, shared by S156 and
 *        S157 (S157-04 validation)
 * @param roomId the S152 space, or {@code null} for a reading resolved to a building or plant location
 */
public record NormalisedTelemetryReading(
        UUID readingId,
        UUID deviceId,
        String avampAssetId,
        String siteCode,
        String buildingCode,
        UUID roomId,
        String locationCode,
        String channel,
        MeasurementKind kind,
        BigDecimal value,
        Instant observedAt,
        Instant receivedAt) {
}
