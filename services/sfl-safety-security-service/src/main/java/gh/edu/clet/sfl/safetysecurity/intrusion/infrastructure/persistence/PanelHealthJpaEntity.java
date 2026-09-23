package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.PanelHealth;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.PanelHealthStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SourceChannel;
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
@Table(name = "intrusion_panel_health", schema = "safety_security")
public class PanelHealthJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "panel_id", nullable = false, length = 120)
    private String panelId;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PanelHealthStatus status;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
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

    protected PanelHealthJpaEntity() {
    }

    public void apply(PanelHealth health) {
        id = health.id();
        siteCode = health.siteCode();
        panelId = health.panelId();
        zoneCode = health.zoneCode();
        status = health.status();
        lastSeenAt = health.lastSeenAt();
        RecordMetadata metadata = health.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public PanelHealth toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new PanelHealth(id, siteCode, panelId, zoneCode, status, lastSeenAt, metadata);
    }
}
