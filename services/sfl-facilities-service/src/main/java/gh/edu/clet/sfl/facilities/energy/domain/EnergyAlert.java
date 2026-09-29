package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A variance alert, an anomaly flag or a missing-tariff flag - SRS-SFL-S157-02.
 *
 * <p>Three types on one record because the Facilities Director reads them in one list with the same
 * drill-down (site, utility, building, meter), and three because the SRS insists they are distinct: an
 * anomaly "can occur within budget", and a missing tariff is neither.
 *
 * @param alertKey the idempotency identity - {@code variance:MAIN:ELECTRICITY:2026-08-01} - so a sweep that
 *        runs twice, or a close followed by a sweep, raises once
 * @param observed the consumption judged
 * @param referenceValue the budget (variance) or trailing baseline (anomaly); {@code null} for a missing tariff
 */
public record EnergyAlert(
        UUID id,
        String alertKey,
        String siteCode,
        EnergyAlertType type,
        Utility utility,
        String buildingCode,
        UUID meterId,
        EnergyPeriod period,
        BigDecimal observed,
        BigDecimal referenceValue,
        BigDecimal deviationPct,
        BigDecimal thresholdPct,
        String message,
        Instant raisedAt,
        RecordMetadata metadata) {

    public enum EnergyAlertType {
        VARIANCE,
        ANOMALY,
        TARIFF_MISSING
    }
}
