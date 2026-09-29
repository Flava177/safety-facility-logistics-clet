package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.ProjectPermitLink;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA mapping for {@link ProjectPermitLink}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_project_permits", schema = "facilities")
public class ProjectPermitLinkRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "permit_id", nullable = false, length = 80)
    private String permitId;
    @Column(name = "work_type", nullable = false, length = 60)
    private String workType;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ProjectPermitLinkRecord() {
    }

    static ProjectPermitLinkRecord empty() {
        return new ProjectPermitLinkRecord();
    }

    void apply(ProjectPermitLink link) {
        id = link.id();
        projectId = link.projectId();
        siteCode = link.siteCode();
        permitId = link.permitId();
        workType = link.workType();
        metadata = RecordMetadataEmbeddable.from(link.metadata());
    }

    ProjectPermitLink toDomain() {
        return new ProjectPermitLink(id, projectId, siteCode, permitId, workType, metadata.toDomain(recordVersion()));
    }
}
