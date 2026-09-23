package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessOverride;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.OverrideStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_overrides", schema = "safety_security")
public class AccessOverrideJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "scope_ref", nullable = false, length = 200)
    private String scopeRef;
    @Column(nullable = false, length = 2000)
    private String reason;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "approver_id", length = 160)
    private String approverId;
    @Column(name = "break_glass", nullable = false)
    private boolean breakGlass;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OverrideStatus status;
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

    protected AccessOverrideJpaEntity() {
    }

    public void apply(AccessOverride override) {
        id = override.id();
        siteCode = override.siteCode();
        scopeRef = override.scopeRef();
        reason = override.reason();
        requestedBy = override.requestedBy();
        approverId = override.approverId();
        breakGlass = override.breakGlass();
        startsAt = override.startsAt();
        expiresAt = override.expiresAt();
        status = override.status();
        RecordMetadata metadata = override.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public AccessOverride toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new AccessOverride(id, siteCode, scopeRef, reason, requestedBy, approverId, breakGlass, startsAt,
                expiresAt, status, metadata);
    }

    public UUID getId() {
        return id;
    }
}
