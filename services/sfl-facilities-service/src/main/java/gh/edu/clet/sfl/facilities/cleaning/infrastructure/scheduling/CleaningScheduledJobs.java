package gh.edu.clet.sfl.facilities.cleaning.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningScheduleService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningVendorService;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two S169 sweeps, on a timer. Thin by design, like {@code BookingScheduledJobs}: build the system
 * actor, call the service, log what moved.
 *
 * <ul>
 *   <li><strong>Routine generation</strong> ({@code sfl.cleaning.generation.interval-ms}, default one
 *       hour) - materialises routine tasks to the configured horizon. Idempotent, so the interval only
 *       decides how soon a new schedule or room appears in the rota.</li>
 *   <li><strong>SLA response</strong> ({@code sfl.cleaning.sla.interval-ms}, default five minutes) -
 *       records a response breach on an unattended reactive vendor request as soon as its contracted
 *       response time passes, rather than when somebody eventually starts it.</li>
 * </ul>
 *
 * <p>Both run on platform threads, whose row-level security scope is {@code *} (the foundation's
 * {@code PlatformThreads}), because a sweep is estate-wide. Both swallow and log failures: an uncaught
 * exception from a fixed-delay task cancels the schedule for the life of the process.
 */
@Component
@ConditionalOnProperty(name = "sfl.cleaning.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CleaningScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(CleaningScheduledJobs.class);

    /** A service account with {@code *} scope; {@code serviceAccount} lets an audit reader tell it from a person. */
    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal("system.cleaning", "Cleaning scheduler",
            Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final CleaningScheduleService schedules;
    private final CleaningVendorService vendors;

    public CleaningScheduledJobs(CleaningScheduleService schedules, CleaningVendorService vendors) {
        this.schedules = schedules;
        this.vendors = vendors;
    }

    @Scheduled(fixedDelayString = "${sfl.cleaning.generation.interval-ms:3600000}",
            initialDelayString = "${sfl.cleaning.generation.initial-delay-ms:180000}")
    public void generateRoutineTasks() {
        try {
            CleaningScheduleService.GenerationResult result = schedules.generate(null, actor(), SourceChannel.SCHEDULER);
            if (result.tasksCreated() > 0) {
                log.info("Cleaning generation created {} task(s) across {} schedule(s) to {} ({} already present)",
                        result.tasksCreated(), result.schedulesExamined(), result.to(), result.alreadyPresent());
            }
        } catch (RuntimeException failure) {
            log.error("Cleaning generation sweep failed; it will be retried on the next run", failure);
        }
    }

    @Scheduled(fixedDelayString = "${sfl.cleaning.sla.interval-ms:300000}",
            initialDelayString = "${sfl.cleaning.sla.initial-delay-ms:240000}")
    public void evaluateResponseSla() {
        try {
            CleaningVendorService.SlaSweep sweep = vendors.sweepResponse(actor());
            if (sweep.breachesRecorded() > 0) {
                log.info("Cleaning SLA sweep recorded {} response breach(es) across {} unattended request(s)",
                        sweep.breachesRecorded(), sweep.examined());
            }
        } catch (RuntimeException failure) {
            log.error("Cleaning SLA sweep failed; it will be retried on the next run", failure);
        }
    }

    private static ActorContext actor() {
        return new ActorContext(SYSTEM, UUID.randomUUID().toString());
    }
}
