package gh.edu.clet.sfl.facilities.energy.domain.policy;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyBudget;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyTariff;
import gh.edu.clet.sfl.facilities.energy.domain.PeriodVarianceResult;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Budget-versus-actual and cost - SRS-SFL-S157-02.
 *
 * <h2>Overspend only</h2>
 *
 * The acceptance criterion is "consumption exceeds budget by more than the configured threshold", and the
 * user story is catching overspend. An underspend beyond the threshold is reported in the result (the
 * variance is negative) but raises no alert; whether a large underspend should alert (a meter gone quiet
 * looks exactly like one) is an open question in the gap report. The completeness indicator and the
 * anomaly flag are the tools for a quiet meter.
 *
 * <h2>Missing tariff</h2>
 *
 * With consumption and no applicable tariff, cost is {@code null} and {@code tariffMissing} is set. It is
 * never computed at zero: "cannot compute cost variance; flagged rather than assumed zero-cost". With no
 * consumption at all there is nothing to cost, and no flag.
 */
public final class VariancePolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private VariancePolicy() {
    }

    public static BigDecimal variancePct(BigDecimal actual, BigDecimal budget) {
        if (budget == null || budget.signum() == 0 || actual == null) {
            return null;
        }
        return actual.subtract(budget).multiply(HUNDRED).divide(budget, 4, RoundingMode.HALF_UP);
    }

    /** {@code true} when actual exceeds budget by more than {@code thresholdPct} percent. */
    public static boolean breaches(BigDecimal variancePct, BigDecimal thresholdPct) {
        return variancePct != null && variancePct.compareTo(thresholdPct) > 0;
    }

    /** Freezes one period's variance with the budget and tariff versions it used. */
    public static PeriodVarianceResult close(UUID id, String siteCode, Utility utility, EnergyPeriod period,
            BigDecimal consumption, int readingCount, EnergyBudget budget, EnergyTariff tariff,
            BigDecimal thresholdPct, String actorId, Instant at, SourceChannel channel, String correlationId) {
        BigDecimal variance = budget == null ? null : variancePct(consumption, budget.consumptionBudget());
        boolean tariffMissing = tariff == null && consumption.signum() > 0;
        BigDecimal cost = tariff == null ? null
                : consumption.multiply(tariff.unitRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costBudget = budget == null ? null : budget.costBudget();
        boolean comparableCurrency = tariff != null && budget != null && budget.currency() != null
                && budget.currency().equals(tariff.currency());
        BigDecimal costVariance = comparableCurrency ? variancePct(cost, costBudget) : null;
        return new PeriodVarianceResult(id, siteCode, utility, period, consumption, readingCount,
                budget == null ? null : budget.id(), budget == null ? null : budget.version(),
                budget == null ? null : budget.consumptionBudget(), variance, thresholdPct,
                breaches(variance, thresholdPct) || breaches(costVariance, thresholdPct),
                tariff == null ? null : tariff.id(), tariff == null ? null : tariff.version(), cost, costBudget,
                costVariance, tariff != null ? tariff.currency() : budget == null ? null : budget.currency(),
                tariffMissing, actorId, at, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }
}
