package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.ProjectContractor;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA mapping for {@link ProjectContractor}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_project_contractors", schema = "facilities")
public class ProjectContractorRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "contractor_id", nullable = false)
    private UUID contractorId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectContractor.Role role;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ProjectContractorRecord() {
    }

    static ProjectContractorRecord empty() {
        return new ProjectContractorRecord();
    }

    void apply(ProjectContractor assignment) {
        id = assignment.id();
        projectId = assignment.projectId();
        contractorId = assignment.contractorId();
        siteCode = assignment.siteCode();
        role = assignment.role();
        metadata = RecordMetadataEmbeddable.from(assignment.metadata());
    }

    ProjectContractor toDomain() {
        return new ProjectContractor(id, projectId, contractorId, siteCode, role, metadata.toDomain(recordVersion()));
    }
}
