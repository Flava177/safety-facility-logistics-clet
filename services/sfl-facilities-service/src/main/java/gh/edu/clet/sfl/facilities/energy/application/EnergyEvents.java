package gh.edu.clet.sfl.facilities.energy.application;

import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The S157 integration events and their payloads - documented in {@code docs/facilities/S157_Event_Contracts.md}.
 *
 * <p>Payloads carry references, classifications and numbers. No free text a person typed (a reading note,
 * a rejection reason) and no person's name: a consumer that needs who did it reads the audit trail, which
 * is access-controlled, rather than a broker message that is not.
 */
public final class EnergyEvents {

    public static final String METER_REGISTERED = "sfl.ifimp.energy-meter-registered.v1";
    public static final String READING_HELD = "sfl.ifimp.energy-reading-held.v1";
    public static final String READING_VERIFIED = "sfl.ifimp.energy-reading-verified.v1";
    public static final String VARIANCE_ALERT_RAISED = "sfl.ifimp.energy-variance-alert-raised.v1";
    public static final String ANOMALY_FLAGGED = "sfl.ifimp.energy-anomaly-flagged.v1";
    public static final String TARIFF_MISSING = "sfl.ifimp.energy-tariff-missing.v1";
    public static final String KPI_PUBLISHED = "sfl.ifimp.sustainability-kpi-published.v1";

    private EnergyEvents() {
    }

    public record MeterRegistered(UUID meterId, String meterCode, String siteCode, String buildingCode, UUID roomId,
            String utility, String canonicalUnit, String source, String avampAssetId, UUID bmsDeviceId) {

        static MeterRegistered of(EnergyMeter meter) {
            return new MeterRegistered(meter.id(), meter.meterCode(), meter.siteCode(), meter.buildingCode(),
                    meter.roomId(), meter.utility().name(), meter.utility().unitCode(), meter.source().name(),
                    meter.avampAssetId(), meter.bmsDeviceId());
        }
    }

    /** @param holdCode always {@code ENERGY_READING_IMPLAUSIBLE}; the numbers say why */
    public record ReadingHeld(UUID readingId, UUID meterId, String siteCode, String utility, Instant observedAt,
            BigDecimal registerValue, BigDecimal consumption, BigDecimal trailingDailyAverage, BigDecimal bandLow,
            BigDecimal bandHigh, String holdCode) {

        static ReadingHeld of(ConsumptionReading reading) {
            return new ReadingHeld(reading.id(), reading.meterId(), reading.siteCode(), reading.utility().name(),
                    reading.observedAt(), reading.registerValue(), reading.consumption(),
                    reading.trailingDailyAverage(), reading.bandLow(), reading.bandHigh(), "ENERGY_READING_IMPLAUSIBLE");
        }
    }

    /** Published only when a held reading is accepted into the consumption record. */
    public record ReadingVerified(UUID readingId, UUID meterId, String siteCode, String utility, Instant observedAt,
            BigDecimal consumption, Instant verifiedAt) {

        static ReadingVerified of(ConsumptionReading reading) {
            return new ReadingVerified(reading.id(), reading.meterId(), reading.siteCode(), reading.utility().name(),
                    reading.observedAt(), reading.consumption(), reading.verifiedAt());
        }
    }

    /** One payload shape for the three alert events; {@code alertType} says which. */
    public record AlertRaised(UUID alertId, String alertKey, String alertType, String siteCode, String utility,
            String buildingCode, UUID meterId, LocalDate periodStart, LocalDate periodEnd, BigDecimal observed,
            BigDecimal referenceValue, BigDecimal deviationPct, BigDecimal thresholdPct, Instant raisedAt) {

        static AlertRaised of(EnergyAlert alert) {
            return new AlertRaised(alert.id(), alert.alertKey(), alert.type().name(), alert.siteCode(),
                    alert.utility().name(), alert.buildingCode(), alert.meterId(), alert.period().start(),
                    alert.period().end(), alert.observed(), alert.referenceValue(), alert.deviationPct(),
                    alert.thresholdPct(), alert.raisedAt());
        }
    }

    /**
     * The S225 Analytics contract (S157-03). Completeness travels beside the value in every message, so a
     * consumer cannot receive one without the other.
     */
    public record KpiPublished(UUID kpiId, String kpiKey, String scope, String siteCode, String buildingCode,
            String utility, String unit, String periodType, LocalDate periodStart, LocalDate periodEnd,
            boolean periodClosed, BigDecimal consumption, BigDecimal previousConsumption, BigDecimal trendPct,
            BigDecimal carbonKgCo2e, String emissionFactorStatus, int expectedReadings, int receivedReadings,
            BigDecimal completenessPct, String completenessFlag, BigDecimal minimumCompletenessPct, int revision,
            Instant computedAt) {

        static KpiPublished of(SustainabilityKpi kpi) {
            return new KpiPublished(kpi.id(), kpi.kpiKey(), kpi.scope().name(), kpi.siteCode(), kpi.buildingCode(),
                    kpi.utility().name(), kpi.utility().unitCode(), kpi.period().type().name(), kpi.period().start(),
                    kpi.period().end(), kpi.periodClosed(), kpi.consumption(), kpi.previousConsumption(),
                    kpi.trendPct(), kpi.carbonKgCo2e(), kpi.emissionFactorStatus().name(), kpi.expectedReadings(),
                    kpi.receivedReadings(), kpi.completenessPct(), kpi.completenessFlag().name(),
                    kpi.minimumCompletenessPct(), kpi.revision(), kpi.computedAt());
        }
    }
}
