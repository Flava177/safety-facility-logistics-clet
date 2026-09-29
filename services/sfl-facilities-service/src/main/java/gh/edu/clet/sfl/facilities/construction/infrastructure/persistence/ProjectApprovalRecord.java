package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.ProjectApproval;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link ProjectApproval}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_project_approvals", schema = "facilities")
public class ProjectApprovalRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "approver_id", nullable = false, length = 160)
    private String approverId;
    @Column(name = "baseline_revision", nullable = false)
    private int baselineRevision;
    @Column(name = "baseline_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal baselineAmount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(length = 2000)
    private String note;
    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ProjectApprovalRecord() {
    }

    static ProjectApprovalRecord empty() {
        return new ProjectApprovalRecord();
    }

    void apply(ProjectApproval approval) {
        id = approval.id();
        projectId = approval.projectId();
        siteCode = approval.siteCode();
        approverId = approval.approverId();
        baselineRevision = approval.baselineRevision();
        baselineAmount = approval.baselineAmount();
        currency = approval.currency();
        note = approval.note();
        approvedAt = approval.approvedAt();
        metadata = RecordMetadataEmbeddable.from(approval.metadata());
    }

    ProjectApproval toDomain() {
        return new ProjectApproval(id, projectId, siteCode, approverId, baselineRevision, baselineAmount, currency, note,
                approvedAt, metadata.toDomain(recordVersion()));
    }
}
