package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DispatchOutcome;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.ResponseDispatch;
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
@Table(name = "intrusion_response_dispatches", schema = "safety_security")
public class ResponseDispatchJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "alarm_id", nullable = false)
    private UUID alarmId;
    @Column(name = "monitoring_service", nullable = false, length = 200)
    private String monitoringService;
    @Column(name = "dispatch_requested_at", nullable = false)
    private Instant dispatchRequestedAt;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "arrived_at")
    private Instant arrivedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchOutcome outcome;
    @Column(length = 2000)
    private String notes;
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

    protected ResponseDispatchJpaEntity() {
    }

    public void apply(ResponseDispatch dispatch) {
        id = dispatch.id();
        siteCode = dispatch.siteCode();
        alarmId = dispatch.alarmId();
        monitoringService = dispatch.monitoringService();
        dispatchRequestedAt = dispatch.dispatchRequestedAt();
        acknowledgedAt = dispatch.acknowledgedAt();
        arrivedAt = dispatch.arrivedAt();
        outcome = dispatch.outcome();
        notes = dispatch.notes();
        RecordMetadata metadata = dispatch.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public ResponseDispatch toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new ResponseDispatch(id, siteCode, alarmId, monitoringService, dispatchRequestedAt, acknowledgedAt,
                arrivedAt, outcome, notes, metadata);
    }

    public UUID getId() {
        return id;
    }
}
