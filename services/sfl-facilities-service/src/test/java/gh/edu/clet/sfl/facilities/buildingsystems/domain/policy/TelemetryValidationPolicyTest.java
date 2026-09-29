package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineReason;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S156-01 validation: plausibility and monotonic ordering within a clock-skew tolerance. */
class TelemetryValidationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private static final TelemetryValidationPolicy.PlausibilityBand TEMPERATURE =
            new TelemetryValidationPolicy.PlausibilityBand(new BigDecimal("-50"), new BigDecimal("150"));

    @Test
    void a_plausible_in_order_reading_passes() {
        Optional<TelemetryValidationPolicy.Finding> finding = TelemetryValidationPolicy.check(
                new BigDecimal("21.5"), TEMPERATURE, NOW, NOW, NOW.minusSeconds(60), Duration.ofMinutes(2));

        assertThat(finding).isEmpty();
    }

    @Test
    void a_physically_impossible_value_is_flagged_not_stored_as_fact() {
        Optional<TelemetryValidationPolicy.Finding> finding = TelemetryValidationPolicy.check(
                new BigDecimal("900"), TEMPERATURE, NOW, NOW, null, Duration.ofMinutes(2));

        assertThat(finding).isPresent();
        assertThat(finding.get().reason()).isEqualTo(QuarantineReason.IMPLAUSIBLE_VALUE);
    }

    @Test
    void a_reading_within_the_skew_tolerance_of_the_watermark_is_accepted() {
        Instant watermark = NOW;
        Instant slightlyEarlier = NOW.minusSeconds(90);

        Optional<TelemetryValidationPolicy.Finding> finding = TelemetryValidationPolicy.check(new BigDecimal("21"),
                TEMPERATURE, slightlyEarlier, NOW, watermark, Duration.ofMinutes(2));

        assertThat(finding).isEmpty();
    }

    @Test
    void a_reading_before_the_watermark_beyond_tolerance_is_out_of_order() {
        Instant watermark = NOW;
        Instant wayEarlier = NOW.minus(Duration.ofHours(1));

        Optional<TelemetryValidationPolicy.Finding> finding = TelemetryValidationPolicy.check(new BigDecimal("21"),
                TEMPERATURE, wayEarlier, NOW, watermark, Duration.ofMinutes(2));

        assertThat(finding).isPresent();
        assertThat(finding.get().reason()).isEqualTo(QuarantineReason.OUT_OF_ORDER);
    }

    @Test
    void a_reading_dated_far_in_the_future_of_receipt_is_clock_skew() {
        Optional<TelemetryValidationPolicy.Finding> finding = TelemetryValidationPolicy.check(new BigDecimal("21"),
                TEMPERATURE, NOW.plus(Duration.ofHours(2)), NOW, null, Duration.ofMinutes(2));

        assertThat(finding).isPresent();
        assertThat(finding.get().reason()).isEqualTo(QuarantineReason.CLOCK_SKEW);
    }

    @Test
    void first_reading_on_a_channel_has_no_watermark_to_violate() {
        Optional<TelemetryValidationPolicy.Finding> finding = TelemetryValidationPolicy.check(new BigDecimal("21"),
                TEMPERATURE, NOW, NOW, null, Duration.ofMinutes(2));

        assertThat(finding).isEmpty();
    }
}
