package gh.edu.clet.sfl.facilities.energy.application.ports;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything S157 persists, behind one port so the application layer never names a JPA type and the
 * acceptance tests run over an in-memory double.
 *
 * <p>Every list method takes a site filter, and {@code null} means "every site the database session may
 * see". The service always narrows again with {@code FacilitiesAuthorization.filterBySite}: the SQL filter
 * is for efficiency and the row-level-security policy is the backstop, and neither alone is the rule.
 */
public interface EnergyRepository {

    // ---- meters ---------------------------------------------------------------------------------

    /**
     * @throws gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException with
     *         {@code ENERGY_DEVICE_DOUBLE_REGISTERED} when the AVAMP identity is already a meter - the
     *         translation of {@code ux_energy_meters_avamp}, for the registration that lost a race
     */
    EnergyMeter saveMeter(EnergyMeter meter);

    Optional<EnergyMeter> findMeter(UUID id);

    Optional<EnergyMeter> findMeterByCode(String siteCode, String meterCode);

    Optional<EnergyMeter> findMeterByAvampAssetId(String avampAssetId);

    Optional<EnergyMeter> findMeterByVendorRef(String vendorMeterRef);

    List<EnergyMeter> findMeters(String siteCode, Utility utility, boolean activeOnly);

    // ---- readings -------------------------------------------------------------------------------

    ConsumptionReading saveReading(ConsumptionReading reading);

    Optional<ConsumptionReading> findReading(UUID id);

    Optional<ConsumptionReading> findReadingBySourceReference(String sourceReference);

    /** The most recent posted reading on a meter - the base of a register delta and of a stream interval. */
    Optional<ConsumptionReading> findLatestPostedReading(UUID meterId);

    /** Posted readings on a meter observed in {@code [from, to)}. */
    List<ConsumptionReading> findPostedReadings(UUID meterId, Instant from, Instant to);

    record ReadingQuery(String siteCode, UUID meterId, ReadingStatus status, Instant from, Instant to, int limit) {
    }

    List<ConsumptionReading> findReadings(ReadingQuery query);

    /**
     * SRS 4.2 retention: deletes posted and rejected readings observed before {@code cutoff}. A held reading
     * is never purged - it is an open question somebody has not answered. Daily consumption, period results
     * and KPIs are kept.
     *
     * @return the number of readings removed
     */
    int purgeReadingsObservedBefore(Instant cutoff);

    // ---- the consumption record -----------------------------------------------------------------

    Optional<DailyConsumption> findDaily(UUID meterId, LocalDate day);

    DailyConsumption saveDaily(DailyConsumption daily);

    /** Daily rows in {@code [from, to)}. */
    List<DailyConsumption> findDaily(String siteCode, Utility utility, LocalDate from, LocalDate to);

    List<DailyConsumption> findDailyForMeter(UUID meterId, LocalDate from, LocalDate to);

    // ---- budgets, tariffs, factors - versioned, insert-only ------------------------------------

    EnergyBudget saveBudget(EnergyBudget budget);

    /** Every version for the site/utility/period, any order. */
    List<EnergyBudget> findBudgets(String siteCode, Utility utility, EnergyPeriod period);

    List<EnergyBudget> findBudgets(String siteCode, LocalDate from, LocalDate to);

    EnergyTariff saveTariff(EnergyTariff tariff);

    List<EnergyTariff> findTariffs(String siteCode, Utility utility);

    EmissionFactor saveEmissionFactor(EmissionFactor factor);

    List<EmissionFactor> findEmissionFactors(String siteCode, Utility utility);

    // ---- closed periods, alerts, KPIs ----------------------------------------------------------

    Optional<PeriodVarianceResult> findResult(String siteCode, Utility utility, EnergyPeriod period);

    /** Insert only. A closed period is never rewritten. */
    PeriodVarianceResult saveResult(PeriodVarianceResult result);

    List<PeriodVarianceResult> findResults(String siteCode, LocalDate from, LocalDate to);

    Optional<EnergyAlert> findAlertByKey(String alertKey);

    EnergyAlert saveAlert(EnergyAlert alert);

    List<EnergyAlert> findAlerts(String siteCode, EnergyAlert.EnergyAlertType type, Instant since, int limit);

    Optional<SustainabilityKpi> findKpiByKey(String kpiKey);

    SustainabilityKpi saveKpi(SustainabilityKpi kpi);

    record KpiQuery(String siteCode, SustainabilityKpi.KpiScope scope, Utility utility,
            EnergyPeriod.PeriodType periodType, LocalDate from, LocalDate to, int limit) {
    }

    List<SustainabilityKpi> findKpis(KpiQuery query);
}
