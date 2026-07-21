package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Persistence image of an audit record. The table is append-only in the database as well as in code —
 * a trigger rejects UPDATE and DELETE — so this entity is only ever inserted.
 */
@Entity
@Table(name = "fleet_audit_records", schema = "fleet_logistics")
public class AuditRecordEntity {

    @Id
    private UUID id;

    @Column(name = "sequence_no", nullable = false, unique = true)
    private long sequenceNo;

    @Column(name = "site_scope", nullable = false, length = 40)
    private String siteScope;

    @Column(name = "actor_id", nullable = false, length = 160)
    private String actorId;

    @Column(name = "actor_display_name", nullable = false, length = 200)
    private String actorDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false, length = 80)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 160)
    private String resourceId;

    @Column(name = "before_value", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String afterValue;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 40)
    private SourceChannel sourceChannel;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "record_hash", nullable = false, length = 64)
    private String recordHash;

    protected AuditRecordEntity() {
    }

    private AuditRecordEntity(AuditEvent event) {
        this.id = event.id();
        this.sequenceNo = event.sequenceNo();
        this.siteScope = event.siteScope().value();
        this.actorId = event.actorId();
        this.actorDisplayName = event.actorDisplayName();
        this.action = event.action();
        this.resourceType = event.resourceType();
        this.resourceId = event.resourceId();
        this.beforeValue = event.beforeValue();
        this.afterValue = event.afterValue();
        this.correlationId = event.correlationId();
        this.sourceChannel = event.sourceChannel();
        this.occurredAt = event.occurredAt();
        this.previousHash = event.previousHash();
        this.recordHash = event.recordHash();
    }

    public static AuditRecordEntity from(AuditEvent event) {
        return new AuditRecordEntity(event);
    }

    public AuditEvent toDomain() {
        return new AuditEvent(id, sequenceNo, SiteCode.of(siteScope), actorId, actorDisplayName, action,
                resourceType, resourceId, beforeValue, afterValue, correlationId, sourceChannel, occurredAt,
                previousHash, recordHash);
    }

    public long sequenceNo() {
        return sequenceNo;
    }

    public String recordHash() {
        return recordHash;
    }
}
