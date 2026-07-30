package gh.edu.clet.sfl.emergencynotification.infrastructure.integration;

import gh.edu.clet.sfl.emergencynotification.application.port.InboxAdminPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read access to the secure integration inbox, over the same table
 * {@code EmergencyIntegrationInbox} writes to before any domain effect.
 *
 * <p>Closes gap 3. Every count and every row here already existed — the table has been written on
 * every provider callback since the service was built — and nothing read it back, which is why an
 * activation with 480 sent and 0 delivered could not be diagnosed.
 */
@Component
public class InboxAdminAdapter implements InboxAdminPort {

    private static final String TABLE = "emergency_notification.integration_inbox_messages";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InboxAdminAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public InboxHealth health(int recentLimit) {
        // Bounded here rather than trusted from the caller: this is a health panel, and a request
        // for ten thousand envelopes is a mistake rather than a requirement.
        int limit = Math.max(1, Math.min(recentLimit <= 0 ? 20 : recentLimit, 100));
        List<InboxMessage> recent = jdbc.query(
                "SELECT id,source_system,event_type,site_scope,status,attempts,failure_reason,idempotency_key,"
                        + "received_at,processed_at FROM " + TABLE + " ORDER BY received_at DESC, id DESC LIMIT ?",
                this::message, limit);
        return new InboxHealth(countByStatus("PROCESSED"), countByStatus("REJECTED"), countByStatus("DEAD_LETTER"),
                recent, clock.instant());
    }

    private InboxMessage message(ResultSet rs, int row) throws SQLException {
        return new InboxMessage(rs.getString("id"), rs.getString("source_system"), rs.getString("event_type"),
                rs.getString("site_scope"), rs.getString("status"), rs.getInt("attempts"),
                rs.getString("failure_reason"), rs.getString("idempotency_key"), instant(rs, "received_at"),
                instant(rs, "processed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private long countByStatus(String status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE + " WHERE status=?", Long.class, status);
        return count == null ? 0L : count;
    }
}
