package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.Camera;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.CameraHealthStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RecordingStatus;
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

/** JPA mapping for {@link Camera}. */
@Entity
@Table(name = "cctv_cameras", schema = "safety_security")
public class CameraJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "camera_id", nullable = false, length = 120)
    private String cameraId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "location_ref", length = 120)
    private String locationRef;
    @Column(name = "coverage_area", length = 500)
    private String coverageArea;
    @Column(name = "examination_area", nullable = false)
    private boolean examinationArea;
    @Enumerated(EnumType.STRING)
    @Column(name = "recording_status", nullable = false, length = 20)
    private RecordingStatus recordingStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 20)
    private CameraHealthStatus healthStatus;
    @Column(name = "last_health_at", nullable = false)
    private Instant lastHealthAt;
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

    protected CameraJpaEntity() {
    }

    public void apply(Camera camera) {
        id = camera.id();
        siteCode = camera.siteCode();
        cameraId = camera.cameraId();
        name = camera.name();
        locationRef = camera.locationRef();
        coverageArea = camera.coverageArea();
        examinationArea = camera.examinationArea();
        recordingStatus = camera.recordingStatus();
        healthStatus = camera.healthStatus();
        lastHealthAt = camera.lastHealthAt();
        RecordMetadata metadata = camera.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public Camera toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new Camera(id, siteCode, cameraId, name, locationRef, coverageArea, examinationArea, recordingStatus,
                healthStatus, lastHealthAt, metadata);
    }

    public UUID getId() {
        return id;
    }
}
