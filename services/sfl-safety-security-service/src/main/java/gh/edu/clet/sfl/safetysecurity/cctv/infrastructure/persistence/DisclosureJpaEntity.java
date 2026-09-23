package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.Disclosure;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.DisclosureStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link Disclosure}. */
@Entity
@Table(name = "cctv_disclosures", schema = "safety_security")
public class DisclosureJpaEntity {

    @Id
    private UUID id;
    @Column(name = "evidence_item_id", nullable = false)
    private UUID evidenceItemId;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(nullable = false, length = 2000)
    private String purpose;
    @Column(nullable = false, length = 300)
    private String recipient;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisclosureStatus status;
    @Column(name = "decided_by", length = 160)
    private String decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(length = 64)
    private String hash;
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;
    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;
    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 20)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected DisclosureJpaEntity() {
    }

    public void apply(Disclosure disclosure) {
        id = disclosure.id();
        evidenceItemId = disclosure.evidenceItemId();
        siteCode = disclosure.siteCode();
        purpose = disclosure.purpose();
        recipient = disclosure.recipient();
        requestedBy = disclosure.requestedBy();
        status = disclosure.status();
        decidedBy = disclosure.decidedBy();
        decidedAt = disclosure.decidedAt();
        hash = disclosure.hash();
        RecordMetadata metadata = disclosure.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public Disclosure toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new Disclosure(id, evidenceItemId, siteCode, purpose, recipient, requestedBy, status, decidedBy,
                decidedAt, hash, metadata);
    }

    public UUID getId() {
        return id;
    }
}
