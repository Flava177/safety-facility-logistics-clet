package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectOrigin;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
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
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link ConstructionProject}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_projects", schema = "facilities")
public class ConstructionProjectRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "project_reference", nullable = false, length = 40)
    private String projectReference;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 4000)
    private String scope;
    @Column(name = "work_types", length = 400)
    private String workTypes;
    @Column(name = "budget_baseline", precision = 18, scale = 2)
    private BigDecimal budgetBaseline;
    @Column(length = 3)
    private String currency;
    @Column(name = "baseline_revision", nullable = false)
    private int baselineRevision;
    @Column(name = "funding_source_reference", length = 120)
    private String fundingSourceReference;
    @Column(name = "funding_source_name", length = 200)
    private String fundingSourceName;
    @Column(name = "project_manager_id", length = 160)
    private String projectManagerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectOrigin origin;
    @Column(name = "space_change_request_id")
    private UUID spaceChangeRequestId;
    @Column(name = "committed_scenario_id")
    private UUID committedScenarioId;
    @Column(name = "affected_room_ids", length = 4000)
    private String affectedRoomIds;
    @Column(name = "requesting_unit", length = 200)
    private String requestingUnit;
    @Column(length = 4000)
    private String justification;
    @Column(name = "approval_id")
    private UUID approvalId;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "practical_completion_at")
    private Instant practicalCompletionAt;
    @Column(name = "handed_over_at")
    private Instant handedOverAt;
    @Column(name = "defects_liability_ends_on")
    private LocalDate defectsLiabilityEndsOn;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "cancellation_reason", length = 2000)
    private String cancellationReason;
    @Column(name = "registered_by", nullable = false, length = 160)
    private String registeredBy;
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ConstructionProjectRecord() {
    }

    static ConstructionProjectRecord empty() {
        return new ConstructionProjectRecord();
    }

    void apply(ConstructionProject project) {
        id = project.id();
        projectReference = project.projectReference();
        siteCode = project.siteCode();
        title = project.title();
        scope = project.scope();
        workTypes = ConstructionColumns.join(project.workTypes());
        budgetBaseline = project.budgetBaseline();
        currency = project.currency();
        baselineRevision = project.baselineRevision();
        fundingSourceReference = project.fundingSourceReference();
        fundingSourceName = project.fundingSourceName();
        projectManagerId = project.projectManagerId();
        status = project.status();
        origin = project.origin();
        spaceChangeRequestId = project.spaceChangeRequestId();
        committedScenarioId = project.committedScenarioId();
        affectedRoomIds = ConstructionColumns.join(project.affectedRoomIds());
        requestingUnit = project.requestingUnit();
        justification = project.justification();
        approvalId = project.approvalId();
        startedAt = project.startedAt();
        practicalCompletionAt = project.practicalCompletionAt();
        handedOverAt = project.handedOverAt();
        defectsLiabilityEndsOn = project.defectsLiabilityEndsOn();
        closedAt = project.closedAt();
        cancellationReason = project.cancellationReason();
        registeredBy = project.registeredBy();
        registeredAt = project.registeredAt();
        metadata = RecordMetadataEmbeddable.from(project.metadata());
    }

    ConstructionProject toDomain() {
        return new ConstructionProject(id, projectReference, siteCode, title, scope,
                ConstructionColumns.strings(workTypes), budgetBaseline, currency, baselineRevision,
                fundingSourceReference, fundingSourceName, projectManagerId, status, origin, spaceChangeRequestId,
                committedScenarioId, ConstructionColumns.uuids(affectedRoomIds), requestingUnit, justification,
                approvalId, startedAt, practicalCompletionAt, handedOverAt, defectsLiabilityEndsOn, closedAt,
                cancellationReason, registeredBy, registeredAt, metadata.toDomain(recordVersion()));
    }
}
