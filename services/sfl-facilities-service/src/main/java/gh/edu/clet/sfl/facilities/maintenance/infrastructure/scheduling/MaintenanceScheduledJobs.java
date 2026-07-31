package gh.edu.clet.sfl.facilities.maintenance.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.maintenance.application.EvidenceDisposalService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceEscalationService;
import gh.edu.clet.sfl.facilities.maintenance.application.PreventiveMaintenanceService;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two S153 sweeps, on a timer.
 *
 * <p>SRS-SFL-S153-02 says escalation happens "when the scheduled evaluation runs", which means
 * something has to run it. This is that something, and it is deliberately thin: it builds a system
 * actor, calls the application service, and logs what moved. Every decision — what is overdue, what
 * level it is owed, whether a schedule has already generated — is in the services, where it is
 * testable without a clock and a thread.
 *
 * <h2>Two things worth knowing before changing the intervals</h2>
 *
 * <p><strong>Both sweeps are idempotent</strong>, so the interval is a latency choice rather than a
 * correctness one. Running escalation every fifteen minutes rather than every five means an item can
 * sit fifteen minutes past its threshold before anybody is told; it does not mean it might be missed.
 *
 * <p><strong>Neither is safe to run on more than one instance at once</strong> in the sense of being
 * wasteful, not wrong: two instances sweeping together will both read the same overdue rows, and the
 * second will find every level already applied and do nothing. It is idempotence rather than a lock,
 * which is the right trade for two jobs that run this rarely — a distributed lock would be a second
 * thing to operate for a saving of a few wasted queries. Recorded because it is the kind of thing
 * somebody adds a lock for without asking whether it is needed.
 */
@Component
public class MaintenanceScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduledJobs.class);

    /**
     * The actor escalations and generated work are recorded against.
     *
     * <p>A service account with {@code *} scope, because a sweep is estate-wide by nature and cannot
     * ask a person for their sites. {@code serviceAccount} is true so an audit reader can tell this
     * from a person who happens to have every site — a distinction that matters when the question is
     * "who escalated this at three in the morning?"
     */
    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal(
            "system.maintenance", "Maintenance scheduler", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final PreventiveMaintenanceService preventive;
    private final MaintenanceEscalationService escalation;
    private final EvidenceDisposalService disposal;
    private final Clock clock;
    private final boolean enabled;

    public MaintenanceScheduledJobs(PreventiveMaintenanceService preventive,
            MaintenanceEscalationService escalation, EvidenceDisposalService disposal, Clock clock,
            @Value("${sfl.maintenance.scheduling.enabled:true}") boolean enabled) {
        this.preventive = preventive;
        this.escalation = escalation;
        this.disposal = disposal;
        this.clock = clock;
        this.enabled = enabled;
    }

    /**
     * Escalates everything past its SLA.
     *
     * <p>Every fifteen minutes. The tightest SLA in the default policy is thirty minutes, so a quarter
     * of that is fine-grained enough that nothing sits long unnoticed, and coarse enough that the
     * query is not running constantly against a table that changes slowly.
     */
    @Scheduled(fixedDelayString = "${sfl.maintenance.escalation.interval-ms:900000}",
            initialDelayString = "${sfl.maintenance.escalation.initial-delay-ms:60000}")
    public void sweepEscalations() {
        if (!enabled) {
            return;
        }
        try {
            MaintenanceEscalationService.EscalationSweep sweep = escalation.sweep(systemActor());
            if (sweep.total() > 0) {
                log.info("SLA sweep escalated {} fault(s) and {} work order(s) at {}",
                        sweep.faultsEscalated(), sweep.workOrdersEscalated(), sweep.evaluatedAt());
            }
        } catch (RuntimeException failure) {
            // Swallowed on purpose. An uncaught exception from a fixedDelay task cancels the schedule
            // for the life of the process, so one bad row would silently stop every future escalation
            // — the failure mode being least likely to be noticed, on the job whose whole purpose is
            // to notice things.
            log.error("SLA escalation sweep failed; it will be retried on the next run", failure);
        }
    }

    /**
     * Disposes of evidence whose retention has run out.
     *
     * <p>Daily, and deliberately not more often. Retention is measured in years, so nothing is gained
     * by checking hourly — and this is the one job in the module that destroys something, so its blast
     * radius per run should be as small as the requirement allows.
     */
    @Scheduled(cron = "${sfl.maintenance.disposal.cron:0 30 2 * * *}")
    public void sweepEvidenceDisposal() {
        if (!enabled) {
            return;
        }
        try {
            int disposed = disposal.sweep(systemActor());
            if (disposed > 0) {
                log.info("Retention sweep disposed of {} evidence reference(s)", disposed);
            }
        } catch (RuntimeException failure) {
            // Swallowed for the same reason as the escalation sweep: an uncaught exception from a
            // scheduled task cancels the schedule for the life of the process. A retention sweep that
            // silently stopped running would be discovered by a regulator rather than by us.
            log.error("Evidence disposal sweep failed; it will be retried on the next run", failure);
        }
    }

    /**
     * Raises the preventive work due today.
     *
     * <p>Hourly rather than daily. A daily job has one chance to run, so a deploy or an outage across
     * its window silently skips a day of preventive maintenance; hourly it catches up by itself, and
     * the twenty-three runs that find nothing cost one indexed query each.
     */
    @Scheduled(fixedDelayString = "${sfl.maintenance.preventive.interval-ms:3600000}",
            initialDelayString = "${sfl.maintenance.preventive.initial-delay-ms:120000}")
    public void generatePreventiveWork() {
        if (!enabled) {
            return;
        }
        try {
            LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
            List<WorkOrder> raised = preventive.generateDueWorkOrders(systemActor(), today);
            if (!raised.isEmpty()) {
                log.info("Preventive generation raised {} work order(s) for {}", raised.size(), today);
            }
        } catch (RuntimeException failure) {
            log.error("Preventive generation failed; it will be retried on the next run", failure);
        }
    }

    /** A fresh correlation id per run, so one sweep's audit records can be read together. */
    private ActorContext systemActor() {
        return new ActorContext(SYSTEM, UUID.randomUUID().toString());
    }
}
