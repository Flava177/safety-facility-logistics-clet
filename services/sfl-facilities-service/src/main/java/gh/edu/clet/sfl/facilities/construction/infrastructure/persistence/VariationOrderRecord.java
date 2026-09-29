package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link VariationOrder}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_variations", schema = "facilities")
public class VariationOrderRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "variation_reference", nullable = false, length = 40)
    private String variationReference;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "change_description", nullable = false, length = 4000)
    private String changeDescription;
    @Column(name = "cost_delta", nullable = false, precision = 18, scale = 2)
    private BigDecimal costDelta;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 4000)
    private String justification;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VariationOrder.Status status;
    @Column(name = "escalation_required", nullable = false)
    private boolean escalationRequired;
    @Column(name = "escalation_reason", length = 1000)
    private String escalationReason;
    @Column(name = "submitted_by", nullable = false, length = 160)
    private String submittedBy;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    @Column(name = "decided_by", length = 160)
    private String decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_note", length = 2000)
    private String decisionNote;
    @Column(name = "escalated_approved_by", length = 160)
    private String escalatedApprovedBy;
    @Column(name = "escalated_approved_at")
    private Instant escalatedApprovedAt;
    @Column(name = "cumulative_percent", precision = 9, scale = 4)
    private BigDecimal cumulativePercent;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected VariationOrderRecord() {
    }

    static VariationOrderRecord empty() {
        return new VariationOrderRecord();
    }

    void apply(VariationOrder variation) {
        id = variation.id();
        variationReference = variation.variationReference();
        projectId = variation.projectId();
        siteCode = variation.siteCode();
        changeDescription = variation.changeDescription();
        costDelta = variation.costDelta();
        currency = variation.currency();
        justification = variation.justification();
        status = variation.status();
        escalationRequired = variation.escalationRequired();
        escalationReason = variation.escalationReason();
        submittedBy = variation.submittedBy();
        submittedAt = variation.submittedAt();
        decidedBy = variation.decidedBy();
        decidedAt = variation.decidedAt();
        decisionNote = variation.decisionNote();
        escalatedApprovedBy = variation.escalatedApprovedBy();
        escalatedApprovedAt = variation.escalatedApprovedAt();
        cumulativePercent = variation.cumulativePercent();
        metadata = RecordMetadataEmbeddable.from(variation.metadata());
    }

    VariationOrder toDomain() {
        return new VariationOrder(id, variationReference, projectId, siteCode, changeDescription, costDelta, currency,
                justification, status, escalationRequired, escalationReason, submittedBy, submittedAt, decidedBy,
                decidedAt, decisionNote, escalatedApprovedBy, escalatedApprovedAt, cumulativePercent,
                metadata.toDomain(recordVersion()));
    }
}
