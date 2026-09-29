package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * One version of a site's budget for one utility in one month - SRS-SFL-S157-02 "Budgets and tariffs are
 * configurable per site/utility/period" and "versioned; a change does not retroactively alter historical
 * variance calculations".
 *
 * <p>Never edited. A revised budget is version + 1, and the closed period's result keeps the id and
 * version of the one it was measured against (see {@link PeriodVarianceResult}).
 *
 * @param costBudget optional; a consumption budget alone is a complete budget
 */
public record EnergyBudget(
        UUID id,
        String siteCode,
        Utility utility,
        EnergyPeriod period,
        int version,
        BigDecimal consumptionBudget,
        BigDecimal costBudget,
        String currency,
        String reason,
        RecordMetadata metadata) {

    public EnergyBudget {
        if (period.type() != EnergyPeriod.PeriodType.MONTH) {
            throw new FacilitiesException.ValidationFailedException("Budgets are set per calendar month.");
        }
        if (consumptionBudget == null || consumptionBudget.signum() <= 0) {
            throw new FacilitiesException.ValidationFailedException("A consumption budget must be more than zero.");
        }
        if (costBudget != null && costBudget.signum() <= 0) {
            throw new FacilitiesException.ValidationFailedException("A cost budget, when given, must be more than zero.");
        }
        if ((costBudget == null) != (currency == null || currency.isBlank())) {
            throw new FacilitiesException.ValidationFailedException("A cost budget and its currency go together.");
        }
        currency = currency == null || currency.isBlank() ? null : currency.strip().toUpperCase(Locale.ROOT);
    }

    public static EnergyBudget version(UUID id, String siteCode, Utility utility, EnergyPeriod period, int version,
            BigDecimal consumptionBudget, BigDecimal costBudget, String currency, String reason, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new EnergyBudget(id, siteCode, utility, period, version, consumptionBudget, costBudget, currency,
                reason == null || reason.isBlank() ? null : reason.strip(),
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }
}
