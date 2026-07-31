package gh.edu.clet.sfl.facilities.booking.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.booking.application.BookingReconciliationService;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two S159 sweeps, on a timer.
 *
 * <p>Thin by design, exactly like {@code MaintenanceScheduledJobs}: build a system actor, call the
 * application service, log what moved. Every decision about what is overdue or held lives in the
 * services, where it is testable without a clock and a thread.
 *
 * <h2>Why the no-show sweep runs more often than the readiness one</h2>
 *
 * They are answering different questions. A readiness hold is advisory — it flags a booking a human
 * will look at — so fifteen minutes of latency costs nothing. A no-show <em>releases a space</em>,
 * and every minute between the grace expiring and the sweep noticing is a minute the hall is unusable
 * by anybody else. Five minutes against a twenty-minute default grace means the room comes back
 * within a quarter of the time it was held for nothing.
 *
 * <p>Both are idempotent, so the intervals are latency choices rather than correctness ones, and two
 * instances sweeping at once waste a query rather than double-count anybody.
 */
@Component
public class BookingScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduledJobs.class);

    /**
     * The actor holds and no-shows are recorded against.
     *
     * <p>A service account with {@code *} scope, because a sweep is estate-wide by nature and cannot
     * ask a person for their sites. {@code serviceAccount} is true so an audit reader can tell this
     * from a person who happens to have every site.
     */
    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal(
            "system.booking", "Booking scheduler", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final BookingReconciliationService reconciliation;
    private final boolean enabled;

    public BookingScheduledJobs(BookingReconciliationService reconciliation,
            @Value("${sfl.booking.scheduling.enabled:true}") boolean enabled) {
        this.reconciliation = reconciliation;
        this.enabled = enabled;
    }

    /** Places and clears readiness holds across every live booking. */
    @Scheduled(fixedDelayString = "${sfl.booking.readiness.interval-ms:900000}",
            initialDelayString = "${sfl.booking.readiness.initial-delay-ms:90000}")
    public void sweepReadinessHolds() {
        if (!enabled) {
            return;
        }
        try {
            BookingReconciliationService.ReadinessSweep sweep = reconciliation.sweepReadinessHolds(actor());
            if (sweep.total() > 0) {
                log.info("Booking readiness sweep placed {} hold(s) and cleared {} across {} booking(s) at {}",
                        sweep.holdsPlaced(), sweep.holdsCleared(), sweep.examined(), sweep.evaluatedAt());
            }
        } catch (RuntimeException failure) {
            // Swallowed on purpose. An uncaught exception from a fixedDelay task cancels the schedule
            // for the life of the process, so one bad row would silently stop every future sweep — the
            // failure mode least likely to be noticed, on the job whose whole purpose is to notice.
            log.error("Booking readiness sweep failed; it will be retried on the next run", failure);
        }
    }

    /** Marks bookings nobody took up and releases what they were holding. */
    @Scheduled(fixedDelayString = "${sfl.booking.no-show.interval-ms:300000}",
            initialDelayString = "${sfl.booking.no-show.initial-delay-ms:120000}")
    public void sweepNoShows() {
        if (!enabled) {
            return;
        }
        try {
            BookingReconciliationService.NoShowSweep sweep = reconciliation.sweepNoShows(actor());
            if (sweep.recorded() > 0) {
                log.info("Booking no-show sweep recorded {} of {} candidate(s) at {}", sweep.recorded(),
                        sweep.examined(), sweep.evaluatedAt());
            }
        } catch (RuntimeException failure) {
            log.error("Booking no-show sweep failed; it will be retried on the next run", failure);
        }
    }

    /** A fresh correlation id per run, so one sweep's audit records can be read together. */
    private ActorContext actor() {
        return new ActorContext(SYSTEM, UUID.randomUUID().toString());
    }
}
