package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A closed month's budget-versus-actual for one site and utility, frozen at close - SRS-SFL-S157-02.
 *
 * <p>This record is the reason "a change does not retroactively alter historical variance calculations"
 * holds. The variance is computed once, at close, and stored beside the budget and tariff <em>versions</em>
 * it used. Nothing recomputes it: a budget revised after close is a new version the closed result does
 * not reference, and a second close of the same period returns this row unchanged.
 *
 * @param variancePct {@code (actual - budget) / budget x 100}; {@code null} with no budget
 * @param tariffMissing consumption with no applicable tariff - {@code cost} is then {@code null}, never zero
 */
public record PeriodVarianceResult(
        UUID id,
        String siteCode,
        Utility utility,
        EnergyPeriod period,
        BigDecimal consumption,
        int readingCount,
        UUID budgetId,
        Integer budgetVersion,
        BigDecimal consumptionBudget,
        BigDecimal variancePct,
        BigDecimal thresholdPct,
        boolean varianceAlert,
        UUID tariffId,
        Integer tariffVersion,
        BigDecimal cost,
        BigDecimal costBudget,
        BigDecimal costVariancePct,
        String currency,
        boolean tariffMissing,
        String closedBy,
        Instant closedAt,
        RecordMetadata metadata) {
}
