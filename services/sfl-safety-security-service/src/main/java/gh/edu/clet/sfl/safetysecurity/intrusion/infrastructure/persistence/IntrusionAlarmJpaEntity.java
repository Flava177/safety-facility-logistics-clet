package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmSeverity;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
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
@Table(name = "intrusion_alarms", schema = "safety_security")
public class IntrusionAlarmJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "panel_id", nullable = false, length = 120)
    private String panelId;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "alarm_type", nullable = false, length = 20)
    private AlarmType alarmType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSeverity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmStatus status;
    @Column(name = "signal_count", nullable = false)
    private int signalCount;
    @Column(name = "first_signal_at", nullable = false)
    private Instant firstSignalAt;
    @Column(name = "last_signal_at", nullable = false)
    private Instant lastSignalAt;
    @Column(name = "ack_due_at")
    private Instant ackDueAt;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "acknowledged_by", length = 160)
    private String acknowledgedBy;
    @Column(name = "escalated_at")
    private Instant escalatedAt;
    @Column(name = "escalated_to", length = 40)
    private String escalatedTo;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;
    @Column(name = "evidence_ref", length = 200)
    private String evidenceRef;
    @Column(name = "seeded_incident_id")
    private UUID seededIncidentId;
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

    protected IntrusionAlarmJpaEntity() {
    }

    public void apply(IntrusionAlarm alarm) {
        id = alarm.id();
        siteCode = alarm.siteCode();
        panelId = alarm.panelId();
        zoneCode = alarm.zoneCode();
        alarmType = alarm.alarmType();
        severity = alarm.severity();
        status = alarm.status();
        signalCount = alarm.signalCount();
        firstSignalAt = alarm.firstSignalAt();
        lastSignalAt = alarm.lastSignalAt();
        ackDueAt = alarm.ackDueAt();
        acknowledgedAt = alarm.acknowledgedAt();
        acknowledgedBy = alarm.acknowledgedBy();
        escalatedAt = alarm.escalatedAt();
        escalatedTo = alarm.escalatedTo();
        resolvedAt = alarm.resolvedAt();
        resolvedBy = alarm.resolvedBy();
        evidenceRef = alarm.evidenceRef();
        seededIncidentId = alarm.seededIncidentId();
        RecordMetadata metadata = alarm.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public IntrusionAlarm toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, status, signalCount,
                firstSignalAt, lastSignalAt, ackDueAt, acknowledgedAt, acknowledgedBy, escalatedAt, escalatedTo,
                resolvedAt, resolvedBy, evidenceRef, seededIncidentId, metadata);
    }

    public UUID getId() {
        return id;
    }
}
