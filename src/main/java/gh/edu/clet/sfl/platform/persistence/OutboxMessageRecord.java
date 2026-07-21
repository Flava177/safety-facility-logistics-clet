package gh.edu.clet.sfl.platform.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "outbox_messages", schema = "messaging")
public class OutboxMessageRecord {

    @Id
    private UUID id;
    @Column(name = "event_type", nullable = false, length = 180)
    private String eventType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    protected OutboxMessageRecord() {
    }

    public OutboxMessageRecord(UUID id, String eventType, UUID aggregateId, String correlationId,
            String payload, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.correlationId = correlationId;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = createdAt;
    }
}
