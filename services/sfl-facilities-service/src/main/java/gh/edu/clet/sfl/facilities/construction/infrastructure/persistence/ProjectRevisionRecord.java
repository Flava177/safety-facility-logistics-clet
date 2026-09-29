package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.ProjectRevision;
import gh.edu.clet.sfl.facilities.construction.domain.RevisionSubject;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA mapping for {@link ProjectRevision}. Column names match V21 exactly.
 *
 * <p>Deliberately not a {@code VersionedRecord}: a row here is never updated (V21's trigger refuses it),
 * so it has no optimistic lock to take, and {@code record_version} keeps its column default of 0.
 */
@Entity
@Table(name = "construction_project_revisions", schema = "facilities")
public class ProjectRevisionRecord {

    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RevisionSubject subject;
    @Column(name = "subject_id")
    private UUID subjectId;
    @Column(name = "subject_code", nullable = false, length = 40)
    private String subjectCode;
    @Column(nullable = false)
    private int revision;
    @Column(precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(length = 3)
    private String currency;
    @Column(name = "target_date")
    private LocalDate targetDate;
    @Column(length = 2000)
    private String reason;
    @Column(name = "revised_by", nullable = false, length = 160)
    private String revisedBy;
    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ProjectRevisionRecord() {
    }

    static ProjectRevisionRecord of(ProjectRevision revision) {
        ProjectRevisionRecord record = new ProjectRevisionRecord();
        record.id = revision.id();
        record.projectId = revision.projectId();
        record.siteCode = revision.siteCode();
        record.subject = revision.subject();
        record.subjectId = revision.subjectId();
        record.subjectCode = revision.subjectCode();
        record.revision = revision.revision();
        record.amount = revision.amount();
        record.currency = revision.currency();
        record.targetDate = revision.targetDate();
        record.reason = revision.reason();
        record.revisedBy = revision.revisedBy();
        record.revisedAt = revision.revisedAt();
        record.metadata = RecordMetadataEmbeddable.from(revision.metadata());
        return record;
    }

    ProjectRevision toDomain() {
        return new ProjectRevision(id, projectId, siteCode, subject, subjectId, subjectCode, revision, amount, currency,
                targetDate, reason, revisedBy, revisedAt, metadata.toDomain(0L));
    }
}
