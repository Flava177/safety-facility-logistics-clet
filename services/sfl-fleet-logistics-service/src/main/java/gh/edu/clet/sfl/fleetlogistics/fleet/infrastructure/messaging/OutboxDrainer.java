package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes pending outbox messages to the configured transport (SRS-SFL-S166-04: "Failed integration
 * deliveries shall be retried and surfaced on an integration-health dashboard").
 *
 * <p>Each pass claims a batch with {@code FOR UPDATE SKIP LOCKED}, so several service instances can
 * drain concurrently. A failed delivery is rescheduled with exponential backoff read from the runtime
 * configuration at that moment; after the configured attempt limit the message is dead-lettered and
 * stays visible on the integration-health projection for manual replay.
 */
@Component
public class OutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainer.class);

    private final OutboxMessageRepository outboxMessages;
    private final FleetEventTransport transport;
    private final RuntimeConfigurationPort runtimeConfiguration;
    private final Clock clock;
    private final int batchSize;

    OutboxDrainer(OutboxMessageRepository outboxMessages, FleetEventTransport transport,
            RuntimeConfigurationPort runtimeConfiguration, Clock clock,
            @org.springframework.beans.factory.annotation.Value("${sfl.fleet.messaging.drain-batch-size:50}")
            int batchSize) {
        this.outboxMessages = outboxMessages;
        this.transport = transport;
        this.runtimeConfiguration = runtimeConfiguration;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * Drains one batch.
     *
     * @return the number of messages published in this pass
     */
    @Transactional
    public int drainOnce() {
        Instant now = clock.instant();
        List<OutboxMessageEntity> due = outboxMessages.claimDue(now, PageRequest.of(0, batchSize));
        if (due.isEmpty()) {
            return 0;
        }

        int maxAttempts = runtimeConfiguration.outboundMaxAttempts();
        int published = 0;
        for (OutboxMessageEntity message : due) {
            try {
                transport.send(message);
                message.markPublished(now);
                published++;
            } catch (RuntimeException exception) {
                int attemptsSoFar = message.attemptCount() + 1;
                if (attemptsSoFar >= maxAttempts) {
                    message.markDeadLettered(now, describe(exception));
                    log.error("Fleet outbox message {} ({}) dead-lettered after {} attempts",
                            message.id(), message.eventType(), attemptsSoFar, exception);
                } else {
                    Instant nextAttempt = now.plus(runtimeConfiguration.outboundRetryBackoff(attemptsSoFar));
                    message.markFailed(now, nextAttempt, describe(exception));
                    log.warn("Fleet outbox message {} ({}) failed on attempt {}; retrying at {}",
                            message.id(), message.eventType(), attemptsSoFar, nextAttempt, exception);
                }
            }
        }
        outboxMessages.saveAll(due);
        return published;
    }

    /** Returns a dead-lettered message to the pending queue. Used by the privileged replay endpoint. */
    @Transactional
    public boolean requeue(java.util.UUID messageId) {
        return outboxMessages.findById(messageId)
                .filter(message -> OutboxMessageEntity.STATUS_DEAD_LETTERED.equals(message.status()))
                .map(message -> {
                    message.requeue(clock.instant());
                    outboxMessages.save(message);
                    return true;
                })
                .orElse(false);
    }

    public String transportName() {
        return transport.name();
    }

    private static String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }
}
