package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import java.time.Clock;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The drainer, against a real PostgreSQL.
 *
 * <p>Against a real database on purpose: {@code FOR UPDATE SKIP LOCKED}, the {@code payload::text}
 * cast off a {@code jsonb} column and the partial index on {@code next_attempt_at} are all things an
 * in-memory double would happily pretend to do. The S152 pass lost a day to exactly that class of
 * difference, and this is the mechanism that carries every IFIMP event off the service.
 */
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        // Off, so the scheduled tick cannot race the assertions; the test drives drain() itself.
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.facilities.messaging.max-attempts=3",
        "sfl.facilities.messaging.retry-base=PT30S"
})
class FacilitiesOutboxDrainerTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    /** A transport the test can break on demand, and that records what it was asked to send. */
    static final class RecordingTransport implements FacilitiesEventTransport {
        final List<OutboxMessage> sent = new ArrayList<>();
        final AtomicBoolean failing = new AtomicBoolean(false);

        @Override
        public void send(OutboxMessage message) {
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

    @TestConfiguration
    static class Transports {
        @Bean
        @Primary
        RecordingTransport recordingTransport() {
            return new RecordingTransport();
        }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingTransport transport;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private FacilitiesOutboxDrainer drainer;

    @BeforeEach
    void setUp() {
        transport.sent.clear();
        transport.failing.set(false);
        jdbc.update("DELETE FROM facilities.outbox_messages WHERE aggregate_type = 'DrainerTest'");
        drainer = new FacilitiesOutboxDrainer(jdbc, transport, transactionManager, clock,
                3, 100, java.time.Duration.ofSeconds(30), java.time.Duration.ofHours(1));
    }

    @Test
    void a_pending_message_is_sent_and_marked_published() {
        UUID id = insertPending("sfl.ifimp.work-order-created.v1");

        drainer.drain();

        // `contains`, not `containsExactly`: this runs against the shared e2e database, which carries
        // pending rows from earlier hand-driven verification. Draining those too is correct behaviour —
        // asserting the queue was empty first would be asserting something about the fixture.
        assertThat(transport.sent).extracting(OutboxMessage::id).contains(id);
        assertThat(transport.sent).filteredOn(message -> message.id().equals(id)).singleElement()
                .satisfies(message -> {
                    assertThat(message.eventType()).isEqualTo("sfl.ifimp.work-order-created.v1");
                    // The payload must arrive as text: it is stored as jsonb, and the `payload::text`
                    // cast in the claim query is the only reason that works.
                    assertThat(message.payload()).contains("\"probe\"");
                    assertThat(message.siteScope()).isEqualTo("E2E-SITE");
                });

        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("failure_reason")).isNull();
        assertThat(row.get("next_attempt_at")).isNull();
    }

    @Test
    void a_failed_delivery_is_retried_with_backoff_rather_than_immediately() {
        UUID id = insertPending("sfl.ifimp.facility-fault-reported.v1");
        transport.failing.set(true);

        drainer.drain();

        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(1);
        assertThat(row.get("failure_reason")).isEqualTo("broker unavailable");
        assertThat(row.get("next_attempt_at")).isNotNull();

        // A second tick inside the backoff window must not pick it up again — that is the whole point of
        // the window. Hammering a broker that is down turns one outage into two.
        drainer.drain();
        assertThat(((Number) row(id).get("attempt_count")).intValue()).isEqualTo(1);
    }

    @Test
    void a_message_that_keeps_failing_is_dead_lettered_and_stops_blocking_the_queue() {
        UUID poison = insertPending("sfl.ifimp.booking-requested.v1");
        transport.failing.set(true);

        // max-attempts is 3. Clear the backoff between ticks so the test drives attempts, not the clock.
        for (int attempt = 0; attempt < 3; attempt++) {
            jdbc.update("UPDATE facilities.outbox_messages SET next_attempt_at = NULL WHERE id = ?", poison);
            drainer.drain();
        }

        Map<String, Object> row = row(poison);
        assertThat(row.get("status")).isEqualTo("DEAD_LETTERED");
        assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(3);
        assertThat(row.get("dead_lettered_at")).isNotNull();
        assertThat(row.get("next_attempt_at")).isNull();

        // And the queue behind it moves: one bad payload must not hold up every event queued after it.
        transport.failing.set(false);
        UUID healthy = insertPending("sfl.ifimp.room-readiness-changed.v1");
        drainer.drain();

        assertThat(transport.sent).extracting(OutboxMessage::id).contains(healthy).doesNotContain(poison);
        assertThat(row(healthy).get("status")).isEqualTo("PUBLISHED");
    }

    private UUID insertPending(String eventType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO facilities.outbox_messages
                    (id, event_type, event_version, aggregate_type, aggregate_id, site_scope,
                     correlation_id, causation_id, payload, status, created_at, attempt_count, schema_version)
                VALUES (?, ?, 1, 'DrainerTest', ?, 'E2E-SITE', ?, ?, ?::jsonb, 'PENDING', ?, 0, 1)
                """,
                id, eventType, id.toString(), "corr-" + id, "cause-" + id,
                "{\"probe\":\"" + id + "\"}", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM facilities.outbox_messages WHERE id = ?", id);
    }
}
