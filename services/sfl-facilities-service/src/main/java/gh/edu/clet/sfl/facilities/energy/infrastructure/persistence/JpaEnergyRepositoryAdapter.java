package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption;
import gh.edu.clet.sfl.facilities.energy.domain.EmissionFactor;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyBudget;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyTariff;
import gh.edu.clet.sfl.facilities.energy.domain.MeterStatus;
import gh.edu.clet.sfl.facilities.energy.domain.PeriodVarianceResult;
import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * The one adapter behind {@link EnergyRepository}.
 *
 * <p>Updates reuse the managed row and check it is not stale, exactly as {@code VersionedRecord} describes,
 * so the real {@code @Version} guard issues the {@code UPDATE}. Insert-only records (budgets, tariffs,
 * factors, closed results, alerts) are only ever written once.
 *
 * <p>{@link #saveMeter} flushes and translates {@code ux_energy_meters_avamp} into
 * {@code ENERGY_DEVICE_DOUBLE_REGISTERED}, so the registration that loses a race is told the same thing as
 * one that simply came second (S157-04).
 */
@Repository
public class JpaEnergyRepositoryAdapter implements EnergyRepository {

    private static final String AVAMP_CONSTRAINT = "ux_energy_meters_avamp";
    /** Stand-ins for open temporal bounds: pgjdbc cannot type a null Instant (see JpaBookingRepositoryAdapter). */
    private static final Instant NO_LOWER = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant NO_UPPER = Instant.parse("9999-01-01T00:00:00Z");

    private final EnergyMeterJpaRepository meters;
    private final EnergyReadingJpaRepository readings;
    private final EnergyDailyConsumptionJpaRepository daily;
    private final EnergyBudgetJpaRepository budgets;
    private final EnergyTariffJpaRepository tariffs;
    private final EnergyEmissionFactorJpaRepository factors;
    private final EnergyPeriodResultJpaRepository results;
    private final EnergyAlertJpaRepository alerts;
    private final SustainabilityKpiJpaRepository kpis;

    JpaEnergyRepositoryAdapter(EnergyMeterJpaRepository meters, EnergyReadingJpaRepository readings,
            EnergyDailyConsumptionJpaRepository daily, EnergyBudgetJpaRepository budgets,
            EnergyTariffJpaRepository tariffs, EnergyEmissionFactorJpaRepository factors,
            EnergyPeriodResultJpaRepository results, EnergyAlertJpaRepository alerts,
            SustainabilityKpiJpaRepository kpis) {
        this.meters = meters;
        this.readings = readings;
        this.daily = daily;
        this.budgets = budgets;
        this.tariffs = tariffs;
        this.factors = factors;
        this.results = results;
        this.alerts = alerts;
        this.kpis = kpis;
    }

    // ---- meters ---------------------------------------------------------------------------------

    @Override
    public EnergyMeter saveMeter(EnergyMeter meter) {
        Optional<EnergyMeterRecord> existing = meters.findById(meter.id());
        existing.ifPresent(record -> record.requireNotStale(meter.metadata().version()));
        EnergyMeterRecord record = existing.orElseGet(EnergyMeterRecord::empty);
        record.apply(meter);
        try {
            return meters.saveAndFlush(record).toDomain();
        } catch (DataIntegrityViolationException violation) {
            if (mentions(violation, AVAMP_CONSTRAINT)) {
                throw new FacilitiesException(FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED,
                        FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED.defaultMessage() + " AVAMP asset "
                                + meter.avampAssetId() + " was registered by another request at the same time.");
            }
            throw violation;
        }
    }

    @Override
    public Optional<EnergyMeter> findMeter(UUID id) {
        return meters.findById(id).map(EnergyMeterRecord::toDomain);
    }

    @Override
    public Optional<EnergyMeter> findMeterByCode(String siteCode, String meterCode) {
        return meters.findBySiteCodeAndMeterCode(siteCode, meterCode).map(EnergyMeterRecord::toDomain);
    }

    @Override
    public Optional<EnergyMeter> findMeterByAvampAssetId(String avampAssetId) {
        return meters.findByAvampAssetId(avampAssetId).map(EnergyMeterRecord::toDomain);
    }

    @Override
    public Optional<EnergyMeter> findMeterByVendorRef(String vendorMeterRef) {
        return meters.findByVendorMeterRef(vendorMeterRef).map(EnergyMeterRecord::toDomain);
    }

    @Override
    public List<EnergyMeter> findMeters(String siteCode, Utility utility, boolean activeOnly) {
        return meters.search(siteCode, utility, activeOnly ? MeterStatus.ACTIVE : null).stream()
                .map(EnergyMeterRecord::toDomain).toList();
    }

    // ---- readings -------------------------------------------------------------------------------

    @Override
    public ConsumptionReading saveReading(ConsumptionReading reading) {
        Optional<EnergyReadingRecord> existing = readings.findById(reading.id());
        existing.ifPresent(record -> record.requireNotStale(reading.metadata().version()));
        EnergyReadingRecord record = existing.orElseGet(EnergyReadingRecord::empty);
        record.apply(reading);
        return readings.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<ConsumptionReading> findReading(UUID id) {
        return readings.findById(id).map(EnergyReadingRecord::toDomain);
    }

    @Override
    public Optional<ConsumptionReading> findReadingBySourceReference(String sourceReference) {
        return readings.findBySourceReference(sourceReference).map(EnergyReadingRecord::toDomain);
    }

    @Override
    public Optional<ConsumptionReading> findLatestPostedReading(UUID meterId) {
        return readings.findFirstByMeterIdAndStatusOrderByObservedAtDesc(meterId, ReadingStatus.POSTED)
                .map(EnergyReadingRecord::toDomain);
    }

    @Override
    public List<ConsumptionReading> findPostedReadings(UUID meterId, Instant from, Instant to) {
        return readings.findByMeterIdAndStatusAndObservedAtGreaterThanEqualAndObservedAtLessThanOrderByObservedAtAsc(
                meterId, ReadingStatus.POSTED, from, to).stream().map(EnergyReadingRecord::toDomain).toList();
    }

    @Override
    public List<ConsumptionReading> findReadings(ReadingQuery query) {
        return readings.search(query.siteCode(), query.meterId(), query.status(),
                query.from() == null ? NO_LOWER : query.from(), query.to() == null ? NO_UPPER : query.to(),
                PageRequest.of(0, Math.max(1, query.limit()))).stream().map(EnergyReadingRecord::toDomain).toList();
    }

    @Override
    public int purgeReadingsObservedBefore(Instant cutoff) {
        return readings.purge(cutoff, ReadingStatus.HELD);
    }

    // ---- the consumption record -----------------------------------------------------------------

    @Override
    public Optional<DailyConsumption> findDaily(UUID meterId, LocalDate day) {
        return daily.findByMeterIdAndDay(meterId, day).map(EnergyDailyConsumptionRecord::toDomain);
    }

    @Override
    public DailyConsumption saveDaily(DailyConsumption value) {
        Optional<EnergyDailyConsumptionRecord> existing = daily.findById(value.id());
        existing.ifPresent(record -> record.requireNotStale(value.metadata().version()));
        EnergyDailyConsumptionRecord record = existing.orElseGet(EnergyDailyConsumptionRecord::empty);
        record.apply(value);
        return daily.saveAndFlush(record).toDomain();
    }

    @Override
    public List<DailyConsumption> findDaily(String siteCode, Utility utility, LocalDate from, LocalDate to) {
        return daily.search(siteCode, utility, from, to).stream().map(EnergyDailyConsumptionRecord::toDomain).toList();
    }

    @Override
    public List<DailyConsumption> findDailyForMeter(UUID meterId, LocalDate from, LocalDate to) {
        return daily.findByMeterIdAndDayGreaterThanEqualAndDayLessThanOrderByDayAsc(meterId, from, to).stream()
                .map(EnergyDailyConsumptionRecord::toDomain).toList();
    }

    // ---- budgets, tariffs, factors ------------------------------------------------------------

    @Override
    public EnergyBudget saveBudget(EnergyBudget budget) {
        return budgets.saveAndFlush(EnergyBudgetRecord.of(budget)).toDomain();
    }

    @Override
    public List<EnergyBudget> findBudgets(String siteCode, Utility utility, EnergyPeriod period) {
        return budgets.findBySiteCodeAndUtilityAndPeriodTypeAndPeriodStart(siteCode, utility, period.type(),
                period.start()).stream().map(EnergyBudgetRecord::toDomain).toList();
    }

    @Override
    public List<EnergyBudget> findBudgets(String siteCode, LocalDate from, LocalDate to) {
        return budgets.findBySiteCodeAndPeriodStartGreaterThanEqualAndPeriodStartLessThan(siteCode, from, to).stream()
                .map(EnergyBudgetRecord::toDomain).toList();
    }

    @Override
    public EnergyTariff saveTariff(EnergyTariff tariff) {
        return tariffs.saveAndFlush(EnergyTariffRecord.of(tariff)).toDomain();
    }

    @Override
    public List<EnergyTariff> findTariffs(String siteCode, Utility utility) {
        return tariffs.findBySiteCodeAndUtility(siteCode, utility).stream().map(EnergyTariffRecord::toDomain).toList();
    }

    @Override
    public EmissionFactor saveEmissionFactor(EmissionFactor factor) {
        return factors.saveAndFlush(EnergyEmissionFactorRecord.of(factor)).toDomain();
    }

    @Override
    public List<EmissionFactor> findEmissionFactors(String siteCode, Utility utility) {
        return factors.findBySiteCodeAndUtility(siteCode, utility).stream().map(EnergyEmissionFactorRecord::toDomain)
                .toList();
    }

    // ---- closed periods, alerts, KPIs ----------------------------------------------------------

    @Override
    public Optional<PeriodVarianceResult> findResult(String siteCode, Utility utility, EnergyPeriod period) {
        return results.findBySiteCodeAndUtilityAndPeriodTypeAndPeriodStart(siteCode, utility, period.type(),
                period.start()).map(EnergyPeriodResultRecord::toDomain);
    }

    @Override
    public PeriodVarianceResult saveResult(PeriodVarianceResult result) {
        return results.saveAndFlush(EnergyPeriodResultRecord.of(result)).toDomain();
    }

    @Override
    public List<PeriodVarianceResult> findResults(String siteCode, LocalDate from, LocalDate to) {
        return results.findBySiteCodeAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAsc(
                siteCode, from, to).stream().map(EnergyPeriodResultRecord::toDomain).toList();
    }

    @Override
    public Optional<EnergyAlert> findAlertByKey(String alertKey) {
        return alerts.findByAlertKey(alertKey).map(EnergyAlertRecord::toDomain);
    }

    @Override
    public EnergyAlert saveAlert(EnergyAlert alert) {
        return alerts.saveAndFlush(EnergyAlertRecord.of(alert)).toDomain();
    }

    @Override
    public List<EnergyAlert> findAlerts(String siteCode, EnergyAlert.EnergyAlertType type, Instant since, int limit) {
        return alerts.search(siteCode, type, since == null ? NO_LOWER : since, PageRequest.of(0, Math.max(1, limit)))
                .stream().map(EnergyAlertRecord::toDomain).toList();
    }

    @Override
    public Optional<SustainabilityKpi> findKpiByKey(String kpiKey) {
        return kpis.findByKpiKey(kpiKey).map(SustainabilityKpiRecord::toDomain);
    }

    @Override
    public SustainabilityKpi saveKpi(SustainabilityKpi kpi) {
        Optional<SustainabilityKpiRecord> existing = kpis.findById(kpi.id());
        existing.ifPresent(record -> record.requireNotStale(kpi.metadata().version()));
        SustainabilityKpiRecord record = existing.orElseGet(SustainabilityKpiRecord::empty);
        record.apply(kpi);
        return kpis.saveAndFlush(record).toDomain();
    }

    @Override
    public List<SustainabilityKpi> findKpis(KpiQuery query) {
        return kpis.search(query.siteCode(), query.scope(), query.utility(), query.periodType(), query.from(),
                query.to(), PageRequest.of(0, Math.max(1, query.limit()))).stream()
                .map(SustainabilityKpiRecord::toDomain).toList();
    }

    private static boolean mentions(Throwable failure, String constraint) {
        for (Throwable cursor = failure; cursor != null; cursor = cursor.getCause()) {
            String text = cursor.getMessage();
            if (text != null && text.toLowerCase(Locale.ROOT).contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
