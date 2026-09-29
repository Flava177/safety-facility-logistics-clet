package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row of {@code facilities.vendor_inbox_messages} - see V15. Append-only; never updated. */
@Entity
@Table(name = "vendor_inbox_messages", schema = "facilities")
class VendorInboxMessageEntity {

    @Id
    private UUID id;
    @Column(name = "source_system", nullable = false, length = 80)
    private String sourceSystem;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VendorChannel channel;
    @Column(name = "message_type", length = 120)
    private String messageType;
    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;
    @Column(name = "site_code", length = 40)
    private String siteCode;
    @Column(nullable = false, length = 20)
    private String outcome;
    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 60)
    private VendorRejectionReason rejectionReason;
    @Column(name = "rejection_detail", length = 1000)
    private String rejectionDetail;
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;
    @Column(name = "signed_at")
    private Instant signedAt;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected VendorInboxMessageEntity() {
    }

    UUID id() {
        return id;
    }

    VendorInboxMessageEntity(UUID id, String sourceSystem, VendorChannel channel, String messageType,
            String idempotencyKey, String siteCode, String outcome, VendorRejectionReason rejectionReason,
            String rejectionDetail, String payloadHash, Instant signedAt, Instant receivedAt, String correlationId) {
        this.id = id;
        this.sourceSystem = sourceSystem;
        this.channel = channel;
        this.messageType = truncate(messageType, 120);
        this.idempotencyKey = truncate(idempotencyKey, 200);
        this.siteCode = truncate(siteCode, 40);
        this.outcome = outcome;
        this.rejectionReason = rejectionReason;
        this.rejectionDetail = truncate(rejectionDetail, 1000);
        this.payloadHash = payloadHash;
        this.signedAt = signedAt;
        this.receivedAt = receivedAt;
        this.correlationId = truncate(correlationId, 120);
    }

    /** A forged message's fields are attacker-sized; the log keeps a bounded prefix rather than failing. */
    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
