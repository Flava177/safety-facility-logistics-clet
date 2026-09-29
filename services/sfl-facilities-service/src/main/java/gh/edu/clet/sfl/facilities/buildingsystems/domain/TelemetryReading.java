package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One reading S156 holds as fact - authenticated, schema-valid, from a registered device, resolved to an
 * S152 location, plausible and in order (SRS-SFL-S156-01). Anything that failed one of those is a
 * {@link QuarantinedReading} instead, never a row here.
 *
 * <p>Immutable once written, except for {@code evidenceHold}: a reading an alert cites as evidence is
 * exempt from the retention purge, because "auto-generated work orders must carry a back-reference to
 * the triggering telemetry evidence" (S156-02) is worthless if the purge deletes what it points at.
 *
 * @param sourceId the authenticated vendor source it arrived from
 * @param format the adapter shape it was translated from - which vendor, for an investigator
 * @param idempotencyKey the vendor message it arrived in; with {@code itemIndex}, what makes a replay
 *        answer as before rather than store twice
 * @param releasedFromQuarantineId set when a reviewer released it from quarantine. Such a reading is a
 *        historical fact but was never evaluated against rules - see {@code TelemetryIngestionService}
 */
public record TelemetryReading(
        UUID id,
        String siteCode,
        UUID deviceId,
        String avampAssetId,
        String deviceCode,
        String buildingCode,
        UUID roomId,
        String locationCode,
        String channel,
        MeasuredQuantity quantity,
        BigDecimal value,
        Instant observedAt,
        Instant receivedAt,
        String sourceId,
        String format,
        String idempotencyKey,
        int itemIndex,
        UUID inboxId,
        UUID releasedFromQuarantineId,
        boolean evidenceHold,
        RecordMetadata metadata) {

    public TelemetryReading {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(channel, "channel is required");
        Objects.requireNonNull(quantity, "quantity is required");
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(observedAt, "observedAt is required");
        Objects.requireNonNull(receivedAt, "receivedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }
}
