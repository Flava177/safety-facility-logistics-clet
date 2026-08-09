package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The idempotent consumer, backed by {@code facilities.inbox_messages}.
 *
 * <p>Outbox plus a broker is <strong>at-least-once</strong>, never exactly-once - the guide says so in
 * 0K and it is not a limitation anyone can engineer away. A message will be delivered twice: the
 * drainer publishes, the broker acknowledges, the drainer dies before it records the publish, and it
 * publishes again on restart. So every consumer has to be safe to run twice, and the cheapest way to
 * be safe is to remember what has already been seen.
 *
 * <p>The claim is the insert itself. {@code message_id} is the primary key, so two concurrent
 * deliveries of the same message race on the database and exactly one wins - no read-then-write, no
 * window between checking and acting. The loser gets a {@link DuplicateKeyException} and stops.
 *
 * <h2>Two things worth knowing about this table</h2>
 *
 * <p><strong>The key is the message, not the message and the consumer.</strong> A second consumer in
 * this service subscribing to the same event would find it already claimed and skip it. That is fine
 * while IFIMP has one handler and wrong the moment it has two; the fix is a composite key, and it is
 * better recorded here than discovered by a handler that silently never runs.
 *
 * <p><strong>The claim commits with the handler's work.</strong> {@code REQUIRED} rather than a new
 * transaction, deliberately: if the handler throws, the claim rolls back with it and the redelivery
 * is processed rather than swallowed. Claiming in a separate committed transaction would mean a
 * handler that failed halfway had consumed its own message.
 */
@Component
public class IntegrationInbox {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public IntegrationInbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Claims a message for a consumer.
     *
     * @return true when this delivery is the first and the caller should process it; false when it has
     *     already been handled and the caller should acknowledge and do nothing.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean claim(UUID messageId, String consumerName, String eventType, String correlationId) {
        try {
            jdbc.update("""
                    INSERT INTO facilities.inbox_messages
                        (message_id, consumer_name, event_type, processed_at, correlation_id)
                    VALUES (?, ?, ?, ?, ?)
                    """, messageId, consumerName, eventType, java.sql.Timestamp.from(clock.instant()),
                    correlationId);
            return true;
        } catch (DuplicateKeyException alreadySeen) {
            return false;
        }
    }
}
