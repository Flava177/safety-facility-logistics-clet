package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The emergency outbox drainer, against a real PostgreSQL, now that it has a real transport to hand
 * messages to instead of a no-op that always "succeeded".
 *
 * <p>Covers what the audit finding was specifically about - a message is marked {@code PUBLISHED} only
 * once the transport confirms it, a failing transport retries and eventually dead-letters without
 * blocking the rest of the batch, and a message already marked {@code PUBLISHED} is never handed to the
 * transport again on a later drain (the producer-side half of duplicate-delivery safety; the consumer
 * side is an inbox-dedup concern for whichever service eventually subscribes to {@code ssemp.#}, none
 * of which exists yet for this event catalogue - see the accompanying report).
 */
@EnabledIf(value = "gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see SafetySecurityPostgresSupport.unavailableReason()")
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        // Off, so the scheduled tick cannot race the assertions; the test drives drain() itself.
        "sfl.emergency.messaging.drainer-enabled=false"
})
class OutboxDrainerTest extends SafetySecurityPostgresSupport {

    /** A transport the test can break on demand, and that records what it was asked to send. */
    static final class RecordingTransport implements EmergencyEventTransport {
        final List<EmergencyOutboxMessage> sent = new ArrayList<>();
        final AtomicBoolean failing = new AtomicBoolean(false);

        @Override
        public void send(EmergencyOutboxMessage message) {
            if (failing.get()) {
                throw new IllegalStateException("broker unavailable");
            }
            sent.add(message);
        }

        @Override
        public String name() {
            return "recording";
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    private RecordingTransport transport;
    private OutboxDrainer drainer;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport();
        jdbc.update("DELETE FROM emergency_notification.outbox_messages WHERE aggregate_type = 'DrainerTest'");
        drainer = new OutboxDrainer(jdbc, transport, clock, 3);
    }

    @Test
    void a_pending_message_is_sent_and_marked_published_only_once_the_transport_confirms_it() {
        UUID id = insertPending("sfl.ssemp.emergency-notification-activated.v1");

        drainer.drain();

        assertThat(transport.sent).extracting(EmergencyOutboxMessage::id).contains(id);
        assertThat(transport.sent).filteredOn(m -> m.id().equals(id)).singleElement().satisfies(m -> {
            assertThat(m.eventType()).isEqualTo("sfl.ssemp.emergency-notification-activated.v1");
            // The payload must arrive as text: it is stored as jsonb, and the `payload::text` cast in
            // the claim query is the only reason that works.
            assertThat(m.payload()).contains("\"probe\"");
            assertThat(m.siteScope()).isEqualTo("E2E-SITE");
        });

        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("failure_reason")).isNull();
    }

    @Test
    void a_published_message_is_never_handed_to_the_transport_again() {
        UUID id = insertPending("sfl.ssemp.emergency-drill-completed.v1");
        drainer.drain();
        assertThat(row(id).get("status")).isEqualTo("PUBLISHED");
        transport.sent.clear();

        drainer.drain();

        assertThat(transport.sent).extracting(EmergencyOutboxMessage::id).doesNotContain(id);
    }

    @Test
    void a_failing_delivery_retries_then_dead_letters_without_blocking_the_rest_of_the_batch() {
        UUID poison = insertPending("sfl.ssemp.emergency-activation-submitted.v1");
        transport.failing.set(true);

        drainer.drain();
        Map<String, Object> afterFirst = row(poison);
        assertThat(afterFirst.get("status")).isEqualTo("PENDING");
        assertThat(((Number) afterFirst.get("attempt_count")).intValue()).isEqualTo(1);
        assertThat(afterFirst.get("failure_reason")).isEqualTo("broker unavailable");

        drainer.drain();
        drainer.drain();

        Map<String, Object> afterThird = row(poison);
        assertThat(afterThird.get("status")).isEqualTo("DEAD_LETTERED");
        assertThat(((Number) afterThird.get("attempt_count")).intValue()).isEqualTo(3);

        // The queue behind it still moves: one bad payload must not hold up delivery of everything
        // queued in the same batch.
        transport.failing.set(false);
        UUID healthy = insertPending("sfl.ssemp.emergency-all-clear-sent.v1");
        drainer.drain();

        assertThat(transport.sent).extracting(EmergencyOutboxMessage::id).contains(healthy).doesNotContain(poison);
        assertThat(row(healthy).get("status")).isEqualTo("PUBLISHED");
    }

    /**
     * Backdated to 2000, not the real clock: the shared e2e database carries hundreds of PENDING rows
     * from earlier test runs that ran with the drainer disabled (this class's own {@code setUp} does
     * the same), and {@code drain()}'s query is {@code ORDER BY created_at LIMIT 100} - a row inserted
     * "now" would sort behind that whole backlog and never make it into the batch this test drains.
     * Sorting first guarantees this row is claimed regardless of how large the backlog has grown.
     */
    private UUID insertPending(String eventType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO emergency_notification.outbox_messages
                    (id, event_type, event_version, aggregate_type, aggregate_id, site_scope,
                     correlation_id, causation_id, payload, status, created_at, attempt_count)
                VALUES (?, ?, 1, 'DrainerTest', ?, 'E2E-SITE', ?, ?, ?::jsonb, 'PENDING', ?, 0)
                """,
                id, eventType, id.toString(), "corr-" + id, "cause-" + id,
                "{\"probe\":\"" + id + "\"}",
                OffsetDateTime.ofInstant(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC));
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM emergency_notification.outbox_messages WHERE id = ?", id);
    }
}
