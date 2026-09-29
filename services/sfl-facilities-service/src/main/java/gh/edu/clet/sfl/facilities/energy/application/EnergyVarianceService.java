package gh.edu.clet.sfl.facilities.energy.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyBudget;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyTariff;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.PeriodVarianceResult;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.energy.domain.policy.AnomalyPolicy;
import gh.edu.clet.sfl.facilities.energy.domain.policy.VariancePolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budgets, tariffs, period close, variance and anomaly alerting - SRS-SFL-S157-02.
 *
 * <h2>Why the close is where variance is computed</h2>
 *
 * The acceptance criterion is "when the period closes, then a variance alert is raised naming the site and
 * utility". So variance is a fact about a closed period, computed once and frozen in a
 * {@link PeriodVarianceResult} with the budget and tariff versions it used. The provisional figure the
 * dashboard shows for an open month is computed on read and never stored; nothing can mistake it for the
 * closed one, and no later budget revision can reach back into a closed result.
 *
 * <h2>Who is told</h2>
 *
 * The workflow ends "Facilities Director notified with drill-down to site/meter". Each alert is an
 * {@link EnergyAlert} row (the drill-down: site, utility, building, meter) and an outbox event. S153's
 * notification port has no energy notification kind and belongs to another module, so no person-directed
 * notification is sent yet - recorded in the gap report rather than smuggled through a work-order kind.
 */
@Service
public class EnergyVarianceService {

    private static final String BUDGET = "EnergyBudget";
    private static final String TARIFF = "EnergyTariff";
    private static final String RESULT = "EnergyPeriodResult";
    private static final String ALERT = "EnergyAlert";

    private final EnergyRepository repository;
    private final FacilitiesRepository facilities;
    private final EnergyConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public EnergyVarianceService(EnergyRepository repository, FacilitiesRepository facilities,
            EnergyConfiguration configuration, FacilitiesAuthorization authorization, AuditPort audit,
            ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.facilities = facilities;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    // =============================================================================================
    // Budgets and tariffs - versioned, insert-only
    // =============================================================================================

    @Transactional
    public EnergyBudget createBudget(EnergyCommands.CreateBudget command) {
        String site = requireSite(command.siteCode());
        authorization.require(command.actor(), SflPermission.FACILITIES_ENERGY_BUDGET_MANAGE, site, command.channel(),
                BUDGET, "new");
        requireUtility(command.utility());
        if (command.periodStart() == null) {
            throw new FacilitiesException.ValidationFailedException("A budget needs the month it is for.");
        }
        EnergyPeriod period = EnergyPeriod.month(command.periodStart());
        int version = repository.findBudgets(site, command.utility(), period).stream()
                .mapToInt(EnergyBudget::version).max().orElse(0) + 1;
        EnergyBudget budget = repository.saveBudget(EnergyBudget.version(UUID.randomUUID(), site, command.utility(),
                period, version, command.consumptionBudget(), command.costBudget(), command.currency(),
                command.reason(), command.actor().actorId(), clock.instant(), command.channel(),
                command.actor().correlationId()));
        audit.record(command.actor(), command.channel(), AuditAction.ENERGY_BUDGET_VERSION_CREATED, BUDGET,
                budget.id().toString(), site, null, budget);
        return budget;
    }

    @Transactional
    public EnergyTariff createTariff(EnergyCommands.CreateTariff command) {
        String site = requireSite(command.siteCode());
        authorization.require(command.actor(), SflPermission.FACILITIES_ENERGY_BUDGET_MANAGE, site, command.channel(),
                TARIFF, "new");
        requireUtility(command.utility());
        int version = repository.findTariffs(site, command.utility()).stream()
                .mapToInt(EnergyTariff::version).max().orElse(0) + 1;
        EnergyTariff tariff = repository.saveTariff(EnergyTariff.version(UUID.randomUUID(), site, command.utility(),
                version, command.unitRate(), command.currency(), command.validFrom(), command.validTo(),
                command.reason(), command.actor().actorId(), clock.instant(), command.channel(),
                command.actor().correlationId()));
        audit.record(command.actor(), command.channel(), AuditAction.ENERGY_TARIFF_VERSION_CREATED, TARIFF,
                tariff.id().toString(), site, null, tariff);
        return tariff;
    }

    @Transactional(readOnly = true)
    public List<EnergyBudget> budgets(String siteCode, LocalDate from, LocalDate to, ActorContext actor,
            SourceChannel channel) {
        String site = requireSite(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, site, channel, BUDGET, "list");
        return repository.findBudgets(site, from == null ? LocalDate.of(2000, 1, 1) : from,
                to == null ? LocalDate.of(9999, 1, 1) : to).stream()
                .sorted(Comparator.comparing((EnergyBudget budget) -> budget.period().start())
                        .thenComparing(EnergyBudget::utility).thenComparingInt(EnergyBudget::version))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnergyTariff> tariffs(String siteCode, Utility utility, ActorContext actor, SourceChannel channel) {
        String site = requireSite(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, site, channel, TARIFF, "list");
        List<EnergyTariff> tariffs = new ArrayList<>();
        for (Utility each : utility == null ? EnumSet.allOf(Utility.class) : EnumSet.of(utility)) {
            tariffs.addAll(repository.findTariffs(site, each));
        }
        tariffs.sort(Comparator.comparing(EnergyTariff::utility).thenComparingInt(EnergyTariff::version));
        return tariffs;
    }

    // =============================================================================================
    // Period close - "when the period closes, then a variance alert is raised naming the site and utility"
    // =============================================================================================

    /** Closes an ended month at a site, for one utility or every utility with consumption or a budget. */
    @Transactional
    public List<PeriodVarianceResult> closePeriod(EnergyCommands.ClosePeriod command) {
        String site = requireSite(command.siteCode());
        authorization.require(command.actor(), SflPermission.FACILITIES_ENERGY_BUDGET_MANAGE, site, command.channel(),
                RESULT, "close");
        if (command.periodStart() == null) {
            throw new FacilitiesException.ValidationFailedException("Closing needs the month to close.");
        }
        EnergyPeriod period = EnergyPeriod.month(command.periodStart());
        if (!period.hasEndedBy(clock.instant())) {
            throw new FacilitiesException.ValidationFailedException("The month starting " + period.start()
                    + " has not ended; a period is closed only once it is over.");
        }
        Set<Utility> utilities = command.utility() == null ? utilitiesToClose(site, period) : Set.of(command.utility());
        List<PeriodVarianceResult> results = new ArrayList<>();
        for (Utility utility : utilities) {
            results.add(closeOne(site, utility, period, command.actor(), command.channel()));
        }
        return results;
    }

    /**
     * The scheduled close: every ended month in the look-back window, every site, every utility with
     * consumption or a budget, not yet closed. Idempotent - a closed period is returned, never recomputed.
     *
     * @return how many periods this run closed
     */
    @Transactional
    public int sweepCloses(ActorContext actor) {
        Instant now = clock.instant();
        EnergyPeriod period = EnergyPeriod.month(EnergyPeriod.dayOf(now)).previous();
        int closed = 0;
        for (int back = 0; back < configuration.closeLookbackPeriods(); back++, period = period.previous()) {
            for (Site site : facilities.findSites()) {
                for (Utility utility : utilitiesToClose(site.siteCode(), period)) {
                    if (repository.findResult(site.siteCode(), utility, period).isEmpty()) {
                        closeOne(site.siteCode(), utility, period, actor, SourceChannel.SCHEDULER);
                        closed++;
                    }
                }
            }
        }
        return closed;
    }

    PeriodVarianceResult closeOne(String site, Utility utility, EnergyPeriod period, ActorContext actor,
            SourceChannel channel) {
        Optional<PeriodVarianceResult> existing = repository.findResult(site, utility, period);
        if (existing.isPresent()) {
            return existing.get();
        }
        List<DailyConsumption> days = repository.findDaily(site, utility, period.start(), period.end());
        BigDecimal consumption = days.stream().map(DailyConsumption::consumption).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        int readings = days.stream().mapToInt(DailyConsumption::readingCount).sum();
        EnergyBudget budget = activeBudget(site, utility, period).orElse(null);
        EnergyTariff tariff = EnergyTariff.applicable(repository.findTariffs(site, utility), period).orElse(null);
        Instant at = clock.instant();
        PeriodVarianceResult result = repository.saveResult(VariancePolicy.close(UUID.randomUUID(), site, utility,
                period, consumption, readings, budget, tariff, configuration.varianceThresholdPct(site),
                actor.actorId(), at, channel, actor.correlationId()));
        audit.record(actor, channel, AuditAction.ENERGY_PERIOD_CLOSED, RESULT, result.id().toString(), site, null,
                result);
        if (result.varianceAlert()) {
            raise(actor, channel, EnergyAlert.EnergyAlertType.VARIANCE, "variance:" + site + ":" + utility + ":"
                    + period.start(), site, utility, null, null, period, consumption, result.consumptionBudget(),
                    result.variancePct() != null && VariancePolicy.breaches(result.variancePct(), result.thresholdPct())
                            ? result.variancePct() : result.costVariancePct(),
                    result.thresholdPct(), varianceMessage(result),
                    AuditAction.ENERGY_VARIANCE_ALERT_RAISED, EnergyEvents.VARIANCE_ALERT_RAISED);
        }
        if (result.tariffMissing()) {
            raise(actor, channel, EnergyAlert.EnergyAlertType.TARIFF_MISSING, "tariff-missing:" + site + ":" + utility
                    + ":" + period.start(), site, utility, null, null, period, consumption, null, null, null,
                    "Missing Tariff - " + site + " " + utility + " used " + plain(consumption) + " "
                            + utility.unitCode() + " in " + period.start().getMonth() + " " + period.start().getYear()
                            + " with no configured tariff. "
                            + FacilitiesErrorCode.ENERGY_TARIFF_MISSING.defaultMessage(),
                    AuditAction.ENERGY_TARIFF_MISSING_FLAGGED, EnergyEvents.TARIFF_MISSING);
        }
        return result;
    }

    private static String varianceMessage(PeriodVarianceResult result) {
        StringBuilder message = new StringBuilder("Variance alert - ").append(result.siteCode()).append(' ')
                .append(result.utility()).append(", ").append(result.period().start().getMonth()).append(' ')
                .append(result.period().start().getYear()).append(": ");
        if (result.variancePct() != null && VariancePolicy.breaches(result.variancePct(), result.thresholdPct())) {
            message.append("consumption ").append(plain(result.consumption())).append(' ')
                    .append(result.utility().unitCode()).append(" is ").append(plain(result.variancePct()))
                    .append("% over the budget of ").append(plain(result.consumptionBudget())).append(" (v")
                    .append(result.budgetVersion()).append(')');
        } else {
            message.append("cost ").append(plain(result.cost())).append(' ').append(result.currency())
                    .append(" is ").append(plain(result.costVariancePct())).append("% over the cost budget of ")
                    .append(plain(result.costBudget()));
        }
        return message.append(", beyond the ").append(plain(result.thresholdPct())).append("% threshold.").toString();
    }

    // =============================================================================================
    // Anomaly - "a sudden consumption spike versus the trailing baseline raises a distinct anomaly flag"
    // =============================================================================================

    /**
     * Judges one completed day for every AMI and stream meter at the given sites (all sites when {@code null}).
     * Manual meters are skipped: their consumption lands on the day of the walk (see {@link DailyConsumption}),
     * so every read day would look like a spike.
     *
     * @return the anomalies raised by this run
     */
    @Transactional
    public List<EnergyAlert> evaluateAnomalies(LocalDate day, String siteCode, ActorContext actor,
            SourceChannel channel) {
        List<String> sites;
        if (siteCode != null && !siteCode.isBlank()) {
            String site = requireSite(siteCode);
            authorization.require(actor, SflPermission.FACILITIES_ENERGY_BUDGET_MANAGE, site, channel, ALERT,
                    "anomaly-evaluation");
            sites = List.of(site);
        } else {
            authorization.require(actor, SflPermission.FACILITIES_ENERGY_BUDGET_MANAGE, channel, ALERT,
                    "anomaly-evaluation", "*");
            sites = facilities.findSites().stream().map(Site::siteCode)
                    .filter(site -> authorization.canAccessSite(actor, site)).toList();
        }
        LocalDate judged = day == null ? EnergyPeriod.dayOf(clock.instant()).minusDays(1) : day;
        if (!judged.isBefore(EnergyPeriod.dayOf(clock.instant()))) {
            throw new FacilitiesException.ValidationFailedException("Only a completed day can be judged for a spike.");
        }
        List<EnergyAlert> raised = new ArrayList<>();
        for (String site : sites) {
            for (EnergyMeter meter : repository.findMeters(site, null, true)) {
                if (meter.source() == MeterSource.MANUAL) {
                    continue;
                }
                evaluateMeter(meter, judged, actor, channel).ifPresent(raised::add);
            }
        }
        return raised;
    }

    private Optional<EnergyAlert> evaluateMeter(EnergyMeter meter, LocalDate day, ActorContext actor,
            SourceChannel channel) {
        String key = "anomaly:" + meter.id() + ":" + day;
        if (repository.findAlertByKey(key).isPresent()) {
            return Optional.empty();
        }
        Optional<DailyConsumption> today = repository.findDaily(meter.id(), day);
        if (today.isEmpty()) {
            return Optional.empty();
        }
        int window = configuration.anomalyBaselineDays(meter.siteCode());
        List<BigDecimal> baseline = repository.findDailyForMeter(meter.id(), day.minusDays(window), day).stream()
                .map(DailyConsumption::consumption).toList();
        BigDecimal threshold = configuration.anomalySpikePct(meter.siteCode());
        Optional<AnomalyPolicy.Spike> spike = AnomalyPolicy.judge(today.get().consumption(), baseline,
                configuration.anomalyMinimumBaselineDays(meter.siteCode()), threshold);
        return spike.map(found -> raise(actor, channel, EnergyAlert.EnergyAlertType.ANOMALY, key, meter.siteCode(),
                meter.utility(), meter.buildingCode(), meter.id(), new EnergyPeriod(EnergyPeriod.PeriodType.DAY, day),
                today.get().consumption(), found.baseline(), found.deviationPct(), threshold,
                "Anomaly - meter " + meter.meterCode() + " (" + meter.siteCode() + " " + meter.buildingCode() + ", "
                        + meter.utility() + ") used " + plain(today.get().consumption()) + " "
                        + meter.utility().unitCode() + " on " + day + ", " + plain(found.deviationPct())
                        + "% above its trailing baseline of " + plain(found.baseline()) + " per day. Raised "
                        + "independently of budget: a spike can occur within budget."));
    }

    // =============================================================================================
    // Views
    // =============================================================================================

    /** A month's budget-versus-actual: the frozen result once closed, otherwise a provisional computation. */
    public record VarianceView(PeriodVarianceResult result, boolean closed) {
    }

    @Transactional(readOnly = true)
    public VarianceView variance(String siteCode, Utility utility, LocalDate periodStart, ActorContext actor,
            SourceChannel channel) {
        String site = requireSite(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, site, channel, RESULT, "variance");
        requireUtility(utility);
        EnergyPeriod period = EnergyPeriod.month(periodStart == null ? EnergyPeriod.dayOf(clock.instant()) : periodStart);
        Optional<PeriodVarianceResult> closed = repository.findResult(site, utility, period);
        if (closed.isPresent()) {
            return new VarianceView(closed.get(), true);
        }
        List<DailyConsumption> days = repository.findDaily(site, utility, period.start(), period.end());
        BigDecimal consumption = days.stream().map(DailyConsumption::consumption).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        PeriodVarianceResult provisional = VariancePolicy.close(UUID.randomUUID(), site, utility, period, consumption,
                days.stream().mapToInt(DailyConsumption::readingCount).sum(),
                activeBudget(site, utility, period).orElse(null),
                EnergyTariff.applicable(repository.findTariffs(site, utility), period).orElse(null),
                configuration.varianceThresholdPct(site), actor.actorId(), clock.instant(), channel,
                actor.correlationId());
        return new VarianceView(provisional, false);
    }

    @Transactional(readOnly = true)
    public List<PeriodVarianceResult> closedPeriods(String siteCode, LocalDate from, LocalDate to, ActorContext actor,
            SourceChannel channel) {
        String site = requireSite(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, site, channel, RESULT, "list");
        return repository.findResults(site, from == null ? LocalDate.of(2000, 1, 1) : from,
                to == null ? LocalDate.of(9999, 1, 1) : to);
    }

    public record CostEstimate(String siteCode, Utility utility, EnergyPeriod period, BigDecimal consumption,
            BigDecimal unitRate, String currency, Integer tariffVersion, BigDecimal cost) {
    }

    /**
     * The cost of a month's consumption at its tariff. With consumption and no tariff this refuses with
     * {@code ENERGY_TARIFF_MISSING} (S157-02 Missing Tariff) - it never answers zero.
     */
    @Transactional(readOnly = true)
    public CostEstimate cost(String siteCode, Utility utility, LocalDate periodStart, ActorContext actor,
            SourceChannel channel) {
        String site = requireSite(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, site, channel, TARIFF, "cost");
        requireUtility(utility);
        EnergyPeriod period = EnergyPeriod.month(periodStart == null ? EnergyPeriod.dayOf(clock.instant()) : periodStart);
        BigDecimal consumption = repository.findDaily(site, utility, period.start(), period.end()).stream()
                .map(DailyConsumption::consumption).reduce(BigDecimal.ZERO, BigDecimal::add);
        Optional<EnergyTariff> tariff = EnergyTariff.applicable(repository.findTariffs(site, utility), period);
        if (tariff.isEmpty()) {
            if (consumption.signum() > 0) {
                throw new FacilitiesException(FacilitiesErrorCode.ENERGY_TARIFF_MISSING, "Missing Tariff - " + site
                        + " " + utility + " has " + plain(consumption) + " " + utility.unitCode()
                        + " of consumption in the month starting " + period.start() + " and no configured tariff. "
                        + FacilitiesErrorCode.ENERGY_TARIFF_MISSING.defaultMessage());
            }
            return new CostEstimate(site, utility, period, consumption, null, null, null, null);
        }
        return new CostEstimate(site, utility, period, consumption, tariff.get().unitRate(), tariff.get().currency(),
                tariff.get().version(), consumption.multiply(tariff.get().unitRate()).setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public List<EnergyAlert> alerts(String siteCode, EnergyAlert.EnergyAlertType type, Instant since, int limit,
            ActorContext actor, SourceChannel channel) {
        String site = siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, channel, ALERT, "list", site);
        authorization.requireRequestedSite(actor, site, channel, ALERT);
        return authorization.filterBySite(actor, repository.findAlerts(site, type,
                since == null ? Instant.EPOCH : since, Math.min(Math.max(1, limit), 500)), EnergyAlert::siteCode);
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    private Optional<EnergyBudget> activeBudget(String site, Utility utility, EnergyPeriod period) {
        return repository.findBudgets(site, utility, period).stream().max(Comparator.comparingInt(EnergyBudget::version));
    }

    private Set<Utility> utilitiesToClose(String site, EnergyPeriod period) {
        Set<Utility> utilities = EnumSet.noneOf(Utility.class);
        utilities.addAll(repository.findDaily(site, null, period.start(), period.end()).stream()
                .map(DailyConsumption::utility).collect(Collectors.toSet()));
        utilities.addAll(repository.findBudgets(site, period.start(), period.end()).stream()
                .map(EnergyBudget::utility).collect(Collectors.toSet()));
        return utilities;
    }

    private EnergyAlert raise(ActorContext actor, SourceChannel channel, EnergyAlert.EnergyAlertType type, String key,
            String site, Utility utility, String buildingCode, UUID meterId, EnergyPeriod period, BigDecimal observed,
            BigDecimal reference, BigDecimal deviation, BigDecimal threshold, String message, AuditAction action,
            String eventType) {
        Optional<EnergyAlert> existing = repository.findAlertByKey(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant at = clock.instant();
        EnergyAlert alert = repository.saveAlert(new EnergyAlert(UUID.randomUUID(), key, site, type, utility,
                buildingCode, meterId, period, observed, reference, deviation, threshold, message, at,
                RecordMetadata.createdBy(actor.actorId(), at, channel, actor.correlationId())));
        audit.record(actor, channel, action, ALERT, alert.id().toString(), site, null, alert);
        outbox.record(eventType, 1, ALERT, alert.id(), site, actor.correlationId(), actor.actorId(),
                EnergyEvents.AlertRaised.of(alert));
        return alert;
    }

    private EnergyAlert raise(ActorContext actor, SourceChannel channel, EnergyAlert.EnergyAlertType type, String key,
            String site, Utility utility, String buildingCode, UUID meterId, EnergyPeriod period, BigDecimal observed,
            BigDecimal reference, BigDecimal deviation, BigDecimal threshold, String message) {
        return raise(actor, channel, type, key, site, utility, buildingCode, meterId, period, observed, reference,
                deviation, threshold, message, AuditAction.ENERGY_ANOMALY_FLAGGED, EnergyEvents.ANOMALY_FLAGGED);
    }

    private static void requireUtility(Utility utility) {
        if (utility == null) {
            throw new FacilitiesException.ValidationFailedException("utility is required.");
        }
    }

    private static String requireSite(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("siteCode is required.");
        }
        return EstateCodes.normalize(siteCode);
    }

    static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
