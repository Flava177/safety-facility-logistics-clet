package gh.edu.clet.sfl.facilities.construction.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.construction.application.ContractorComplianceService;
import gh.edu.clet.sfl.facilities.construction.application.HandoverService;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two S176 sweeps, on a timer. Thin by design, like {@code BookingScheduledJobs}: build a system
 * actor, call the service, log what moved.
 *
 * <ul>
 *   <li><strong>Compliance suspension</strong> (hourly by default) - SRS-SFL-S176-02: every active
 *       site-access grant of a contractor whose insurance or competency has expired is suspended,
 *       without anybody checking. An expiry date lapses at midnight UTC; the first run after that
 *       suspends. An hour of latency against a date-granular expiry is the trade: tighter costs a
 *       query per contractor per run for no change in outcome on most days.</li>
 *   <li><strong>Defect sync</strong> (every fifteen minutes) - SRS-SFL-S176-04: reads S153 for each open
 *       defects-liability item and closes it when its work order is closed, so the snagging backlog on
 *       the dashboard does not wait for somebody to try to close the project.</li>
 * </ul>
 *
 * <p>Both are idempotent. Both run on the platform scheduler thread, whose RLS scope is {@code *}.
 */
@Component
@ConditionalOnProperty(name = "sfl.construction.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ConstructionScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ConstructionScheduledJobs.class);

    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal(
            "system.construction", "Construction scheduler", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final ContractorComplianceService compliance;
    private final HandoverService handover;

    public ConstructionScheduledJobs(ContractorComplianceService compliance, HandoverService handover) {
        this.compliance = compliance;
        this.handover = handover;
    }

    @Scheduled(fixedDelayString = "${sfl.construction.compliance.interval-ms:3600000}",
            initialDelayString = "${sfl.construction.compliance.initial-delay-ms:150000}")
    public void suspendLapsedAccess() {
        try {
            ContractorComplianceService.SuspensionSweep sweep = compliance.suspendLapsedGrants(actor());
            if (sweep.suspended() > 0) {
                log.warn("Construction compliance sweep suspended {} of {} active contractor site-access grant(s) at {}"
                        + " - recorded, not enforced at the door", sweep.suspended(), sweep.examined(),
                        sweep.evaluatedAt());
            }
        } catch (RuntimeException failure) {
            // Swallowed on purpose: an uncaught exception cancels a fixedDelay schedule for the life of
            // the process, and this is the job whose whole purpose is not needing a person to notice.
            log.error("Construction compliance sweep failed; it will be retried on the next run", failure);
        }
    }

    @Scheduled(fixedDelayString = "${sfl.construction.defects.interval-ms:900000}",
            initialDelayString = "${sfl.construction.defects.initial-delay-ms:180000}")
    public void syncDefects() {
        try {
            HandoverService.DefectSync sync = handover.syncDefects(actor());
            if (sync.closed() > 0) {
                log.info("Construction defect sync closed {} of {} open defects-liability item(s) at {}",
                        sync.closed(), sync.examined(), sync.evaluatedAt());
            }
        } catch (RuntimeException failure) {
            log.error("Construction defect sync failed; it will be retried on the next run", failure);
        }
    }

    private ActorContext actor() {
        return new ActorContext(SYSTEM, UUID.randomUUID().toString());
    }
}
