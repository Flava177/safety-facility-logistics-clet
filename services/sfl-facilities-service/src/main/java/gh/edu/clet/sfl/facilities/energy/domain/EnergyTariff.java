package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * One version of a site's unit rate for a utility, valid over a date range - SRS-SFL-S157-02.
 *
 * <p>A zero rate is refused. S157-02's Missing Tariff state says a site with consumption and no tariff is
 * "flagged rather than assumed zero-cost"; allowing zero would give somebody a way to silence that flag
 * that looks like configuration.
 *
 * @param validTo exclusive; {@code null} for open-ended
 */
public record EnergyTariff(
        UUID id,
        String siteCode,
        Utility utility,
        int version,
        BigDecimal unitRate,
        String currency,
        LocalDate validFrom,
        LocalDate validTo,
        String reason,
        RecordMetadata metadata) {

    public EnergyTariff {
        if (unitRate == null || unitRate.signum() <= 0) {
            throw new FacilitiesException.ValidationFailedException(
                    "A tariff's unit rate must be more than zero. A site with no tariff is flagged, never zero-cost.");
        }
        if (currency == null || currency.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A tariff needs a currency.");
        }
        currency = currency.strip().toUpperCase(Locale.ROOT);
        if (validFrom == null) {
            throw new FacilitiesException.ValidationFailedException("A tariff needs the date it takes effect.");
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new FacilitiesException.ValidationFailedException("A tariff must end after it starts.");
        }
    }

    public static EnergyTariff version(UUID id, String siteCode, Utility utility, int version, BigDecimal unitRate,
            String currency, LocalDate validFrom, LocalDate validTo, String reason, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new EnergyTariff(id, siteCode, utility, version, unitRate, currency, validFrom, validTo,
                reason == null || reason.isBlank() ? null : reason.strip(),
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(validFrom) && (validTo == null || day.isBefore(validTo));
    }

    /**
     * The tariff a period is costed at: the highest version whose validity covers the period's first day.
     * One rate per period, deliberately - a mid-month tariff change is costed from the following month, and
     * the gap report says so, because splitting a month needs daily costing nobody has asked for.
     */
    public static Optional<EnergyTariff> applicable(Collection<EnergyTariff> tariffs, EnergyPeriod period) {
        return tariffs.stream().filter(tariff -> tariff.covers(period.start()))
                .max(Comparator.comparingInt(EnergyTariff::version));
    }
}
