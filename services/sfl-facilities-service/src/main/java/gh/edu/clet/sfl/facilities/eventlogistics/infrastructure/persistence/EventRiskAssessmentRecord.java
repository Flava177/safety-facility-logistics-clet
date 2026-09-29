package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.RiskAssessmentProjection;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** JPA mapping for {@link RiskAssessmentProjection}, keyed by S165's assessment id and version. */
@Entity
@Table(name = "event_risk_assessments", schema = "facilities")
@IdClass(EventRiskAssessmentRecord.Key.class)
public class EventRiskAssessmentRecord extends VersionedRecord {

    /** Written by the SSEMP event handler, never by a person. */
    static final String WRITER = "system.ssemp-integration";

    @Id
    @Column(name = "assessment_id", nullable = false, length = 120)
    private String assessmentId;
    @Id
    @Column(nullable = false)
    private int version;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskAssessmentCurrency.Status status;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskAssessmentCurrency.RiskLevel riskLevel;
    @Column(name = "review_due_at")
    private Instant reviewDueAt;
    @Column(name = "author_id", length = 160)
    private String authorId;
    @Column(name = "signed_off_by", length = 160)
    private String signedOffBy;
    @Column(name = "last_event_type", nullable = false, length = 120)
    private String lastEventType;
    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EventRiskAssessmentRecord() {
    }

    static EventRiskAssessmentRecord empty() {
        return new EventRiskAssessmentRecord();
    }

    void apply(RiskAssessmentProjection projection) {
        Instant created = metadata == null ? projection.lastEventAt() : metadata.createdAt();
        assessmentId = projection.assessmentId();
        version = projection.version();
        siteCode = projection.siteCode();
        status = projection.status();
        riskLevel = projection.riskLevel();
        reviewDueAt = projection.reviewDueAt();
        authorId = projection.authorId();
        signedOffBy = projection.signedOffBy();
        lastEventType = projection.lastEventType();
        lastEventAt = projection.lastEventAt();
        metadata = RecordMetadataEmbeddable.from(new RecordMetadata(WRITER, created, WRITER, projection.lastEventAt(),
                0L, SourceChannel.INTEGRATION, null));
    }

    RiskAssessmentProjection toDomain() {
        return new RiskAssessmentProjection(assessmentId, version, siteCode, status, riskLevel, reviewDueAt, authorId,
                signedOffBy, lastEventType, lastEventAt);
    }

    /** The composite key. */
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        private String assessmentId;
        private int version;

        public Key() {
        }

        public Key(String assessmentId, int version) {
            this.assessmentId = assessmentId;
            this.version = version;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.version == version && Objects.equals(key.assessmentId, assessmentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assessmentId, version);
        }
    }
}
