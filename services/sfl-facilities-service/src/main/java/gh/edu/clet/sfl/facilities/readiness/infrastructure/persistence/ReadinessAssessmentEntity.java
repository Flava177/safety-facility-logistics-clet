package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessmentItem;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A submitted assessment and its answers.
 *
 * <p>The table carries an append-only trigger (V7). Nothing here updates: an assessment is written
 * once and read forever, and a changed space gets a new one.
 */
@Entity
@Table(name = "facility_readiness_assessments", schema = "facilities")
class ReadinessAssessmentEntity {

    @Id
    private UUID id;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "checklist_id")
    private UUID checklistId;
    @Column(name = "checklist_code", length = 60)
    private String checklistCode;
    @Column(name = "checklist_version", nullable = false)
    private int checklistVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "operating_mode", nullable = false, length = 20)
    private OperatingMode operatingMode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationReadinessStatus outcome;
    @Column(nullable = false)
    private int score;
    @Column(length = 2000)
    private String notes;
    @Column(name = "assessed_by", nullable = false, length = 160)
    private String assessedBy;
    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "assessment_id", nullable = false)
    private List<ReadinessAssessmentItemEntity> items = new ArrayList<>();

    protected ReadinessAssessmentEntity() {
    }

    static ReadinessAssessmentEntity from(ReadinessAssessment assessment, String correlationId) {
        ReadinessAssessmentEntity entity = new ReadinessAssessmentEntity();
        entity.id = assessment.id();
        entity.roomId = assessment.roomId();
        entity.siteCode = assessment.siteCode();
        entity.checklistId = assessment.checklistId();
        entity.checklistCode = assessment.checklistCode();
        entity.checklistVersion = assessment.checklistVersion();
        entity.operatingMode = assessment.operatingMode();
        entity.outcome = assessment.outcome();
        entity.score = assessment.score();
        entity.notes = assessment.notes();
        entity.assessedBy = assessment.assessedBy();
        entity.assessedAt = assessment.assessedAt();
        entity.correlationId = correlationId;
        assessment.items().stream().map(ReadinessAssessmentItemEntity::from).forEach(entity.items::add);
        return entity;
    }

    ReadinessAssessment toDomain() {
        List<ReadinessAssessmentItem> domainItems = items.stream().map(item -> item.toDomain(id)).toList();
        return new ReadinessAssessment(id, roomId, siteCode, checklistId, checklistCode, checklistVersion,
                operatingMode, outcome, score, domainItems, notes, assessedBy, assessedAt);
    }
}
