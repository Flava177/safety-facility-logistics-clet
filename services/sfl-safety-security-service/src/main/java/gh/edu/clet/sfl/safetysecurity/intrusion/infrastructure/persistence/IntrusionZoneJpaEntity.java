package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionZone;
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
@Table(name = "intrusion_zones", schema = "safety_security")
public class IntrusionZoneJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "location_ref", length = 120)
    private String locationRef;
    @Column(name = "arm_schedule", nullable = false, length = 500)
    private String armSchedule;
    @Column(name = "protected_zone", nullable = false)
    private boolean protectedZone;
    @Column(nullable = false)
    private boolean armed;
    @Column(name = "examination_mode", nullable = false)
    private boolean examinationMode;
    @Column(name = "examination_until")
    private Instant examinationUntil;
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

    protected IntrusionZoneJpaEntity() {
    }

    public void apply(IntrusionZone zone) {
        id = zone.id();
        siteCode = zone.siteCode();
        zoneCode = zone.zoneCode();
        name = zone.name();
        locationRef = zone.locationRef();
        armSchedule = zone.armSchedule();
        protectedZone = zone.protectedZone();
        armed = zone.armed();
        examinationMode = zone.examinationMode();
        examinationUntil = zone.examinationUntil();
        RecordMetadata metadata = zone.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public IntrusionZone toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new IntrusionZone(id, siteCode, zoneCode, name, locationRef, armSchedule, protectedZone, armed,
                examinationMode, examinationUntil, metadata);
    }

    public UUID getId() {
        return id;
    }
}
