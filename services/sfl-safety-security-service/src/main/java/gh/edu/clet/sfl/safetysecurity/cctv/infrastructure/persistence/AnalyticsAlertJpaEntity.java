package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertSeverity;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AnalyticsAlert;
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

/** JPA mapping for {@link AnalyticsAlert}. */
@Entity
@Table(name = "cctv_analytics_alerts", schema = "safety_security")
public class AnalyticsAlertJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "camera_id", nullable = false, length = 120)
    private String cameraId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "seeded_incident_id")
    private UUID seededIncidentId;
    @Column(name = "siem_forwarded_at")
    private Instant siemForwardedAt;
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

    protected AnalyticsAlertJpaEntity() {
    }

    public void apply(AnalyticsAlert alert) {
        id = alert.id();
        siteCode = alert.siteCode();
        cameraId = alert.cameraId();
        type = alert.type();
        severity = alert.severity();
        status = alert.status();
        occurredAt = alert.occurredAt();
        seededIncidentId = alert.seededIncidentId();
        siemForwardedAt = alert.siemForwardedAt();
        RecordMetadata metadata = alert.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public AnalyticsAlert toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new AnalyticsAlert(id, siteCode, cameraId, type, severity, status, occurredAt, seededIncidentId,
                siemForwardedAt, metadata);
    }

    public UUID getId() {
        return id;
    }
}
