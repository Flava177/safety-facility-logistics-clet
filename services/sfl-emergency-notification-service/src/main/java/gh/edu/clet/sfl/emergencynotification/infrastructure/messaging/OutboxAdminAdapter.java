package gh.edu.clet.sfl.emergencynotification.infrastructure.messaging;

import gh.edu.clet.sfl.emergencynotification.application.port.OutboxAdminPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Integration-health and privileged dead-letter replay over the transactional outbox. */
@Component
public class OutboxAdminAdapter implements OutboxAdminPort {

    private final JdbcTemplate jdbc;

    public OutboxAdminAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OutboxHealth health() {
        long pending = countByStatus("PENDING");
        long published = countByStatus("PUBLISHED");
        long deadLettered = countByStatus("DEAD_LETTERED");
        List<OutboxEntry> recent = jdbc.query("""
                SELECT id,event_type,aggregate_type,aggregate_id,status,attempt_count,failure_reason,created_at
                FROM emergency_notification.outbox_messages WHERE status='DEAD_LETTERED'
                ORDER BY created_at DESC LIMIT 20
                """, (rs, n) -> new OutboxEntry((UUID) rs.getObject("id"), rs.getString("event_type"),
                rs.getString("aggregate_type"), rs.getString("aggregate_id"), rs.getString("status"),
                rs.getInt("attempt_count"), rs.getString("failure_reason"),
                rs.getTimestamp("created_at").toInstant()));
        return new OutboxHealth(pending, published, deadLettered, recent);
    }

    @Override
    public boolean replay(UUID messageId) {
        int updated = jdbc.update("""
                UPDATE emergency_notification.outbox_messages SET status='PENDING', attempt_count=0, failure_reason=NULL
                WHERE id=? AND status='DEAD_LETTERED'
                """, messageId);
        return updated > 0;
    }

    private long countByStatus(String status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM emergency_notification.outbox_messages WHERE status=?", Long.class, status);
        return count == null ? 0L : count;
    }
}
