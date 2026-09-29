package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.CommitOutcome;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link AllocationScenario}. Column names match V18 exactly. */
@Entity
@Table(name = "space_scenarios", schema = "facilities")
public class SpaceScenarioRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "plan_reference", nullable = false, length = 40)
    private String planReference;
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
    @Column(name = "based_on_scenario_id")
    private UUID basedOnScenarioId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 4000)
    private String description;
    @Column(name = "space_change_request_id")
    private UUID spaceChangeRequestId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScenarioStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "commit_outcome", length = 20)
    private CommitOutcome commitOutcome;
    @Column(name = "commit_note", length = 2000)
    private String commitNote;
    @Column(name = "committed_by", length = 160)
    private String committedBy;
    @Column(name = "committed_at")
    private Instant committedAt;
    @Column(name = "applied_to_register_at")
    private Instant appliedToRegisterAt;
    @Column(name = "linked_project_id")
    private UUID linkedProjectId;
    @Column(name = "linked_project_reference", length = 80)
    private String linkedProjectReference;
    @Column(name = "handed_over_by", length = 160)
    private String handedOverBy;
    @Column(name = "handed_over_at")
    private Instant handedOverAt;
    @Column(name = "discarded_by", length = 160)
    private String discardedBy;
    @Column(name = "discarded_at")
    private Instant discardedAt;
    @Column(name = "discard_reason", length = 2000)
    private String discardReason;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected SpaceScenarioRecord() {
    }

    void apply(AllocationScenario s) {
        id = s.id();
        siteCode = s.siteCode();
        planReference = s.planReference();
        versionNumber = s.versionNumber();
        basedOnScenarioId = s.basedOnScenarioId();
        name = s.name();
        description = s.description();
        spaceChangeRequestId = s.spaceChangeRequestId();
        status = s.status();
        commitOutcome = s.commitOutcome();
        commitNote = s.commitNote();
        committedBy = s.committedBy();
        committedAt = s.committedAt();
        appliedToRegisterAt = s.appliedToRegisterAt();
        linkedProjectId = s.linkedProjectId();
        linkedProjectReference = s.linkedProjectReference();
        handedOverBy = s.handedOverBy();
        handedOverAt = s.handedOverAt();
        discardedBy = s.discardedBy();
        discardedAt = s.discardedAt();
        discardReason = s.discardReason();
        metadata = RecordMetadataEmbeddable.from(s.metadata());
    }

    AllocationScenario toDomain() {
        return new AllocationScenario(id, siteCode, planReference, versionNumber, basedOnScenarioId, name, description,
                spaceChangeRequestId, status, commitOutcome, commitNote, committedBy, committedAt, appliedToRegisterAt,
                linkedProjectId, linkedProjectReference, handedOverBy, handedOverAt, discardedBy, discardedAt,
                discardReason, metadata.toDomain(recordVersion()));
    }
}
