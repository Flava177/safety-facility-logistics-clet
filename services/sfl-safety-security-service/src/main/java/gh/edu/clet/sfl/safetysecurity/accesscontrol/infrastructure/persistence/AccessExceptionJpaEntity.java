package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionRuleCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionSeverity;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionStatus;
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
@Table(name = "access_exceptions", schema = "safety_security")
public class AccessExceptionJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "reader_id", length = 120)
    private String readerId;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false, length = 30)
    private ExceptionRuleCode ruleCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExceptionSeverity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExceptionStatus status;
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

    protected AccessExceptionJpaEntity() {
    }

    public void apply(AccessException exception) {
        id = exception.id();
        siteCode = exception.siteCode();
        eventId = exception.eventId();
        readerId = exception.readerId();
        zoneCode = exception.zoneCode();
        ruleCode = exception.ruleCode();
        severity = exception.severity();
        status = exception.status();
        seededIncidentId = exception.seededIncidentId();
        siemForwardedAt = exception.siemForwardedAt();
        RecordMetadata metadata = exception.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public AccessException toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new AccessException(id, siteCode, eventId, readerId, zoneCode, ruleCode, severity, status,
                seededIncidentId, siemForwardedAt, metadata);
    }

    public UUID getId() {
        return id;
    }
}
