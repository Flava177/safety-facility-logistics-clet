package gh.edu.clet.sfl.facilities.energy.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;

/**
 * SRS-SFL-S157-02: "A sudden consumption spike versus the trailing baseline raises a distinct anomaly
 * flag, since a spike can occur within budget."
 *
 * <p>Deliberately independent of the budget. A pump left running all weekend can double a building's
 * water use for two days and still finish the month under budget; the variance alert would never fire and
 * the only thing that catches it is comparing a day with the days before it.
 *
 * <p>The baseline is the mean of the trailing days <em>that have data</em>. A day with no rows is missing,
 * not zero - averaging it in as zero would drag the baseline down after an outage and make the first
 * normal day afterwards look like a spike.
 */
public final class AnomalyPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AnomalyPolicy() {
    }

    public record Spike(BigDecimal baseline, BigDecimal deviationPct) {
    }

    /**
     * @param baselineDays daily consumption for the trailing window, excluding the day judged
     * @return the spike, or empty when the day is within the band or there is too little history to say
     */
    public static Optional<Spike> judge(BigDecimal day, Collection<BigDecimal> baselineDays, int minimumBaselineDays,
            BigDecimal spikePct) {
        if (day == null || baselineDays.size() < Math.max(1, minimumBaselineDays)) {
            return Optional.empty();
        }
        BigDecimal baseline = baselineDays.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(baselineDays.size()), 4, RoundingMode.HALF_UP);
        if (baseline.signum() <= 0) {
            // Nothing to be a multiple of. A meter that used nothing for a fortnight and then something
            // is a commissioning question, not a spike, and a percentage of zero is undefined.
            return Optional.empty();
        }
        BigDecimal deviation = day.subtract(baseline).multiply(HUNDRED).divide(baseline, 4, RoundingMode.HALF_UP);
        return deviation.compareTo(spikePct) > 0 ? Optional.of(new Spike(baseline, deviation)) : Optional.empty();
    }
}
