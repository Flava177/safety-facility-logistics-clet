package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * A fleet integration event awaiting delivery.
 *
 * <p>Written in the same transaction as the business change (SRS-SFL-S166-04 at-least-once delivery
 * safety) and drained by {@code OutboxDrainer}. Delivery state — attempts, next attempt, dead-letter —
 * lives on the row so a restart never loses the retry schedule.
 */
@Entity
@Table(name = "outbox_messages", schema = "fleet_logistics")
public class OutboxMessageEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DEAD_LETTERED = "DEAD_LETTERED";

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 180)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "aggregate_type", nullable = false, length = 120)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(name = "site_scope", length = 80)
    private String siteScope;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "causation_id", length = 120)
    private String causationId;

    @Column(name = "actor_id", length = 160)
    private String actorId;

    @Column(name = "trace_parent", length = 120)
    private String traceParent;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    protected OutboxMessageEntity() {
    }

    public OutboxMessageEntity(UUID id, String eventType, int eventVersion, String aggregateType, String aggregateId,
            String siteScope, String correlationId, String causationId, String actorId, String traceParent,
            int schemaVersion, String payload, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.siteScope = siteScope;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.actorId = actorId;
        this.traceParent = traceParent;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.status = STATUS_PENDING;
        this.createdAt = createdAt;
        this.attemptCount = 0;
        this.nextAttemptAt = createdAt;
    }

    public void markPublished(Instant now) {
        this.status = STATUS_PUBLISHED;
        this.publishedAt = now;
        this.lastAttemptAt = now;
        this.attemptCount = this.attemptCount + 1;
        this.nextAttemptAt = null;
        this.failureReason = null;
    }

    /** Records a failed attempt and schedules the next one. */
    public void markFailed(Instant now, Instant nextAttempt, String reason) {
        this.attemptCount = this.attemptCount + 1;
        this.lastAttemptAt = now;
        this.nextAttemptAt = nextAttempt;
        this.failureReason = truncate(reason);
    }

    /** Gives up on the message; it stays visible for the integration-health dashboard and manual replay. */
    public void markDeadLettered(Instant now, String reason) {
        this.attemptCount = this.attemptCount + 1;
        this.status = STATUS_DEAD_LETTERED;
        this.lastAttemptAt = now;
        this.deadLetteredAt = now;
        this.nextAttemptAt = null;
        this.failureReason = truncate(reason);
    }

    /** Returns a dead-lettered message to the pending queue for a manual replay. */
    public void requeue(Instant now) {
        this.status = STATUS_PENDING;
        this.deadLetteredAt = null;
        this.nextAttemptAt = now;
        this.failureReason = null;
    }

    public UUID id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public int eventVersion() {
        return eventVersion;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String siteScope() {
        return siteScope;
    }

    public String correlationId() {
        return correlationId;
    }

    public String causationId() {
        return causationId;
    }

    public String actorId() {
        return actorId;
    }

    public String traceParent() {
        return traceParent;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String payload() {
        return payload;
    }

    public String status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public String failureReason() {
        return failureReason;
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 2000 ? reason : reason.substring(0, 2000);
    }
}
