package gh.edu.clet.sfl.emergencynotification.infrastructure.messaging;

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
 * Drains the transactional outbox at-least-once via the configured transport. Phase-1 {@code local}
 * transport records delivery (no broker); a delivery that throws is retried and dead-lettered after
 * {@code sfl.emergency.messaging.max-attempts}. It never fakes vendor success — a failed sink dead-letters.
 */
@Component
@ConditionalOnProperty(name = "sfl.emergency.messaging.drainer-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainer.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int maxAttempts;

    public OutboxDrainer(JdbcTemplate jdbc, Clock clock,
            @Value("${sfl.emergency.messaging.max-attempts:5}") int maxAttempts) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${sfl.emergency.messaging.drain-delay:PT10S}",
            initialDelayString = "${sfl.emergency.messaging.drain-initial-delay:PT15S}")
    public void drain() {
        List<UUID> pending = jdbc.query("""
                SELECT id FROM emergency_notification.outbox_messages WHERE status='PENDING'
                ORDER BY created_at LIMIT 100
                """, (rs, n) -> (UUID) rs.getObject("id"));
        for (UUID id : pending) {
            try {
                deliver(id);
                jdbc.update("UPDATE emergency_notification.outbox_messages SET status='PUBLISHED', published_at=? "
                        + "WHERE id=?", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), id);
            } catch (RuntimeException e) {
                int attempts = jdbc.queryForObject(
                        "SELECT attempt_count FROM emergency_notification.outbox_messages WHERE id=?", Integer.class, id)
                        + 1;
                String status = attempts >= maxAttempts ? "DEAD_LETTERED" : "PENDING";
                jdbc.update("UPDATE emergency_notification.outbox_messages SET attempt_count=?, status=?, "
                        + "failure_reason=? WHERE id=?", attempts, status, e.getMessage(), id);
                log.warn("Outbox delivery for {} failed (attempt {}), status={}", id, attempts, status);
            }
        }
    }

    /** Phase-1 recorded local delivery. A real broker/consumer replaces this without a domain change. */
    private void deliver(UUID id) {
        log.debug("Recorded local delivery of outbox message {}", id);
    }
}
