package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption;
import gh.edu.clet.sfl.facilities.energy.domain.EmissionFactor;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyBudget;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyTariff;
import gh.edu.clet.sfl.facilities.energy.domain.PeriodVarianceResult;
import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * An in-memory {@link EnergyRepository} for the S157 application tests.
 *
 * <p>Reproduces the one uniqueness rule application code depends on for its own error state: one meter
 * per AVAMP asset id ({@code saveMeter} throws {@code ENERGY_DEVICE_DOUBLE_REGISTERED} exactly as
 * {@code ux_energy_meters_avamp} does in PostgreSQL), so {@code EnergyMeterService}'s S157-04 test does
 * not need a real database. Every other uniqueness constraint (meter code, source reference, alert key,
 * budget/tariff/factor version, KPI key) is likewise enforced here, for the same reason.
 */
public class InMemoryEnergyRepository implements EnergyRepository {

    private final Map<UUID, EnergyMeter> meters = new LinkedHashMap<>();
    private final Map<UUID, ConsumptionReading> readings = new LinkedHashMap<>();
    private final Map<UUID, DailyConsumption> daily = new LinkedHashMap<>();
    private final Map<UUID, EnergyBudget> budgets = new LinkedHashMap<>();
    private final Map<UUID, EnergyTariff> tariffs = new LinkedHashMap<>();
    private final Map<UUID, EmissionFactor> factors = new LinkedHashMap<>();
    private final Map<UUID, PeriodVarianceResult> results = new LinkedHashMap<>();
    private final Map<UUID, EnergyAlert> alerts = new LinkedHashMap<>();
    private final Map<UUID, SustainabilityKpi> kpis = new LinkedHashMap<>();

    // ---- meters ---------------------------------------------------------------------------------

    @Override
    public EnergyMeter saveMeter(EnergyMeter meter) {
        if (meter.avampAssetId() != null) {
            meters.values().stream()
                    .filter(existing -> !existing.id().equals(meter.id()))
                    .filter(existing -> meter.avampAssetId().equals(existing.avampAssetId()))
                    .findFirst()
                    .ifPresent(clash -> {
                        throw new FacilitiesException(FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED,
                                FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED.defaultMessage()
                                        + " AVAMP asset " + meter.avampAssetId() + " was registered by another "
                                        + "request at the same time.");
                    });
        }
        meters.put(meter.id(), meter);
        return meter;
    }

    @Override
    public Optional<EnergyMeter> findMeter(UUID id) {
        return Optional.ofNullable(meters.get(id));
    }

    @Override
    public Optional<EnergyMeter> findMeterByCode(String siteCode, String meterCode) {
        return meters.values().stream()
                .filter(m -> m.siteCode().equals(siteCode) && m.meterCode().equals(meterCode)).findFirst();
    }

    @Override
    public Optional<EnergyMeter> findMeterByAvampAssetId(String avampAssetId) {
        return meters.values().stream().filter(m -> avampAssetId.equals(m.avampAssetId())).findFirst();
    }

    @Override
    public Optional<EnergyMeter> findMeterByVendorRef(String vendorMeterRef) {
        return meters.values().stream().filter(m -> vendorMeterRef.equals(m.vendorMeterRef())).findFirst();
    }

    @Override
    public List<EnergyMeter> findMeters(String siteCode, Utility utility, boolean activeOnly) {
        return meters.values().stream()
                .filter(m -> siteCode == null || m.siteCode().equals(siteCode))
                .filter(m -> utility == null || m.utility() == utility)
                .filter(m -> !activeOnly || m.isActive())
                .sorted(Comparator.comparing(EnergyMeter::siteCode).thenComparing(EnergyMeter::meterCode))
                .toList();
    }

    // ---- readings -------------------------------------------------------------------------------

    @Override
    public ConsumptionReading saveReading(ConsumptionReading reading) {
        readings.put(reading.id(), reading);
        return reading;
    }

    @Override
    public Optional<ConsumptionReading> findReading(UUID id) {
        return Optional.ofNullable(readings.get(id));
    }

    @Override
    public Optional<ConsumptionReading> findReadingBySourceReference(String sourceReference) {
        return readings.values().stream().filter(r -> sourceReference.equals(r.sourceReference())).findFirst();
    }

    @Override
    public Optional<ConsumptionReading> findLatestPostedReading(UUID meterId) {
        return readings.values().stream()
                .filter(r -> r.meterId().equals(meterId) && r.status() == ReadingStatus.POSTED)
                .max(Comparator.comparing(ConsumptionReading::observedAt));
    }

    @Override
    public List<ConsumptionReading> findPostedReadings(UUID meterId, Instant from, Instant to) {
        return readings.values().stream()
                .filter(r -> r.meterId().equals(meterId) && r.status() == ReadingStatus.POSTED)
                .filter(r -> !r.observedAt().isBefore(from) && r.observedAt().isBefore(to))
                .sorted(Comparator.comparing(ConsumptionReading::observedAt))
                .toList();
    }

    @Override
    public List<ConsumptionReading> findReadings(ReadingQuery query) {
        Instant from = query.from() == null ? Instant.EPOCH : query.from();
        Instant to = query.to() == null ? Instant.parse("9999-01-01T00:00:00Z") : query.to();
        return readings.values().stream()
                .filter(r -> query.siteCode() == null || r.siteCode().equals(query.siteCode()))
                .filter(r -> query.meterId() == null || r.meterId().equals(query.meterId()))
                .filter(r -> query.status() == null || r.status() == query.status())
                .filter(r -> !r.observedAt().isBefore(from) && r.observedAt().isBefore(to))
                .sorted(Comparator.comparing(ConsumptionReading::observedAt).reversed())
                .limit(Math.max(1, query.limit()))
                .toList();
    }

    @Override
    public int purgeReadingsObservedBefore(Instant cutoff) {
        List<UUID> victims = readings.values().stream()
                .filter(r -> r.observedAt().isBefore(cutoff) && r.status() != ReadingStatus.HELD)
                .map(ConsumptionReading::id).toList();
        victims.forEach(readings::remove);
        return victims.size();
    }

    // ---- the consumption record -----------------------------------------------------------------

    @Override
    public Optional<DailyConsumption> findDaily(UUID meterId, LocalDate day) {
        return daily.values().stream().filter(d -> d.meterId().equals(meterId) && d.day().equals(day)).findFirst();
    }

    @Override
    public DailyConsumption saveDaily(DailyConsumption value) {
        daily.put(value.id(), value);
        return value;
    }

    @Override
    public List<DailyConsumption> findDaily(String siteCode, Utility utility, LocalDate from, LocalDate to) {
        return daily.values().stream()
                .filter(d -> siteCode == null || d.siteCode().equals(siteCode))
                .filter(d -> utility == null || d.utility() == utility)
                .filter(d -> !d.day().isBefore(from) && d.day().isBefore(to))
                .sorted(Comparator.comparing(DailyConsumption::day))
                .toList();
    }

    @Override
    public List<DailyConsumption> findDailyForMeter(UUID meterId, LocalDate from, LocalDate to) {
        return daily.values().stream()
                .filter(d -> d.meterId().equals(meterId))
                .filter(d -> !d.day().isBefore(from) && d.day().isBefore(to))
                .sorted(Comparator.comparing(DailyConsumption::day))
                .toList();
    }

    // ---- budgets, tariffs, factors ------------------------------------------------------------

    @Override
    public EnergyBudget saveBudget(EnergyBudget budget) {
        budgets.put(budget.id(), budget);
        return budget;
    }

    @Override
    public List<EnergyBudget> findBudgets(String siteCode, Utility utility, EnergyPeriod period) {
        return budgets.values().stream()
                .filter(b -> b.siteCode().equals(siteCode) && b.utility() == utility
                        && b.period().type() == period.type() && b.period().start().equals(period.start()))
                .toList();
    }

    @Override
    public List<EnergyBudget> findBudgets(String siteCode, LocalDate from, LocalDate to) {
        return budgets.values().stream()
                .filter(b -> b.siteCode().equals(siteCode))
                .filter(b -> !b.period().start().isBefore(from) && b.period().start().isBefore(to))
                .toList();
    }

    @Override
    public EnergyTariff saveTariff(EnergyTariff tariff) {
        tariffs.put(tariff.id(), tariff);
        return tariff;
    }

    @Override
    public List<EnergyTariff> findTariffs(String siteCode, Utility utility) {
        return tariffs.values().stream().filter(t -> t.siteCode().equals(siteCode) && t.utility() == utility).toList();
    }

    @Override
    public EmissionFactor saveEmissionFactor(EmissionFactor factor) {
        factors.put(factor.id(), factor);
        return factor;
    }

    @Override
    public List<EmissionFactor> findEmissionFactors(String siteCode, Utility utility) {
        return factors.values().stream().filter(f -> f.siteCode().equals(siteCode) && f.utility() == utility).toList();
    }

    // ---- closed periods, alerts, KPIs ----------------------------------------------------------

    @Override
    public Optional<PeriodVarianceResult> findResult(String siteCode, Utility utility, EnergyPeriod period) {
        return results.values().stream()
                .filter(r -> r.siteCode().equals(siteCode) && r.utility() == utility
                        && r.period().type() == period.type() && r.period().start().equals(period.start()))
                .findFirst();
    }

    @Override
    public PeriodVarianceResult saveResult(PeriodVarianceResult result) {
        results.put(result.id(), result);
        return result;
    }

    @Override
    public List<PeriodVarianceResult> findResults(String siteCode, LocalDate from, LocalDate to) {
        return results.values().stream()
                .filter(r -> r.siteCode().equals(siteCode))
                .filter(r -> !r.period().start().isBefore(from) && r.period().start().isBefore(to))
                .sorted(Comparator.comparing(r -> r.period().start()))
                .toList();
    }

    @Override
    public Optional<EnergyAlert> findAlertByKey(String alertKey) {
        return alerts.values().stream().filter(a -> a.alertKey().equals(alertKey)).findFirst();
    }

    @Override
    public EnergyAlert saveAlert(EnergyAlert alert) {
        alerts.put(alert.id(), alert);
        return alert;
    }

    @Override
    public List<EnergyAlert> findAlerts(String siteCode, EnergyAlert.EnergyAlertType type, Instant since, int limit) {
        return alerts.values().stream()
                .filter(a -> siteCode == null || a.siteCode().equals(siteCode))
                .filter(a -> type == null || a.type() == type)
                .filter(a -> !a.raisedAt().isBefore(since))
                .sorted(Comparator.comparing(EnergyAlert::raisedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public Optional<SustainabilityKpi> findKpiByKey(String kpiKey) {
        return kpis.values().stream().filter(k -> k.kpiKey().equals(kpiKey)).findFirst();
    }

    @Override
    public SustainabilityKpi saveKpi(SustainabilityKpi kpi) {
        kpis.put(kpi.id(), kpi);
        return kpi;
    }

    @Override
    public List<SustainabilityKpi> findKpis(KpiQuery query) {
        LocalDate from = query.from() == null ? LocalDate.of(2000, 1, 1) : query.from();
        LocalDate to = query.to() == null ? LocalDate.of(9999, 1, 1) : query.to();
        return kpis.values().stream()
                .filter(k -> query.siteCode() == null || k.siteCode().equals(query.siteCode()))
                .filter(k -> query.scope() == null || k.scope() == query.scope())
                .filter(k -> query.utility() == null || k.utility() == query.utility())
                .filter(k -> query.periodType() == null || k.period().type() == query.periodType())
                .filter(k -> !k.period().start().isBefore(from) && k.period().start().isBefore(to))
                .sorted(Comparator.comparing((SustainabilityKpi k) -> k.period().start()).reversed())
                .limit(Math.max(1, query.limit()))
                .toList();
    }

    /** For a test to inspect everything raised, in insertion order. */
    public List<EnergyAlert> allAlerts() {
        return new ArrayList<>(alerts.values());
    }
}
