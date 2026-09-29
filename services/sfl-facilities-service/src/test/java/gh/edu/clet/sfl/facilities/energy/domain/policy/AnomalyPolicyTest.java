package gh.edu.clet.sfl.facilities.energy.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnomalyPolicyTest {

    @Test
    void a_day_within_the_baseline_band_is_not_a_spike() {
        Optional<AnomalyPolicy.Spike> spike = AnomalyPolicy.judge(new BigDecimal("11"),
                List.of(new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10")), 2, BigDecimal.valueOf(50));

        assertThat(spike).isEmpty();
    }

    @Test
    void a_day_far_above_the_baseline_is_a_spike_naming_the_baseline_and_deviation() {
        Optional<AnomalyPolicy.Spike> spike = AnomalyPolicy.judge(new BigDecimal("100"),
                List.of(new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10")), 2, BigDecimal.valueOf(50));

        assertThat(spike).isPresent();
        assertThat(spike.get().baseline()).isEqualByComparingTo("10");
        assertThat(spike.get().deviationPct()).isEqualByComparingTo("900");
    }

    @Test
    void too_little_baseline_history_cannot_be_judged() {
        Optional<AnomalyPolicy.Spike> spike = AnomalyPolicy.judge(new BigDecimal("1000"),
                List.of(new BigDecimal("10")), 7, BigDecimal.valueOf(50));

        assertThat(spike).isEmpty();
    }

    @Test
    void a_zero_baseline_cannot_be_judged_as_a_percentage() {
        Optional<AnomalyPolicy.Spike> spike = AnomalyPolicy.judge(new BigDecimal("50"),
                List.of(BigDecimal.ZERO, BigDecimal.ZERO), 1, BigDecimal.valueOf(50));

        assertThat(spike).isEmpty();
    }
}
