package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes what IFIMP records. The half of the outbox pattern this service never had.
 *
 * <p>S152, S153 and S159 all write to {@code facilities.outbox_messages} inside the business
 * transaction, which is correct and was only ever half the job: nothing drained the table, so the rows
 * accumulated and no event left the service. Three gap reports describe the same consequence in
 * different words — no S153 escalation, no S159 rejection, no-show or readiness hold, and no
 * cross-module saga — and they are one gap with one fix, which is this class.
 *
 * <p><strong>One message, one transaction, claimed with {@code FOR UPDATE SKIP LOCKED}.</strong> The
 * claim and the status write have to be in the same transaction or the lock buys nothing: a row locked
 * by a {@code SELECT ... FOR UPDATE} outside a transaction is released the moment that statement's
 * implicit transaction commits, which is before the message is sent. The transaction is driven through
 * a {@link TransactionTemplate} rather than {@code @Transactional} on a private method, because Spring
 * proxies do not intercept self-invocation — an annotation there would look like a transaction
 * boundary and be nothing of the kind, which is the worst of both.
 *
 * <p>{@code SKIP LOCKED} lets a second instance step over rows the first is holding rather than block
 * behind them, so the drain scales horizontally without leader election. Delivery remains
 * at-least-once — a crash between {@code send} and the commit replays the message — which is why every
 * consumer writes {@code eventId} to {@code inbox_messages} before processing.
 *
 * <p><strong>Backoff, not a tight retry loop.</strong> V5 added {@code attempt_count},
 * {@code next_attempt_at}, {@code last_attempt_at} and {@code dead_lettered_at} with a partial index on
 * {@code next_attempt_at WHERE status = 'PENDING'} — the shape of an exponential backoff waiting for
 * someone to write it. A broker that is down comes back; hammering it every ten seconds meanwhile turns
 * one outage into two. The delay doubles from {@code retry-base} and is capped.
 *
 * <p><strong>Poison after N.</strong> A message that fails {@code max-attempts} times is
 * {@code DEAD_LETTERED} with its reason rather than retried forever, so one malformed payload cannot
 * stop every event queued behind it. A dead-lettered row is visible; a silently skipped one is not.
 */
@Component
@ConditionalOnProperty(name = "sfl.facilities.messaging.drainer-enabled", havingValue = "true",
        matchIfMissing = true)
public class FacilitiesOutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(FacilitiesOutboxDrainer.class);

    private final JdbcTemplate jdbc;
    private final FacilitiesEventTransport transport;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int maxAttempts;
    private final int batchSize;
    private final Duration retryBase;
    private final Duration retryCap;

    FacilitiesOutboxDrainer(JdbcTemplate jdbc, FacilitiesEventTransport transport,
            PlatformTransactionManager transactionManager, Clock clock,
            @Value("${sfl.facilities.messaging.max-attempts:5}") int maxAttempts,
            @Value("${sfl.facilities.messaging.batch-size:100}") int batchSize,
            @Value("${sfl.facilities.messaging.retry-base:PT10S}") Duration retryBase,
            @Value("${sfl.facilities.messaging.retry-cap:PT1H}") Duration retryCap) {
        this.jdbc = jdbc;
        this.transport = transport;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.retryBase = retryBase;
        this.retryCap = retryCap;
    }

    @Scheduled(fixedDelayString = "${sfl.facilities.messaging.drain-delay:PT10S}",
            initialDelayString = "${sfl.facilities.messaging.drain-initial-delay:PT15S}")
    public void drain() {
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
        if (published > 0) {
            log.info("Drained {} IFIMP outbox messages via {}", published, transport.name());
        }
    }

    private enum Outcome { PUBLISHED, FAILED, EMPTY }

    /**
     * Claims, sends and settles exactly one message. A batch in a single transaction would let one
     * poison payload roll back every delivery that preceded it in the same tick.
     */
    private Outcome drainNext() {
        List<OutboxMessage> claimed = jdbc.query("""
                SELECT id, event_type, event_version, schema_version, aggregate_type, aggregate_id,
                       site_scope, correlation_id, causation_id, trace_parent, payload::text AS payload
                  FROM facilities.outbox_messages
                 WHERE status = 'PENDING'
                   AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                 ORDER BY created_at
                 LIMIT 1
                   FOR UPDATE SKIP LOCKED
                """,
                (rs, n) -> new OutboxMessage(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_type"),
                        rs.getInt("event_version"),
                        rs.getInt("schema_version"),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("site_scope"),
                        rs.getString("correlation_id"),
                        rs.getString("causation_id"),
                        rs.getString("trace_parent"),
                        rs.getString("payload")),
                now());

        if (claimed.isEmpty()) {
            return Outcome.EMPTY;
        }
        OutboxMessage message = claimed.get(0);
        try {
            transport.send(message);
            jdbc.update("""
                    UPDATE facilities.outbox_messages
                       SET status = 'PUBLISHED', published_at = ?, last_attempt_at = ?,
                           attempt_count = attempt_count + 1, failure_reason = NULL, next_attempt_at = NULL
                     WHERE id = ?
                    """, now(), now(), message.id());
            return Outcome.PUBLISHED;
        } catch (RuntimeException exception) {
            recordFailure(message, exception);
            return Outcome.FAILED;
        }
    }

    private void recordFailure(OutboxMessage message, RuntimeException exception) {
        Integer previous = jdbc.queryForObject(
                "SELECT attempt_count FROM facilities.outbox_messages WHERE id = ?", Integer.class, message.id());
        int attempts = (previous == null ? 0 : previous) + 1;
        boolean poisoned = attempts >= maxAttempts;
        Instant retryAt = nextAttempt(attempts);

        jdbc.update("""
                UPDATE facilities.outbox_messages
                   SET attempt_count = ?, status = ?, failure_reason = ?, last_attempt_at = ?,
                       next_attempt_at = ?, dead_lettered_at = ?
                 WHERE id = ?
                """,
                attempts,
                poisoned ? "DEAD_LETTERED" : "PENDING",
                truncate(exception.getMessage()),
                now(),
                poisoned ? null : OffsetDateTime.ofInstant(retryAt, ZoneOffset.UTC),
                poisoned ? now() : null,
                message.id());

        if (poisoned) {
            log.error("Outbox message {} ({}) dead-lettered after {} attempts: {}", message.id(),
                    message.eventType(), attempts, exception.getMessage());
        } else {
            log.warn("Outbox delivery for {} ({}) failed on attempt {}, next attempt at {}", message.id(),
                    message.eventType(), attempts, retryAt);
        }
    }

    /** Doubles from the base and stops at the cap, so a long outage does not schedule a retry next year. */
    private Instant nextAttempt(int attempts) {
        Duration delay = retryBase.multipliedBy(1L << Math.min(attempts - 1, 16));
        return clock.instant().plus(delay.compareTo(retryCap) > 0 ? retryCap : delay);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** {@code failure_reason} is VARCHAR(2000); a driver stack trace will exceed it. */
    private static String truncate(String reason) {
        if (reason == null) {
            return "No failure message";
        }
        return reason.length() <= 2000 ? reason : reason.substring(0, 2000);
    }
}
