package gh.edu.clet.sfl.platform.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "audit_events", schema = "platform")
public class AuditEventRecord {

    @Id
    private UUID id;
    @Column(name = "event_type", nullable = false, length = 180)
    private String eventType;
    @Column(name = "aggregate_type", nullable = false, length = 120)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "actor_id", nullable = false, length = 160)
    private String actorId;
    @Column(nullable = false, length = 120)
    private String source;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String details;

    protected AuditEventRecord() {
    }

    public AuditEventRecord(UUID id, String eventType, String aggregateType, UUID aggregateId,
            String actorId, String source, String correlationId, Instant occurredAt, String details) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.actorId = actorId;
        this.source = source;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.details = details;
    }
}
