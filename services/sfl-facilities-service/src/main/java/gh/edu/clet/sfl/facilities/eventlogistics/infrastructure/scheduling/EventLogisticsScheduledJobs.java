package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventReadinessService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventResourceRequestService;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two S173 sweeps, on a timer - thin, like {@code BookingScheduledJobs}: a system actor, a service
 * call, a log line. Every decision lives in the services.
 *
 * <h2>Why the escalation sweep runs every five minutes</h2>
 *
 * <p>SRS-SFL-S173-04's acceptance criterion is that the coordinator is notified "before the event, not
 * after". The window opens 48 hours before the start by default, so five minutes of latency costs
 * nothing; what it guards against is a window configured short (an hour, for a same-day event), where a
 * slow sweep could let the start pass first. The synchronisation sweep is the safety net under the S159
 * observer and the only way S153 completion and S169 fulfilment reach S173; ten minutes is plenty.
 *
 * <p>Both are idempotent: an escalation is recorded once per request, and a synchronisation that
 * finds nothing changed writes nothing.
 */
@Component
@ConditionalOnProperty(name = "sfl.event-logistics.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class EventLogisticsScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(EventLogisticsScheduledJobs.class);

    /** A service account with {@code *} scope: a sweep is estate-wide and cannot ask a person for sites. */
    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal("system.event-logistics-scheduler",
            "Event logistics scheduler", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final EventReadinessService readiness;
    private final EventResourceRequestService requests;

    public EventLogisticsScheduledJobs(EventReadinessService readiness, EventResourceRequestService requests) {
        this.readiness = readiness;
        this.requests = requests;
    }

    /** SRS-SFL-S173-04: escalate unresolved requests whose event's escalation window has opened. */
    @Scheduled(fixedDelayString = "${sfl.event-logistics.escalation.interval-ms:300000}",
            initialDelayString = "${sfl.event-logistics.escalation.initial-delay-ms:150000}")
    public void sweepEscalations() {
        try {
            EventReadinessService.EscalationSweep sweep = readiness.sweepEscalations(actor());
            if (sweep.escalated() > 0) {
                log.info("Event readiness sweep escalated {} request(s) across {} event(s) at {}", sweep.escalated(),
                        sweep.tasksExamined(), sweep.evaluatedAt());
            }
        } catch (RuntimeException failure) {
            // Swallowed on purpose: an uncaught exception cancels a fixedDelay schedule for the life of the
            // process, and a silently dead escalation sweep is the failure S173-04 exists to prevent.
            log.error("Event readiness escalation sweep failed; it will be retried on the next run", failure);
        }
    }

    /** Pulls owning-system state (S159, S153, S169) into S173's requests. */
    @Scheduled(fixedDelayString = "${sfl.event-logistics.synchronise.interval-ms:600000}",
            initialDelayString = "${sfl.event-logistics.synchronise.initial-delay-ms:180000}")
    public void synchronise() {
        try {
            int changed = requests.synchronise(actor());
            if (changed > 0) {
                log.info("Event logistics synchronisation updated {} resource request(s)", changed);
            }
        } catch (RuntimeException failure) {
            log.error("Event logistics synchronisation failed; it will be retried on the next run", failure);
        }
    }

    private static ActorContext actor() {
        return new ActorContext(SYSTEM, UUID.randomUUID().toString());
    }
}
