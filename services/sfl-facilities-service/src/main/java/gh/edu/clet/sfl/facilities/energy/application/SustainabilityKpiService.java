package gh.edu.clet.sfl.facilities.energy.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption;
import gh.edu.clet.sfl.facilities.energy.domain.EmissionFactor;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi.EmissionFactorStatus;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi.KpiScope;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.energy.domain.policy.CompletenessPolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sustainability KPIs and their publication to Analytics (S225) - SRS-SFL-S157-03.
 *
 * <h2>The contract</h2>
 *
 * "Published to Analytics (S225) via the standard event/read-model contract, not a one-off export." Both
 * halves are here: every KPI is a row in the {@code sustainability_kpis} read model (served by
 * {@code GET /api/v1/facilities/energy/kpis}) and every new or changed row is announced on the outbox as
 * {@code sfl.ifimp.sustainability-kpi-published.v1} carrying the full row. A consumer can rebuild from the
 * read model and stay current from the events. S225 has no consumer yet - see the gap report.
 *
 * <h2>Completeness is never optional</h2>
 *
 * Every KPI carries its period, whether the period has ended, expected and received readings, the
 * percentage and a flag. Below the configured minimum it is LOW - published with the flag, never withheld
 * (S157-03 Incomplete Period) - and nothing below 100% is ever COMPLETE (enforced again by the schema).
 *
 * <h2>Rollups</h2>
 *
 * Per site, per building, and cluster-wide (every site), per utility. The cluster row sums the site rows'
 * expected and received counts rather than averaging their percentages, so a large site missing data
 * weighs as much as it should. It is stored under site {@code *} and visible only to cross-site callers.
 */
@Service
public class SustainabilityKpiService {

    private static final String RESOURCE = "SustainabilityKpi";
    private static final String FACTOR = "EnergyEmissionFactor";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final EnergyRepository repository;
    private final FacilitiesRepository facilities;
    private final EnergyConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public SustainabilityKpiService(EnergyRepository repository, FacilitiesRepository facilities,
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

    // ---- emission factors - versioned ---------------------------------------------------------

    /**
     * Held by {@code FACILITIES_ENERGY_BUDGET_MANAGE}: the energy officer and the director set the numbers
     * the institution's cost and carbon are judged by; there is no separate carbon permission to hold.
     */
    @Transactional
    public EmissionFactor createEmissionFactor(EnergyCommands.CreateEmissionFactor command) {
        if (command.siteCode() == null || command.siteCode().isBlank() || command.utility() == null) {
            throw new FacilitiesException.ValidationFailedException("An emission factor needs a site and a utility.");
        }
        String site = EstateCodes.normalize(command.siteCode());
        authorization.require(command.actor(), SflPermission.FACILITIES_ENERGY_BUDGET_MANAGE, site, command.channel(),
                FACTOR, "new");
        int version = repository.findEmissionFactors(site, command.utility()).stream()
                .mapToInt(EmissionFactor::version).max().orElse(0) + 1;
        EmissionFactor factor = repository.saveEmissionFactor(EmissionFactor.version(UUID.randomUUID(), site,
                command.utility(), version, command.kgCo2ePerUnit(), command.validFrom(), command.sourceReference(),
                command.actor().actorId(), clock.instant(), command.channel(), command.actor().correlationId()));
        audit.record(command.actor(), command.channel(), AuditAction.ENERGY_EMISSION_FACTOR_VERSION_CREATED, FACTOR,
                factor.id().toString(), site, null, factor);
        return factor;
    }

    @Transactional(readOnly = true)
    public List<EmissionFactor> emissionFactors(String siteCode, ActorContext actor, SourceChannel channel) {
        if (siteCode == null || siteCode.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("siteCode is required.");
        }
        String site = EstateCodes.normalize(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, site, channel, FACTOR, "list");
        List<EmissionFactor> factors = new ArrayList<>();
        for (Utility utility : Utility.values()) {
            factors.addAll(repository.findEmissionFactors(site, utility));
        }
        factors.sort(Comparator.comparing(EmissionFactor::utility).thenComparingInt(EmissionFactor::version));
        return factors;
    }

    // ---- computation --------------------------------------------------------------------------

    /**
     * Computes and publishes the KPIs for the current and the previous period of the configured type, over
     * the sites the actor may see; the cluster rollup only for an actor who may see every site.
     *
     * @return the KPIs this run published (new or changed); unchanged rows are not re-announced
     */
    @Transactional
    public List<SustainabilityKpi> computeAndPublish(ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_BUDGET_MANAGE, channel, RESOURCE, "compute", "*");
        authorization.requireAnySiteScope(actor);
        Instant now = clock.instant();
        EnergyPeriod current = EnergyPeriod.containing(configuration.kpiPeriodType(), EnergyPeriod.dayOf(now));
        List<SustainabilityKpi> published = new ArrayList<>();
        // Previous first, so the current period's trend reads the previous period's fresh figure.
        for (EnergyPeriod period : List.of(current.previous(), current)) {
            published.addAll(compute(period, actor, channel, now));
        }
        return published;
    }

    private List<SustainabilityKpi> compute(EnergyPeriod period, ActorContext actor, SourceChannel channel,
            Instant now) {
        BigDecimal minimum = configuration.minimumCompletenessPct();
        boolean closed = period.hasEndedBy(now);
        List<SustainabilityKpi> published = new ArrayList<>();
        Map<Utility, List<Figures>> siteFigures = new EnumMap<>(Utility.class);
        for (Site site : facilities.findSites()) {
            if (!authorization.canAccessSite(actor, site.siteCode())) {
                continue;
            }
            List<EnergyMeter> meters = repository.findMeters(site.siteCode(), null, false);
            List<DailyConsumption> days = repository.findDaily(site.siteCode(), null, period.start(), period.end());
            for (Utility utility : Utility.values()) {
                Optional<EmissionFactor> factor = EmissionFactor.applicable(
                        repository.findEmissionFactors(site.siteCode(), utility), period);
                Figures siteTotal = figures(meters, days, utility, null, period, factor);
                if (siteTotal == null) {
                    continue;
                }
                siteFigures.computeIfAbsent(utility, ignored -> new ArrayList<>()).add(siteTotal);
                publish(KpiScope.SITE, site.siteCode(), null, utility, period, closed, siteTotal, minimum, actor,
                        channel, now).ifPresent(published::add);
                for (String building : meters.stream().filter(meter -> meter.utility() == utility)
                        .map(EnergyMeter::buildingCode).collect(Collectors.toCollection(java.util.TreeSet::new))) {
                    Figures buildingTotal = figures(meters, days, utility, building, period, factor);
                    if (buildingTotal != null) {
                        publish(KpiScope.BUILDING, site.siteCode(), building, utility, period, closed, buildingTotal,
                                minimum, actor, channel, now).ifPresent(published::add);
                    }
                }
            }
        }
        if (authorization.canAccessSite(actor, SustainabilityKpi.CLUSTER_SITE)) {
            for (Map.Entry<Utility, List<Figures>> entry : siteFigures.entrySet()) {
                publish(KpiScope.CLUSTER, SustainabilityKpi.CLUSTER_SITE, null, entry.getKey(), period, closed,
                        Figures.sum(entry.getValue()), minimum, actor, channel, now).ifPresent(published::add);
            }
        }
        return published;
    }

    /** One scope's numbers before they become a KPI. {@code carbon} is null when no factor applied at all. */
    record Figures(BigDecimal consumption, int expected, int received, BigDecimal carbon,
            EmissionFactorStatus factorStatus) {

        static Figures sum(List<Figures> parts) {
            BigDecimal consumption = BigDecimal.ZERO;
            BigDecimal carbon = null;
            int expected = 0;
            int received = 0;
            long applied = 0;
            for (Figures part : parts) {
                consumption = consumption.add(part.consumption());
                expected += part.expected();
                received += part.received();
                if (part.carbon() != null) {
                    carbon = carbon == null ? part.carbon() : carbon.add(part.carbon());
                    applied++;
                }
            }
            EmissionFactorStatus status = applied == 0 ? EmissionFactorStatus.NOT_CONFIGURED
                    : applied == parts.size() ? EmissionFactorStatus.APPLIED : EmissionFactorStatus.PARTIAL;
            return new Figures(consumption, expected, received, carbon, status);
        }
    }

    /** {@code null} when no meter of the utility existed in the scope during the period. */
    private static Figures figures(List<EnergyMeter> meters, List<DailyConsumption> days, Utility utility,
            String building, EnergyPeriod period, Optional<EmissionFactor> factor) {
        List<EnergyMeter> inScope = meters.stream().filter(meter -> meter.utility() == utility)
                .filter(meter -> building == null || building.equals(meter.buildingCode())).toList();
        Map<UUID, Integer> receivedByMeter = new TreeMap<>();
        BigDecimal consumption = BigDecimal.ZERO;
        for (DailyConsumption day : days) {
            if (day.utility() == utility && (building == null || building.equals(day.buildingCode()))) {
                consumption = consumption.add(day.consumption());
                receivedByMeter.merge(day.meterId(), day.readingCount(), Integer::sum);
            }
        }
        int expected = 0;
        int received = 0;
        for (EnergyMeter meter : inScope) {
            int owed = CompletenessPolicy.expected(meter, period);
            expected += owed;
            received += CompletenessPolicy.received(owed, receivedByMeter.getOrDefault(meter.id(), 0));
        }
        if (expected == 0) {
            return null;
        }
        BigDecimal totalConsumption = consumption;
        BigDecimal carbon = factor.map(found -> totalConsumption.multiply(found.kgCo2ePerUnit())
                .setScale(4, RoundingMode.HALF_UP)).orElse(null);
        return new Figures(totalConsumption, expected, received, carbon,
                factor.isPresent() ? EmissionFactorStatus.APPLIED : EmissionFactorStatus.NOT_CONFIGURED);
    }

    private Optional<SustainabilityKpi> publish(KpiScope scope, String site, String building, Utility utility,
            EnergyPeriod period, boolean closed, Figures figures, BigDecimal minimum, ActorContext actor,
            SourceChannel channel, Instant now) {
        String key = SustainabilityKpi.keyOf(scope, site, building, utility, period);
        Optional<SustainabilityKpi> existing = repository.findKpiByKey(key);
        BigDecimal previous = repository.findKpiByKey(SustainabilityKpi.keyOf(scope, site, building, utility,
                period.previous())).map(SustainabilityKpi::consumption).orElse(null);
        BigDecimal trend = previous == null || previous.signum() == 0 ? null
                : figures.consumption().subtract(previous).multiply(HUNDRED).divide(previous, 4, RoundingMode.HALF_UP);
        BigDecimal percent = CompletenessPolicy.percent(figures.expected(), figures.received());
        SustainabilityKpi candidate = new SustainabilityKpi(existing.map(SustainabilityKpi::id).orElse(UUID.randomUUID()),
                key, site, scope, building, utility, period, closed, figures.consumption().setScale(4, RoundingMode.HALF_UP),
                previous, trend, figures.carbon(), figures.factorStatus(), figures.expected(), figures.received(),
                percent, CompletenessPolicy.flag(percent, minimum), minimum,
                existing.map(kpi -> kpi.revision() + 1).orElse(1), now,
                existing.map(kpi -> kpi.metadata().modifiedBy(actor.actorId(), now, channel, actor.correlationId()))
                        .orElse(RecordMetadata.createdBy(actor.actorId(), now, channel, actor.correlationId())));
        if (existing.isPresent() && sameFigures(existing.get(), candidate)) {
            return Optional.empty();
        }
        SustainabilityKpi saved = repository.saveKpi(candidate);
        audit.record(actor, channel, AuditAction.SUSTAINABILITY_KPI_PUBLISHED, RESOURCE, saved.id().toString(), site,
                existing.orElse(null), saved);
        outbox.record(EnergyEvents.KPI_PUBLISHED, 1, RESOURCE, saved.id(), site, actor.correlationId(),
                actor.actorId(), EnergyEvents.KpiPublished.of(saved));
        return Optional.of(saved);
    }

    private static boolean sameFigures(SustainabilityKpi a, SustainabilityKpi b) {
        return a.periodClosed() == b.periodClosed() && a.consumption().compareTo(b.consumption()) == 0
                && a.expectedReadings() == b.expectedReadings() && a.receivedReadings() == b.receivedReadings()
                && a.completenessFlag() == b.completenessFlag() && a.emissionFactorStatus() == b.emissionFactorStatus()
                && compare(a.carbonKgCo2e(), b.carbonKgCo2e()) && compare(a.trendPct(), b.trendPct())
                && a.minimumCompletenessPct().compareTo(b.minimumCompletenessPct()) == 0;
    }

    private static boolean compare(BigDecimal a, BigDecimal b) {
        return Objects.equals(a == null ? null : a.stripTrailingZeros(), b == null ? null : b.stripTrailingZeros());
    }

    // ---- the read model -----------------------------------------------------------------------

    /**
     * S225's read of the model. {@code siteCode = *} asks for the cluster rows, which only a cross-site
     * caller may see; any other site is scope-checked as usual.
     */
    @Transactional(readOnly = true)
    public List<SustainabilityKpi> kpis(EnergyRepository.KpiQuery query, ActorContext actor, SourceChannel channel) {
        String site = query.siteCode() == null || query.siteCode().isBlank() ? null
                : SustainabilityKpi.CLUSTER_SITE.equals(query.siteCode().strip()) ? SustainabilityKpi.CLUSTER_SITE
                        : EstateCodes.normalize(query.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, channel, RESOURCE, "list", site);
        if (SustainabilityKpi.CLUSTER_SITE.equals(site)) {
            authorization.requireSite(actor, site, channel, RESOURCE, "cluster");
        } else {
            authorization.requireRequestedSite(actor, site, channel, RESOURCE);
        }
        LocalDate from = query.from() == null ? LocalDate.of(2000, 1, 1) : query.from();
        LocalDate to = query.to() == null ? LocalDate.of(9999, 1, 1) : query.to();
        return authorization.filterBySite(actor, repository.findKpis(new EnergyRepository.KpiQuery(site,
                query.scope(), query.utility(), query.periodType(), from, to,
                Math.min(Math.max(1, query.limit()), 1000))), SustainabilityKpi::siteCode);
    }
}
