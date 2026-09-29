package gh.edu.clet.sfl.facilities.energy.api;

import gh.edu.clet.sfl.facilities.energy.application.EnergyReadingService;
import gh.edu.clet.sfl.facilities.energy.application.EnergyVarianceService;
import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.EmissionFactor;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyBudget;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyTariff;
import gh.edu.clet.sfl.facilities.energy.domain.PeriodVarianceResult;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * S157 response bodies. Every figure carries its unit, and every KPI its completeness, so a screen cannot
 * show one without the other.
 */
public final class EnergyResponses {

    private EnergyResponses() {
    }

    public record MeterResponse(UUID id, String siteCode, String buildingCode, UUID roomId, String meterCode,
            String name, String utility, String unit, String unitSymbol, String source, String avampAssetId,
            String vendorMeterRef, UUID bmsDeviceId, int expectedIntervalMinutes, String status, Instant retiredAt,
            String retiredReason, long version) {

        static MeterResponse from(EnergyMeter meter) {
            return new MeterResponse(meter.id(), meter.siteCode(), meter.buildingCode(), meter.roomId(),
                    meter.meterCode(), meter.name(), meter.utility().name(), meter.utility().unitCode(),
                    meter.utility().unitSymbol(), meter.source().name(), meter.avampAssetId(), meter.vendorMeterRef(),
                    meter.bmsDeviceId(), meter.expectedIntervalMinutes(), meter.status().name(), meter.retiredAt(),
                    meter.retiredReason(), meter.metadata().version());
        }
    }

    /** Entered-by and verified-by are the S157-01 audit trail, shown on the reading itself. */
    public record ReadingResponse(UUID id, UUID meterId, String siteCode, String buildingCode, String utility,
            String unit, String source, String status, Instant observedAt, Instant intervalStart,
            BigDecimal registerValue, BigDecimal consumption, BigDecimal vendorValue, String vendorUnit,
            boolean plausibilityChecked, BigDecimal trailingDailyAverage, BigDecimal bandLow, BigDecimal bandHigh,
            String holdReason, String enteredBy, Instant enteredAt, String verifiedBy, Instant verifiedAt,
            String verificationNote, String note) {

        static ReadingResponse from(ConsumptionReading reading) {
            return new ReadingResponse(reading.id(), reading.meterId(), reading.siteCode(), reading.buildingCode(),
                    reading.utility().name(), reading.utility().unitCode(), reading.source().name(),
                    reading.status().name(), reading.observedAt(), reading.intervalStart(), reading.registerValue(),
                    reading.consumption(), reading.vendorValue(), reading.vendorUnit(), reading.plausibilityChecked(),
                    reading.trailingDailyAverage(), reading.bandLow(), reading.bandHigh(), reading.holdReason(),
                    reading.enteredBy(), reading.enteredAt(), reading.verifiedBy(), reading.verifiedAt(),
                    reading.verificationNote(), reading.note());
        }
    }

    public record IngestResponse(ReadingResponse reading, boolean duplicate) {
    }

    public record ConsumptionResponse(String siteCode, String buildingCode, String utility, String unit,
            String periodType, LocalDate periodStart, LocalDate periodEnd, BigDecimal consumption, int readingCount) {

        static ConsumptionResponse from(EnergyReadingService.ConsumptionPoint point) {
            return new ConsumptionResponse(point.siteCode(), point.buildingCode(), point.utility().name(),
                    point.utility().unitCode(), point.period().type().name(), point.period().start(),
                    point.period().end(), point.consumption(), point.readingCount());
        }
    }

    public record BudgetResponse(UUID id, String siteCode, String utility, String unit, LocalDate periodStart,
            int version, BigDecimal consumptionBudget, BigDecimal costBudget, String currency, String reason,
            String createdBy, Instant createdAt) {

        static BudgetResponse from(EnergyBudget budget) {
            return new BudgetResponse(budget.id(), budget.siteCode(), budget.utility().name(),
                    budget.utility().unitCode(), budget.period().start(), budget.version(), budget.consumptionBudget(),
                    budget.costBudget(), budget.currency(), budget.reason(), budget.metadata().createdBy(),
                    budget.metadata().createdAt());
        }
    }

    public record TariffResponse(UUID id, String siteCode, String utility, String unit, int version,
            BigDecimal unitRate, String currency, LocalDate validFrom, LocalDate validTo, String reason) {

        static TariffResponse from(EnergyTariff tariff) {
            return new TariffResponse(tariff.id(), tariff.siteCode(), tariff.utility().name(),
                    tariff.utility().unitCode(), tariff.version(), tariff.unitRate(), tariff.currency(),
                    tariff.validFrom(), tariff.validTo(), tariff.reason());
        }
    }

    public record EmissionFactorResponse(UUID id, String siteCode, String utility, String unit, int version,
            BigDecimal kgCo2ePerUnit, LocalDate validFrom, String sourceReference) {

        static EmissionFactorResponse from(EmissionFactor factor) {
            return new EmissionFactorResponse(factor.id(), factor.siteCode(), factor.utility().name(),
                    factor.utility().unitCode(), factor.version(), factor.kgCo2ePerUnit(), factor.validFrom(),
                    factor.sourceReference());
        }
    }

    /** {@code closed=false} is a provisional figure for an open month, computed on read and never stored. */
    public record VarianceResponse(boolean closed, String siteCode, String utility, String unit, LocalDate periodStart,
            LocalDate periodEnd, BigDecimal consumption, int readingCount, Integer budgetVersion,
            BigDecimal consumptionBudget, BigDecimal variancePct, BigDecimal thresholdPct, boolean varianceAlert,
            Integer tariffVersion, BigDecimal cost, BigDecimal costBudget, BigDecimal costVariancePct, String currency,
            boolean tariffMissing, String closedBy, Instant closedAt) {

        static VarianceResponse from(PeriodVarianceResult result, boolean closed) {
            return new VarianceResponse(closed, result.siteCode(), result.utility().name(),
                    result.utility().unitCode(), result.period().start(), result.period().end(), result.consumption(),
                    result.readingCount(), result.budgetVersion(), result.consumptionBudget(), result.variancePct(),
                    result.thresholdPct(), result.varianceAlert(), result.tariffVersion(), result.cost(),
                    result.costBudget(), result.costVariancePct(), result.currency(), result.tariffMissing(),
                    closed ? result.closedBy() : null, closed ? result.closedAt() : null);
        }

        static VarianceResponse from(EnergyVarianceService.VarianceView view) {
            return from(view.result(), view.closed());
        }
    }

    public record CostResponse(String siteCode, String utility, String unit, LocalDate periodStart,
            BigDecimal consumption, BigDecimal unitRate, String currency, Integer tariffVersion, BigDecimal cost) {

        static CostResponse from(EnergyVarianceService.CostEstimate estimate) {
            return new CostResponse(estimate.siteCode(), estimate.utility().name(), estimate.utility().unitCode(),
                    estimate.period().start(), estimate.consumption(), estimate.unitRate(), estimate.currency(),
                    estimate.tariffVersion(), estimate.cost());
        }
    }

    public record AlertResponse(UUID id, String alertType, String siteCode, String utility, String unit,
            String buildingCode, UUID meterId, String periodType, LocalDate periodStart, LocalDate periodEnd,
            BigDecimal observed, BigDecimal referenceValue, BigDecimal deviationPct, BigDecimal thresholdPct,
            String message, Instant raisedAt) {

        static AlertResponse from(EnergyAlert alert) {
            return new AlertResponse(alert.id(), alert.type().name(), alert.siteCode(), alert.utility().name(),
                    alert.utility().unitCode(), alert.buildingCode(), alert.meterId(), alert.period().type().name(),
                    alert.period().start(), alert.period().end(), alert.observed(), alert.referenceValue(),
                    alert.deviationPct(), alert.thresholdPct(), alert.message(), alert.raisedAt());
        }
    }

    public record KpiResponse(UUID id, String kpiKey, String scope, String siteCode, String buildingCode,
            String utility, String unit, String periodType, LocalDate periodStart, LocalDate periodEnd,
            boolean periodClosed, BigDecimal consumption, BigDecimal previousConsumption, BigDecimal trendPct,
            BigDecimal carbonKgCo2e, String emissionFactorStatus, int expectedReadings, int receivedReadings,
            BigDecimal completenessPct, String completenessFlag, BigDecimal minimumCompletenessPct, int revision,
            Instant computedAt) {

        static KpiResponse from(SustainabilityKpi kpi) {
            return new KpiResponse(kpi.id(), kpi.kpiKey(), kpi.scope().name(), kpi.siteCode(), kpi.buildingCode(),
                    kpi.utility().name(), kpi.utility().unitCode(), kpi.period().type().name(), kpi.period().start(),
                    kpi.period().end(), kpi.periodClosed(), kpi.consumption(), kpi.previousConsumption(),
                    kpi.trendPct(), kpi.carbonKgCo2e(), kpi.emissionFactorStatus().name(), kpi.expectedReadings(),
                    kpi.receivedReadings(), kpi.completenessPct(), kpi.completenessFlag().name(),
                    kpi.minimumCompletenessPct(), kpi.revision(), kpi.computedAt());
        }
    }
}
