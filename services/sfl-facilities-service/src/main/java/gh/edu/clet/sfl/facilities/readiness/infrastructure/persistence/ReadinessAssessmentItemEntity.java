package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessmentItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "facility_readiness_assessment_items", schema = "facilities")
class ReadinessAssessmentItemEntity {

    @Id
    private UUID id;
    /** Nullable: the checklist item may since have been removed by a new checklist version. */
    @Column(name = "checklist_item_id")
    private UUID checklistItemId;
    @Column(name = "item_code", nullable = false, length = 60)
    private String itemCode;
    @Column(nullable = false, length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "severity_if_failed", nullable = false, length = 20)
    private BlockerSeverity severityIfFailed;
    @Column(nullable = false)
    private boolean mandatory;
    @Column(nullable = false)
    private int weight;
    @Column(nullable = false)
    private boolean passed;
    @Column(name = "comment", length = 1000)
    private String comment;

    protected ReadinessAssessmentItemEntity() {
    }

    static ReadinessAssessmentItemEntity from(ReadinessAssessmentItem item) {
        ReadinessAssessmentItemEntity entity = new ReadinessAssessmentItemEntity();
        entity.id = item.id();
        entity.checklistItemId = item.checklistItemId();
        entity.itemCode = item.itemCode();
        entity.description = item.description();
        entity.severityIfFailed = item.severityIfFailed();
        entity.mandatory = item.mandatory();
        entity.weight = item.weight();
        entity.passed = item.passed();
        entity.comment = item.comment();
        return entity;
    }

    ReadinessAssessmentItem toDomain(UUID assessmentId) {
        return new ReadinessAssessmentItem(id, assessmentId, checklistItemId, itemCode, description,
                severityIfFailed, mandatory, weight, passed, comment);
    }
}
