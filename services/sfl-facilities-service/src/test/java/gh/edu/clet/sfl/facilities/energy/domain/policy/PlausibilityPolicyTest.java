package gh.edu.clet.sfl.facilities.energy.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlausibilityPolicyTest {

    @Test
    void a_negative_delta_is_always_implausible_regardless_of_history() {
        PlausibilityPolicy.Outcome outcome = PlausibilityPolicy.evaluate(new BigDecimal("-5"), Duration.ofDays(1),
                new BigDecimal("10"), 10, 2, BigDecimal.valueOf(50));

        assertThat(outcome.plausible()).isFalse();
        assertThat(outcome.reason()).contains("lower than the previous posted read");
    }

    @Test
    void with_too_little_history_a_reading_posts_unchecked() {
        PlausibilityPolicy.Outcome outcome = PlausibilityPolicy.evaluate(new BigDecimal("9000"), Duration.ofDays(1),
                new BigDecimal("10"), 1, 2, BigDecimal.valueOf(50));

        assertThat(outcome.evaluated()).isFalse();
        assertThat(outcome.plausible()).isTrue();
    }

    @Test
    void within_the_band_is_plausible() {
        PlausibilityPolicy.Outcome outcome = PlausibilityPolicy.evaluate(new BigDecimal("12"), Duration.ofDays(1),
                new BigDecimal("10"), 5, 2, BigDecimal.valueOf(50));

        assertThat(outcome.evaluated()).isTrue();
        assertThat(outcome.plausible()).isTrue();
    }

    @Test
    void outside_the_band_is_held_with_the_numbers_named() {
        PlausibilityPolicy.Outcome outcome = PlausibilityPolicy.evaluate(new BigDecimal("500"), Duration.ofDays(1),
                new BigDecimal("10"), 5, 2, BigDecimal.valueOf(50));

        assertThat(outcome.plausible()).isFalse();
        assertThat(outcome.reason()).contains("Implausible Reading").contains("plausibility band");
    }

    @Test
    void a_short_elapsed_time_is_floored_at_one_hour_so_it_does_not_imply_an_absurd_daily_rate() {
        BigDecimal rate = PlausibilityPolicy.dailyRate(new BigDecimal("10"), Duration.ofMinutes(1));

        // 10 over one hour, not one minute: 10 * 24 = 240/day, not 10 * 1440.
        assertThat(rate).isEqualByComparingTo("240");
    }
}
