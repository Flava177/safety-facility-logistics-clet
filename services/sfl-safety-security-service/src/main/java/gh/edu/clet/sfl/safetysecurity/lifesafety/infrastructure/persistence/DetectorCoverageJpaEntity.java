package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorCoverage;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.TestResult;
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
@Table(name = "lifesafety_detector_coverage", schema = "safety_security")
public class DetectorCoverageJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Column(name = "device_ref", nullable = false, length = 120)
    private String deviceRef;
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30)
    private DetectorType deviceType;
    @Column(nullable = false)
    private boolean covered;
    @Column(name = "last_test_at")
    private Instant lastTestAt;
    @Column(name = "next_test_due_at")
    private Instant nextTestDueAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "last_test_result", nullable = false, length = 20)
    private TestResult lastTestResult;
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

    protected DetectorCoverageJpaEntity() {
    }

    public static DetectorCoverageJpaEntity from(DetectorCoverage c) {
        DetectorCoverageJpaEntity e = new DetectorCoverageJpaEntity();
        e.apply(c);
        return e;
    }

    public void apply(DetectorCoverage c) {
        id = c.id();
        siteCode = c.siteCode();
        zoneCode = c.zoneCode();
        deviceRef = c.deviceRef();
        deviceType = c.deviceType();
        covered = c.covered();
        lastTestAt = c.lastTestAt();
        nextTestDueAt = c.nextTestDueAt();
        lastTestResult = c.lastTestResult();
        createdBy = c.metadata().createdBy();
        createdAt = c.metadata().createdAt();
        lastModifiedBy = c.metadata().lastModifiedBy();
        lastModifiedAt = c.metadata().lastModifiedAt();
        sourceChannel = c.metadata().sourceChannel();
        correlationId = c.metadata().correlationId();
    }

    public DetectorCoverage toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new DetectorCoverage(id, siteCode, zoneCode, deviceRef, deviceType, covered, lastTestAt,
                nextTestDueAt, lastTestResult, metadata);
    }
}
