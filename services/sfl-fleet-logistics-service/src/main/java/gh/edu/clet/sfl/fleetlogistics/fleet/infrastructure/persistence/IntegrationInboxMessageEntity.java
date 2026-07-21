package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationInboxMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA image of an inbound integration inbox message. */
@Entity
@Table(name = "fleet_integration_inbox_messages", schema = "fleet_logistics")
class IntegrationInboxMessageEntity {

    @Id
    private UUID id;

    @Column(name = "source_system", nullable = false, length = 80)
    private String sourceSystem;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationMessageStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected IntegrationInboxMessageEntity() {
    }

    static IntegrationInboxMessageEntity from(IntegrationInboxMessage message) {
        IntegrationInboxMessageEntity entity = new IntegrationInboxMessageEntity();
        entity.id = message.id();
        entity.applyFrom(message);
        return entity;
    }

    void applyFrom(IntegrationInboxMessage message) {
        this.sourceSystem = message.sourceSystem();
        this.idempotencyKey = message.idempotencyKey();
        this.eventType = message.eventType();
        this.siteCode = message.siteCode().value();
        this.correlationId = message.correlationId();
        this.occurredAt = message.occurredAt();
        this.payloadHash = message.payloadHash();
        this.rawPayload = message.rawPayload();
        this.status = message.status();
        this.attempts = message.attempts();
        this.failureReason = message.failureReason();
        this.receivedAt = message.receivedAt();
        this.processedAt = message.processedAt();
    }

    IntegrationInboxMessage toDomain() {
        return new IntegrationInboxMessage(id, sourceSystem, idempotencyKey, eventType, SiteCode.of(siteCode),
                correlationId, occurredAt, payloadHash, rawPayload, status, attempts, failureReason,
                receivedAt, processedAt);
    }
}
