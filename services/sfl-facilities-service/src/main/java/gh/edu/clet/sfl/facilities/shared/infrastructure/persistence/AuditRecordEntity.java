package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditEvent;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA projection of an {@link AuditEvent}. The table carries an append-only trigger (V5). */
@Entity
@Table(name = "facility_audit_records", schema = "facilities")
class AuditRecordEntity {

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
    /**
     * The before and after payloads, stored as text rather than jsonb.
     *
     * <p>jsonb normalises: it reorders object keys and drops insignificant whitespace, so what comes
     * back is not what went in. These fields are inputs to the record hash, so a normalising column
     * type would make every record replay as tampered — which is exactly what happened the first time
     * this ran against PostgreSQL. See V5 for the same note beside the column definition.
     */
    @Column(name = "before_value")
    private String beforeValue;
    @Column(name = "after_value")
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

    static AuditRecordEntity from(AuditEvent event) {
        AuditRecordEntity entity = new AuditRecordEntity();
        entity.id = event.id();
        entity.sequenceNo = event.sequenceNo();
        entity.siteScope = event.siteScope();
        entity.actorId = event.actorId();
        entity.actorDisplayName = event.actorDisplayName();
        entity.action = event.action();
        entity.resourceType = event.resourceType();
        entity.resourceId = event.resourceId();
        entity.beforeValue = event.beforeValue();
        entity.afterValue = event.afterValue();
        entity.correlationId = event.correlationId();
        entity.sourceChannel = event.sourceChannel();
        entity.occurredAt = event.occurredAt();
        entity.previousHash = event.previousHash();
        entity.recordHash = event.recordHash();
        return entity;
    }

    AuditEvent toDomain() {
        return new AuditEvent(id, sequenceNo, siteScope, actorId, actorDisplayName, action, resourceType,
                resourceId, beforeValue, afterValue, correlationId, sourceChannel, occurredAt, previousHash,
                recordHash);
    }
}
