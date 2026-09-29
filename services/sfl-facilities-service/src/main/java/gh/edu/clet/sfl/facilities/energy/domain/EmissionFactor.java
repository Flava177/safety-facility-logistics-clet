package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * One version of a carbon-equivalent factor, in kg CO2e per canonical unit - SRS-SFL-S157-03 "carbon-
 * equivalent estimates where a factor is configured".
 *
 * <p>"Where a factor is configured" is taken literally: with no factor the KPI carries no carbon figure
 * and says {@code NOT_CONFIGURED}. It never falls back to a built-in grid factor, because an institutional
 * ESG report citing a number whose source nobody chose is worse than one that is blank.
 *
 * @param sourceReference where the factor came from (a national inventory, a supplier declaration)
 */
public record EmissionFactor(
        UUID id,
        String siteCode,
        Utility utility,
        int version,
        BigDecimal kgCo2ePerUnit,
        LocalDate validFrom,
        String sourceReference,
        RecordMetadata metadata) {

    public EmissionFactor {
        if (kgCo2ePerUnit == null || kgCo2ePerUnit.signum() < 0) {
            throw new FacilitiesException.ValidationFailedException("An emission factor cannot be negative.");
        }
        if (validFrom == null) {
            throw new FacilitiesException.ValidationFailedException("An emission factor needs the date it applies from.");
        }
    }

    public static EmissionFactor version(UUID id, String siteCode, Utility utility, int version,
            BigDecimal kgCo2ePerUnit, LocalDate validFrom, String sourceReference, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new EmissionFactor(id, siteCode, utility, version, kgCo2ePerUnit, validFrom,
                sourceReference == null || sourceReference.isBlank() ? null : sourceReference.strip(),
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** The highest version in force on the period's first day. */
    public static Optional<EmissionFactor> applicable(Collection<EmissionFactor> factors, EnergyPeriod period) {
        return factors.stream().filter(factor -> !factor.validFrom().isAfter(period.start()))
                .max(Comparator.comparingInt(EmissionFactor::version));
    }
}
