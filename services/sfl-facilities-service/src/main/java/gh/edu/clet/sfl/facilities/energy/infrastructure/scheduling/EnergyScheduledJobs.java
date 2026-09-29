package gh.edu.clet.sfl.facilities.energy.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.energy.application.EnergyReadingService;
import gh.edu.clet.sfl.facilities.energy.application.EnergyVarianceService;
import gh.edu.clet.sfl.facilities.energy.application.SustainabilityKpiService;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The four S157 sweeps. Thin, like {@code BookingScheduledJobs}: build a system actor, call the service,
 * log what moved. Every decision is in the services, where it is testable without a clock and a thread.
 *
 * <ul>
 *   <li><strong>Period close</strong> (hourly) - closes ended months and raises variance and missing-tariff
 *       alerts (S157-02). Hourly because the alert is due "when the period closes"; an hour's latency on the
 *       first of the month is the whole delay.</li>
 *   <li><strong>Anomaly</strong> (hourly) - judges yesterday for every AMI and stream meter. Idempotent on
 *       meter and day, so running hourly costs a query and raises once.</li>
 *   <li><strong>KPI publication</strong> (hourly) - S157-03's "computed on a configured schedule"; only new
 *       or changed KPIs are published.</li>
 *   <li><strong>Retention</strong> (daily) - SRS 4.2.</li>
 * </ul>
 *
 * <p>All are idempotent, so two instances sweeping at once waste queries rather than double-alert; the
 * alert and result unique keys are the backstop. Scheduler threads run under the platform RLS scope.
 */
@Component
@ConditionalOnProperty(name = "sfl.energy.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class EnergyScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(EnergyScheduledJobs.class);

    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal(
            "system.energy", "Energy scheduler", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final EnergyVarianceService variance;
    private final SustainabilityKpiService kpis;
    private final EnergyReadingService readings;

    public EnergyScheduledJobs(EnergyVarianceService variance, SustainabilityKpiService kpis,
            EnergyReadingService readings) {
        this.variance = variance;
        this.kpis = kpis;
        this.readings = readings;
    }

    @Scheduled(fixedDelayString = "${sfl.energy.close.interval-ms:3600000}",
            initialDelayString = "${sfl.energy.close.initial-delay-ms:300000}")
    public void sweepPeriodCloses() {
        try {
            int closed = variance.sweepCloses(actor());
            if (closed > 0) {
                log.info("Energy close sweep closed {} site/utility period(s)", closed);
            }
        } catch (RuntimeException failure) {
            // Swallowed on purpose: an uncaught exception cancels a fixedDelay schedule for the life of the
            // process, and a close sweep that silently stopped is a variance alert that never comes.
            log.error("Energy close sweep failed; it will be retried on the next run", failure);
        }
    }

    @Scheduled(fixedDelayString = "${sfl.energy.anomaly.interval-ms:3600000}",
            initialDelayString = "${sfl.energy.anomaly.initial-delay-ms:360000}")
    public void sweepAnomalies() {
        try {
            int raised = variance.evaluateAnomalies(null, null, actor(), SourceChannel.SCHEDULER).size();
            if (raised > 0) {
                log.info("Energy anomaly sweep raised {} anomaly flag(s)", raised);
            }
        } catch (RuntimeException failure) {
            log.error("Energy anomaly sweep failed; it will be retried on the next run", failure);
        }
    }

    @Scheduled(fixedDelayString = "${sfl.energy.kpi.interval-ms:3600000}",
            initialDelayString = "${sfl.energy.kpi.initial-delay-ms:420000}")
    public void publishKpis() {
        try {
            int published = kpis.computeAndPublish(actor(), SourceChannel.SCHEDULER).size();
            if (published > 0) {
                log.info("Sustainability KPI sweep published {} new or changed KPI(s)", published);
            }
        } catch (RuntimeException failure) {
            log.error("Sustainability KPI sweep failed; it will be retried on the next run", failure);
        }
    }

    @Scheduled(fixedDelayString = "${sfl.energy.retention.interval-ms:86400000}",
            initialDelayString = "${sfl.energy.retention.initial-delay-ms:900000}")
    public void purgeExpiredReadings() {
        try {
            int purged = readings.purgeExpired(actor());
            if (purged > 0) {
                log.info("Energy retention sweep purged {} reading(s)", purged);
            }
        } catch (RuntimeException failure) {
            log.error("Energy retention sweep failed; it will be retried on the next run", failure);
        }
    }

    private static ActorContext actor() {
        return new ActorContext(SYSTEM, UUID.randomUUID().toString());
    }
}
