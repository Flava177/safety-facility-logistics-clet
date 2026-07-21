package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OdometerRegressionException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An odometer reading with its provenance (SRS-SFL-S166-01 "current odometer with provenance").
 *
 * <p>Readings only ever move forward. {@link #advanceTo} rejects a regression outright; correcting a
 * genuinely wrong reading is a separate, authorised action ({@link #correctTo}) that records a reason
 * and evidence in the audit trail, which is the only route the SRS permits.
 */
public record OdometerReading(
        long value,
        DistanceUnit unit,
        OdometerSource source,
        Instant recordedAt) {

    public OdometerReading {
        Objects.requireNonNull(unit, "unit is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
        if (value < 0) {
            throw new IllegalArgumentException("odometer value cannot be negative");
        }
    }

    public static OdometerReading of(long value, OdometerSource source, Instant recordedAt) {
        return new OdometerReading(value, DistanceUnit.KILOMETRES, source, recordedAt);
    }

    /**
     * Advances to a later reading.
     *
     * @throws OdometerRegressionException when the new reading is lower than the current one
     * @throws IllegalArgumentException when the units differ, because comparing km to miles silently
     *         would produce a wrong service-due calculation
     */
    public OdometerReading advanceTo(long newValue, OdometerSource newSource, Instant readAt) {
        if (newValue < value) {
            throw OdometerRegressionException.of(value, newValue);
        }
        return new OdometerReading(newValue, unit, newSource, readAt);
    }

    /** Applies an authorised correction, which is the only way a reading may move backwards. */
    public OdometerReading correctTo(long correctedValue, Instant correctedAt) {
        return new OdometerReading(correctedValue, unit, OdometerSource.AUTHORISED_CORRECTION, correctedAt);
    }

    /** True when the reading is older than {@code threshold} at {@code now}. */
    public boolean isStaleAt(Instant now, Duration threshold) {
        return Duration.between(recordedAt, now).compareTo(threshold) > 0;
    }

    public boolean isAtOrBeyond(long target) {
        return value >= target;
    }
}
