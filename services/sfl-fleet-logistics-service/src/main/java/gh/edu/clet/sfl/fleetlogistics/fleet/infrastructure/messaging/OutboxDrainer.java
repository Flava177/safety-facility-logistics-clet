package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes pending outbox messages to the configured transport (SRS-SFL-S166-04: "Failed integration
 * deliveries shall be retried and surfaced on an integration-health dashboard").
 *
 * <p><strong>One message, one transaction.</strong> Claiming and sending an entire batch inside one
 * transaction would let a poison payload or a crash mid-loop roll back every delivery that preceded it
 * in the same tick - the already-sent messages would revert to {@code PENDING} and be resent on the next
 * pass, even though the broker (and possibly a downstream consumer) already has them. Each message is
 * instead claimed, sent and settled in its own transaction via {@link TransactionTemplate} (an
 * {@code @Transactional} private method would not be intercepted by the Spring proxy on self-invocation),
 * mirroring {@code FacilitiesOutboxDrainer}. {@code FOR UPDATE SKIP LOCKED} still lets several service
 * instances drain concurrently without blocking on each other's claimed rows. A failed delivery is
 * rescheduled with exponential backoff read from the runtime configuration at that moment; after the
 * configured attempt limit the message is dead-lettered and stays visible on the integration-health
 * projection for manual replay.
 */
@Component
public class OutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainer.class);

    private final OutboxMessageRepository outboxMessages;
    private final FleetEventTransport transport;
    private final RuntimeConfigurationPort runtimeConfiguration;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int batchSize;

    OutboxDrainer(OutboxMessageRepository outboxMessages, FleetEventTransport transport,
            RuntimeConfigurationPort runtimeConfiguration, PlatformTransactionManager transactionManager,
            Clock clock, @Value("${sfl.fleet.messaging.drain-batch-size:50}") int batchSize) {
        this.outboxMessages = outboxMessages;
        this.transport = transport;
        this.runtimeConfiguration = runtimeConfiguration;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.batchSize = batchSize;
    }

    private enum Outcome { PUBLISHED, FAILED, EMPTY }

    /**
     * Drains up to {@code batchSize} messages, each in its own transaction.
     *
     * @return the number of messages published in this pass
     */
    public int drainOnce() {
        int published = 0;
        for (int processed = 0; processed < batchSize; processed++) {
            Outcome outcome = transactions.execute(status -> drainNext());
            if (outcome == Outcome.EMPTY) {
                break;
            }
            if (outcome == Outcome.PUBLISHED) {
                published++;
            }
        }
        return published;
    }

    private Outcome drainNext() {
        Instant now = clock.instant();
        List<OutboxMessageEntity> claimed = outboxMessages.claimDue(now, PageRequest.of(0, 1));
        if (claimed.isEmpty()) {
            return Outcome.EMPTY;
        }
        OutboxMessageEntity message = claimed.get(0);
        int maxAttempts = runtimeConfiguration.outboundMaxAttempts();
        try {
            transport.send(message);
            message.markPublished(now);
            outboxMessages.save(message);
            return Outcome.PUBLISHED;
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
            outboxMessages.save(message);
            return Outcome.FAILED;
        }
    }

    /** Returns a dead-lettered message to the pending queue. Used by the privileged replay endpoint. */
    @Transactional
    public boolean requeue(UUID messageId) {
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
