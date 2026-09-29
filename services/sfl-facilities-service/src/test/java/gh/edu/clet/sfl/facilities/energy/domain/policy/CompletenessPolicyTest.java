package gh.edu.clet.sfl.facilities.energy.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.energy.domain.CompletenessFlag;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CompletenessPolicyTest {

    @Test
    void one_hundred_percent_is_complete() {
        assertThat(CompletenessPolicy.percent(100, 100)).isEqualByComparingTo("100.00");
        assertThat(CompletenessPolicy.flag(new BigDecimal("100"), BigDecimal.valueOf(80))).isEqualTo(
                CompletenessFlag.COMPLETE);
    }

    @Test
    void ninety_nine_point_nine_is_partial_not_complete() {
        assertThat(CompletenessPolicy.flag(new BigDecimal("99.99"), BigDecimal.valueOf(80)))
                .isEqualTo(CompletenessFlag.PARTIAL);
    }

    @Test
    void below_the_configured_minimum_is_low() {
        assertThat(CompletenessPolicy.flag(new BigDecimal("40"), BigDecimal.valueOf(80)))
                .isEqualTo(CompletenessFlag.LOW);
    }

    @Test
    void received_is_capped_at_expected_so_a_chatty_meter_cannot_cover_for_a_silent_one() {
        assertThat(CompletenessPolicy.received(10, 25)).isEqualTo(10);
    }

    @Test
    void zero_expected_reports_zero_percent_rather_than_dividing_by_zero() {
        assertThat(CompletenessPolicy.percent(0, 0)).isEqualByComparingTo("0.00");
    }
}
