package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.InspectionSchedule;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
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
@Table(name = "lifesafety_inspection_schedules", schema = "safety_security")
public class InspectionScheduleJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "system_ref", nullable = false, length = 120)
    private String systemRef;
    @Column(length = 500)
    private String description;
    @Column(name = "frequency_days", nullable = false)
    private int frequencyDays;
    @Column(name = "last_performed_at")
    private Instant lastPerformedAt;
    @Column(name = "next_due_at")
    private Instant nextDueAt;
    @Column(name = "evidence_reference", length = 500)
    private String evidenceReference;
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

    protected InspectionScheduleJpaEntity() {
    }

    public static InspectionScheduleJpaEntity from(InspectionSchedule s) {
        InspectionScheduleJpaEntity e = new InspectionScheduleJpaEntity();
        e.apply(s);
        return e;
    }

    public void apply(InspectionSchedule s) {
        id = s.id();
        siteCode = s.siteCode();
        systemRef = s.systemRef();
        description = s.description();
        frequencyDays = s.frequencyDays();
        lastPerformedAt = s.lastPerformedAt();
        nextDueAt = s.nextDueAt();
        evidenceReference = s.evidenceReference();
        createdBy = s.metadata().createdBy();
        createdAt = s.metadata().createdAt();
        lastModifiedBy = s.metadata().lastModifiedBy();
        lastModifiedAt = s.metadata().lastModifiedAt();
        sourceChannel = s.metadata().sourceChannel();
        correlationId = s.metadata().correlationId();
    }

    public InspectionSchedule toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new InspectionSchedule(id, siteCode, systemRef, description, frequencyDays, lastPerformedAt,
                nextDueAt, evidenceReference, metadata);
    }
}
