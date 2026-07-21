package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.scheduling;

import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging.OutboxDrainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link OutboxDrainer} on a fixed delay.
 *
 * <p>Split from the drainer so tests can call {@code drainOnce()} deterministically without a scheduler
 * running underneath them, and so the schedule can be disabled per environment.
 */
@Component
@ConditionalOnProperty(name = "sfl.fleet.scheduling.outbox.enabled", havingValue = "true", matchIfMissing = true)
class OutboxDrainScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainScheduler.class);

    private final OutboxDrainer drainer;

    OutboxDrainScheduler(OutboxDrainer drainer) {
        this.drainer = drainer;
    }

    @Scheduled(fixedDelayString = "${sfl.fleet.scheduling.outbox.fixed-delay:PT5S}",
            initialDelayString = "${sfl.fleet.scheduling.outbox.initial-delay:PT10S}")
    void drain() {
        try {
            int published = drainer.drainOnce();
            if (published > 0) {
                log.debug("Published {} fleet integration events", published);
            }
        } catch (RuntimeException exception) {
            // A drain failure must not kill the schedule; the next pass retries the same batch.
            log.error("Fleet outbox drain pass failed", exception);
        }
    }
}
