package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code OutboxDrainer} against a real PostgreSQL.
 *
 * <p>Was a single {@code @Transactional} method that claimed a whole batch, sent every message
 * synchronously, then {@code saveAll}'d the batch once at the end. A poison payload or a crash mid-loop
 * would roll back the transaction and revert every already-sent message in that batch back to
 * {@code PENDING} - resending events a consumer already received. This pins the one-message-per-transaction
 * fix: a failure partway through a batch must not touch messages that already succeeded.
 */
@EnabledIf(value = "gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FleetPostgresSupport.unavailableReason()")
@SpringBootTest(properties = {"sfl.security.enabled=false", "sfl.fuel.scheduling.enabled=false",
        "sfl.fleet.scheduling.outbox.enabled=false", "sfl.fleet.messaging.transport=local"})
class OutboxDrainerTest extends FleetPostgresSupport {

    /** A transport the test can break for specific messages, and that records what it was asked to send. */
    static final class RecordingTransport implements FleetEventTransport {
        final List<UUID> sent = new ArrayList<>();
        final java.util.Set<UUID> failing = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final AtomicBoolean failAll = new AtomicBoolean(false);

        @Override
        public void send(OutboxMessageEntity message) {
            if (failAll.get() || failing.contains(message.id())) {
                throw new IllegalStateException("broker unavailable");
            }
            sent.add(message.id());
        }

        @Override
        public String name() {
            return "recording";
        }
    }

    /** Small, fixed attempt/backoff so tests do not depend on the compiled-in default of 8 attempts. */
    static final class FixedRuntimeConfiguration implements RuntimeConfigurationPort {
        @Override
        public Duration complianceExpiryWarningWindow(String siteCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration inspectionValidityWindow(String siteCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration serviceDueWarningWindow(String siteCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration odometerStalenessThreshold(String siteCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration telematicsStalenessThreshold(String siteCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration dashboardFreshnessThreshold(String siteCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration integrationSignatureWindow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration outboundRetryBackoff(int attempt) {
            return Duration.ofSeconds(30);
        }

        @Override
        public int outboundMaxAttempts() {
            return 3;
        }

        @Override
        public Optional<String> value(String key, String siteCode) {
            return Optional.empty();
        }

        @Override
        public Instant activeConfigurationChangedAt() {
            return Instant.EPOCH;
        }
    }

    @TestConfiguration
    static class Beans {
        @Bean
        @Primary
        RecordingTransport recordingTransport() {
            return new RecordingTransport();
        }

        @Bean
        @Primary
        RuntimeConfigurationPort fixedRuntimeConfiguration() {
            return new FixedRuntimeConfiguration();
        }
    }

    @Autowired private OutboxMessageRepository repository;
    @Autowired private RecordingTransport transport;
    @Autowired private RuntimeConfigurationPort runtimeConfiguration;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbc;

    private OutboxDrainer drainer;

    /**
     * {@code drainOnce()} claims every currently-due {@code PENDING} row in the table, not just the
     * ones a given test inserted - so a row left {@code PENDING} by one test (the backoff and
     * still-failing-poison tests both do this deliberately) is still claimable by the next test's
     * drainer against this same real database, inflating its {@code published} count. Mirrors
     * {@code FacilitiesOutboxDrainerTest}'s identical cleanup for the identical reason.
     */
    @BeforeEach
    void cleanSlate() {
        jdbc.update("DELETE FROM fleet_logistics.outbox_messages WHERE aggregate_type = 'OutboxDrainerTest'");
    }

    @AfterEach
    void tearDown() {
        transport.sent.clear();
        transport.failing.clear();
        transport.failAll.set(false);
    }

    private OutboxDrainer drainer(int batchSize) {
        return new OutboxDrainer(repository, transport, runtimeConfiguration, transactionManager, clock, batchSize);
    }

    /**
     * Monotonically decreasing, so every message this test class inserts sorts before anything a
     * concurrently-running e2e test in the same suite writes with a real "now" timestamp -
     * {@code claimDue} orders {@code createdAt asc LIMIT batchSize}, and this class is not the only
     * thing in the reactor that writes to this table. Confirmed against a real, freshly seeded
     * Postgres database (not a per-CI-run empty one): other e2e tests in this module leave their own
     * PENDING outbox rows behind, and with more than {@code batchSize} of those already queued ahead
     * of a message stamped with the real clock, this class's own message never entered the claimed
     * batch at all. Backdating removes the dependency on how much of that exists at any given moment.
     */
    private static final AtomicLong INSERT_SEQUENCE = new AtomicLong(0);

    private UUID insertPending(String eventType) {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.EPOCH.plusSeconds(INSERT_SEQUENCE.incrementAndGet());
        OutboxMessageEntity entity = new OutboxMessageEntity(id, eventType, 1, "OutboxDrainerTest", id.toString(),
                "MAIN", "corr-" + id, "cause-" + id, "actor-1", null, 1, "{\"probe\":\"" + id + "\"}", createdAt);
        repository.save(entity);
        return id;
    }

    @Test
    void a_pending_message_is_sent_and_marked_published() {
        UUID id = insertPending("sfl.ftlmp.vehicle-created.v1");

        // Not asserting drainOnce()'s exact return count: this runs against the shared e2e database
        // (see FacilitiesOutboxDrainerTest's identical note), which can carry other due rows from
        // other tests in the same run. Draining those too is correct behaviour for a batch drainer -
        // what this test actually owns is proving its own message.
        drainer(10).drainOnce();

        assertThat(transport.sent).contains(id);
        OutboxMessageEntity row = repository.findById(id).orElseThrow();
        assertThat(row.status()).isEqualTo(OutboxMessageEntity.STATUS_PUBLISHED);
        assertThat(row.publishedAt()).isNotNull();
        assertThat(row.failureReason()).isNull();
    }

    @Test
    void a_failed_delivery_is_retried_with_backoff_rather_than_immediately() {
        UUID id = insertPending("sfl.ftlmp.vehicle-fault-reported.v1");
        transport.failing.add(id);

        drainer(10).drainOnce();

        OutboxMessageEntity row = repository.findById(id).orElseThrow();
        assertThat(row.status()).isEqualTo(OutboxMessageEntity.STATUS_PENDING);
        assertThat(row.attemptCount()).isEqualTo(1);
        assertThat(row.failureReason()).contains("broker unavailable");

        // A second tick inside the 30s backoff window must not pick it up again.
        drainer(10).drainOnce();
        assertThat(repository.findById(id).orElseThrow().attemptCount()).isEqualTo(1);
    }

    @Test
    void a_message_that_keeps_failing_is_dead_lettered_after_max_attempts() {
        UUID poison = insertPending("sfl.ftlmp.dispatch-booking-requested.v1");
        transport.failing.add(poison);

        for (int attempt = 0; attempt < 3; attempt++) {
            repository.findById(poison).ifPresent(entity -> {
                entity.requeue(clock.instant());
                repository.save(entity);
            });
            drainer(10).drainOnce();
        }

        OutboxMessageEntity row = repository.findById(poison).orElseThrow();
        assertThat(row.status()).isEqualTo(OutboxMessageEntity.STATUS_DEAD_LETTERED);
        assertThat(row.attemptCount()).isEqualTo(3);
    }

    @Test
    void a_poison_message_earlier_in_the_batch_does_not_roll_back_messages_already_delivered_after_it() {
        UUID poison = insertPending("sfl.ftlmp.vehicle-created.v1");
        UUID healthyOne = insertPending("sfl.ftlmp.vehicle-created.v1");
        UUID healthyTwo = insertPending("sfl.ftlmp.vehicle-created.v1");
        transport.failing.add(poison);

        drainer(10).drainOnce();

        // The poison message failed but must not have taken the other two down with it - proof that
        // each message settles in its own transaction rather than one shared batch transaction. Not
        // asserting drainOnce()'s exact return count here either, for the same shared-database reason
        // as the test above.
        assertThat(transport.sent).contains(healthyOne, healthyTwo).doesNotContain(poison);
        assertThat(repository.findById(healthyOne).orElseThrow().status())
                .isEqualTo(OutboxMessageEntity.STATUS_PUBLISHED);
        assertThat(repository.findById(healthyTwo).orElseThrow().status())
                .isEqualTo(OutboxMessageEntity.STATUS_PUBLISHED);
        assertThat(repository.findById(poison).orElseThrow().status())
                .isEqualTo(OutboxMessageEntity.STATUS_PENDING);
    }
}
