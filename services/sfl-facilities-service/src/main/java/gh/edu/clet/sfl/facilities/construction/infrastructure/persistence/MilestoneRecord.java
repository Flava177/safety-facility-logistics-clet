package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.Milestone;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link Milestone}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_milestones", schema = "facilities")
public class MilestoneRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "milestone_code", nullable = false, length = 40)
    private String milestoneCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;
    @Column(nullable = false)
    private int revision;
    @Column(name = "achieved_on")
    private LocalDate achievedOn;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected MilestoneRecord() {
    }

    static MilestoneRecord empty() {
        return new MilestoneRecord();
    }

    void apply(Milestone milestone) {
        id = milestone.id();
        projectId = milestone.projectId();
        siteCode = milestone.siteCode();
        milestoneCode = milestone.milestoneCode();
        name = milestone.name();
        targetDate = milestone.targetDate();
        revision = milestone.revision();
        achievedOn = milestone.achievedOn();
        metadata = RecordMetadataEmbeddable.from(milestone.metadata());
    }

    Milestone toDomain() {
        return new Milestone(id, projectId, siteCode, milestoneCode, name, targetDate, revision, achievedOn,
                metadata.toDomain(recordVersion()));
    }
}
