package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineReason;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The SRS-SFL-S156-01 validation rule, as a pure function: "reading timestamps must be monotonic per
 * device-channel within an acceptable clock-skew tolerance; out-of-range, physically impossible values
 * are flagged, not stored as fact".
 *
 * <p>Runs after authentication and schema validation (which the verifier and adapter own) and after the
 * device and location are resolved (which need the registers). What is left is whether the value can be
 * true and whether it arrived in order - the two checks that need nothing but the reading and the
 * channel's watermark.
 *
 * <h2>The order of the checks</h2>
 *
 * <p>Plausibility first. A value of 900 °C is untrustworthy whatever its timestamp, and naming the more
 * fundamental problem sends the reviewer to the right vendor ticket. Then the future (a gateway whose
 * clock is ahead), then the past (a reading older than one already accepted).
 *
 * <h2>Why a tolerance at all</h2>
 *
 * <p>Gateways batch and retry. Two readings from the same channel can arrive swapped by a few seconds
 * without either being wrong, and quarantining the second would bury the review queue in noise. Within
 * the tolerance a slightly older reading is accepted as fact but does not move the watermark back (see
 * {@code ChannelState.observe}); beyond it, it is quarantined. A zero tolerance is allowed and means
 * strictly non-decreasing.
 */
public final class TelemetryValidationPolicy {

    private TelemetryValidationPolicy() {
    }

    /** Inclusive physical limits for one quantity - configurable per site, seeded by V16. */
    public record PlausibilityBand(BigDecimal min, BigDecimal max) {

        public PlausibilityBand {
            Objects.requireNonNull(min, "min is required");
            Objects.requireNonNull(max, "max is required");
            if (min.compareTo(max) > 0) {
                throw new IllegalArgumentException("plausibility min must not exceed max");
            }
        }

        public boolean contains(BigDecimal value) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
        }
    }

    public record Finding(QuarantineReason reason, String detail) {
    }

    /**
     * @param lastObservedAt the channel's watermark, or {@code null} for its first reading
     * @return empty when the reading may be stored as fact, otherwise why it must be quarantined
     */
    public static Optional<Finding> check(BigDecimal value, PlausibilityBand band, Instant observedAt,
            Instant receivedAt, Instant lastObservedAt, Duration skewTolerance) {
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(observedAt, "observedAt is required");
        Duration tolerance = skewTolerance == null || skewTolerance.isNegative() ? Duration.ZERO : skewTolerance;
        if (band != null && !band.contains(value)) {
            return Optional.of(new Finding(QuarantineReason.IMPLAUSIBLE_VALUE,
                    "Value " + value.toPlainString() + " is outside the physically plausible band ["
                            + band.min().toPlainString() + ", " + band.max().toPlainString() + "]"));
        }
        if (receivedAt != null && observedAt.isAfter(receivedAt.plus(tolerance))) {
            return Optional.of(new Finding(QuarantineReason.CLOCK_SKEW,
                    "Observed at " + observedAt + ", later than received at " + receivedAt
                            + " by more than the " + tolerance + " clock-skew tolerance"));
        }
        if (lastObservedAt != null && observedAt.isBefore(lastObservedAt.minus(tolerance))) {
            return Optional.of(new Finding(QuarantineReason.OUT_OF_ORDER,
                    "Observed at " + observedAt + ", before this channel's last accepted reading at "
                            + lastObservedAt + " by more than the " + tolerance + " clock-skew tolerance"));
        }
        return Optional.empty();
    }
}
