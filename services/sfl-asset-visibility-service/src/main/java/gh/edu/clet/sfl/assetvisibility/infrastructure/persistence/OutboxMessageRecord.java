package gh.edu.clet.sfl.assetvisibility.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "outbox_messages", schema = "asset_visibility")
public class OutboxMessageRecord {

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

    protected OutboxMessageRecord() {
    }

    public OutboxMessageRecord(UUID id, String eventType, int eventVersion, String aggregateType,
            UUID aggregateId, String siteScope, String correlationId, String causationId,
            String payload, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId.toString();
        this.siteScope = siteScope;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = createdAt;
    }
}