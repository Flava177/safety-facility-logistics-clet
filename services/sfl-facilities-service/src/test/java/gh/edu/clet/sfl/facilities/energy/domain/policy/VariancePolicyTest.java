package gh.edu.clet.sfl.facilities.energy.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyBudget;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyTariff;
import gh.edu.clet.sfl.facilities.energy.domain.PeriodVarianceResult;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VariancePolicyTest {

    private static final Instant AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final EnergyPeriod AUGUST = EnergyPeriod.month(LocalDate.of(2026, 8, 1));

    @Test
    void variance_beyond_the_threshold_breaches() {
        assertThat(VariancePolicy.breaches(new BigDecimal("15"), BigDecimal.TEN)).isTrue();
        assertThat(VariancePolicy.breaches(new BigDecimal("10"), BigDecimal.TEN)).isFalse();
        assertThat(VariancePolicy.breaches(null, BigDecimal.TEN)).isFalse();
    }

    @Test
    void no_budget_means_no_variance_percentage_at_all() {
        // Zero consumption, isolating "no budget" from the separate missing-tariff rule below, which
        // triggers on any nonzero consumption with no tariff regardless of budget.
        PeriodVarianceResult result = VariancePolicy.close(UUID.randomUUID(), "MAIN", Utility.ELECTRICITY, AUGUST,
                BigDecimal.ZERO, 0, null, null, BigDecimal.TEN, "tester", AT, SourceChannel.SYSTEM, null);

        assertThat(result.variancePct()).isNull();
        assertThat(result.varianceAlert()).isFalse();
        assertThat(result.tariffMissing()).isFalse();
    }

    @Test
    void consumption_with_no_tariff_is_flagged_and_never_costed_at_zero() {
        PeriodVarianceResult result = VariancePolicy.close(UUID.randomUUID(), "MAIN", Utility.WATER, AUGUST,
                new BigDecimal("50"), 5, null, null, BigDecimal.TEN, "tester", AT, SourceChannel.SYSTEM, null);

        assertThat(result.tariffMissing()).isTrue();
        assertThat(result.cost()).isNull();
    }

    @Test
    void zero_consumption_with_no_tariff_is_not_flagged() {
        PeriodVarianceResult result = VariancePolicy.close(UUID.randomUUID(), "MAIN", Utility.WATER, AUGUST,
                BigDecimal.ZERO, 0, null, null, BigDecimal.TEN, "tester", AT, SourceChannel.SYSTEM, null);

        assertThat(result.tariffMissing()).isFalse();
    }

    @Test
    void a_tariff_prices_consumption_and_a_budget_beyond_threshold_alerts() {
        EnergyBudget budget = EnergyBudget.version(UUID.randomUUID(), "MAIN", Utility.ELECTRICITY, AUGUST, 1,
                new BigDecimal("100"), null, null, null, "tester", AT, SourceChannel.WEB, null);
        EnergyTariff tariff = EnergyTariff.version(UUID.randomUUID(), "MAIN", Utility.ELECTRICITY, 1,
                new BigDecimal("2"), "GHS", LocalDate.of(2026, 1, 1), null, null, "tester", AT, SourceChannel.WEB,
                null);

        PeriodVarianceResult result = VariancePolicy.close(UUID.randomUUID(), "MAIN", Utility.ELECTRICITY, AUGUST,
                new BigDecimal("200"), 10, budget, tariff, BigDecimal.TEN, "tester", AT, SourceChannel.SYSTEM, null);

        assertThat(result.variancePct()).isEqualByComparingTo("100");
        assertThat(result.varianceAlert()).isTrue();
        assertThat(result.cost()).isEqualByComparingTo("400.00");
        assertThat(result.budgetVersion()).isEqualTo(1);
        assertThat(result.tariffVersion()).isEqualTo(1);
    }
}
