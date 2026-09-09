package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the transactional outbox at-least-once via the configured {@link EmergencyEventTransport}.
 * The {@code local} transport (default) records delivery without a broker; a delivery that throws is
 * retried and dead-lettered after {@code sfl.emergency.messaging.max-attempts}. It never fakes vendor
 * success - a failed send dead-letters, and a message is marked {@code PUBLISHED} only after
 * {@link EmergencyEventTransport#send} returns without throwing, which for the {@code rabbitmq}
 * transport means only after the broker has acknowledged the publish.
 *
 * <p>One failing message does not block the rest of the batch: each is claimed, sent and settled
 * independently within the loop below, so a poison payload dead-letters on its own schedule without
 * holding up delivery of everything queued behind it.
 */
@Component
@ConditionalOnProperty(name = "sfl.emergency.messaging.drainer-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainer.class);

    private final JdbcTemplate jdbc;
    private final EmergencyEventTransport transport;
    private final Clock clock;
    private final int maxAttempts;

    public OutboxDrainer(JdbcTemplate jdbc, EmergencyEventTransport transport, Clock clock,
            @Value("${sfl.emergency.messaging.max-attempts:5}") int maxAttempts) {
        this.jdbc = jdbc;
        this.transport = transport;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${sfl.emergency.messaging.drain-delay:PT10S}",
            initialDelayString = "${sfl.emergency.messaging.drain-initial-delay:PT15S}")
    public void drain() {
        List<EmergencyOutboxMessage> pending = jdbc.query("""
                SELECT id, event_type, event_version, aggregate_type, aggregate_id, site_scope,
                       correlation_id, causation_id, payload::text AS payload
                  FROM emergency_notification.outbox_messages
                 WHERE status='PENDING'
                 ORDER BY created_at LIMIT 100
                """,
                (rs, n) -> new EmergencyOutboxMessage(
                        (UUID) rs.getObject("id"),
                        rs.getString("event_type"),
                        rs.getInt("event_version"),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("site_scope"),
                        rs.getString("correlation_id"),
                        rs.getString("causation_id"),
                        rs.getString("payload")));
        int published = 0;
        for (EmergencyOutboxMessage message : pending) {
            try {
                transport.send(message);
                jdbc.update("UPDATE emergency_notification.outbox_messages SET status='PUBLISHED', published_at=? "
                        + "WHERE id=?", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), message.id());
                published++;
            } catch (RuntimeException e) {
                int attempts = jdbc.queryForObject(
                        "SELECT attempt_count FROM emergency_notification.outbox_messages WHERE id=?", Integer.class,
                        message.id()) + 1;
                String status = attempts >= maxAttempts ? "DEAD_LETTERED" : "PENDING";
                jdbc.update("UPDATE emergency_notification.outbox_messages SET attempt_count=?, status=?, "
                        + "failure_reason=? WHERE id=?", attempts, status, e.getMessage(), message.id());
                if ("DEAD_LETTERED".equals(status)) {
                    log.error("Outbox message {} ({}) dead-lettered after {} attempts via {}: {}", message.id(),
                            message.eventType(), attempts, transport.name(), e.getMessage());
                } else {
                    log.warn("Outbox delivery for {} ({}) failed via {} on attempt {}: {}", message.id(),
                            message.eventType(), transport.name(), attempts, e.getMessage());
                }
            }
        }
        if (published > 0) {
            log.info("Drained {} emergency outbox messages via {}", published, transport.name());
        }
    }
}
