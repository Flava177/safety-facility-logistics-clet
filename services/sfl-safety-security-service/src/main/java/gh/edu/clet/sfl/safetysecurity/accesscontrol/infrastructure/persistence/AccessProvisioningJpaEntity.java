package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessProvisioning;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ProvisioningBasis;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ProvisioningStatus;
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
@Table(name = "access_provisioning", schema = "safety_security")
public class AccessProvisioningJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "person_ref", nullable = false, length = 160)
    private String personRef;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvisioningBasis basis;
    @Column(length = 2000)
    private String reason;
    @Column(name = "approver_id", length = 160)
    private String approverId;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvisioningStatus status;
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

    protected AccessProvisioningJpaEntity() {
    }

    public void apply(AccessProvisioning provisioning) {
        id = provisioning.id();
        siteCode = provisioning.siteCode();
        personRef = provisioning.personRef();
        zoneCode = provisioning.zoneCode();
        basis = provisioning.basis();
        reason = provisioning.reason();
        approverId = provisioning.approverId();
        expiresAt = provisioning.expiresAt();
        status = provisioning.status();
        RecordMetadata metadata = provisioning.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public AccessProvisioning toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new AccessProvisioning(id, siteCode, personRef, zoneCode, basis, reason, approverId, expiresAt,
                status, metadata);
    }

    public UUID getId() {
        return id;
    }
}
