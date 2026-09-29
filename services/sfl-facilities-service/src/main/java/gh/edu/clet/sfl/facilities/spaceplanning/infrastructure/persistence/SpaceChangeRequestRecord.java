package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link SpaceChangeRequest}. */
@Entity
@Table(name = "space_change_requests", schema = "facilities")
public class SpaceChangeRequestRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(nullable = false, length = 40)
    private String reference;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "requesting_unit", nullable = false, length = 200)
    private String requestingUnit;
    @Column(nullable = false, length = 4000)
    private String justification;
    @Column(name = "target_room_id")
    private UUID targetRoomId;
    @Column(name = "target_room_code", length = 80)
    private String targetRoomCode;
    @Column(name = "target_area_description", length = 2000)
    private String targetAreaDescription;
    @Column(name = "required_headcount")
    private Integer requiredHeadcount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceChangeRequest.Urgency urgency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceChangeRequest.Status status;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "decided_by", length = 160)
    private String decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_reason", length = 2000)
    private String decisionReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_type", length = 30)
    private SpaceChangeRequest.OutcomeType outcomeType;
    @Column(name = "linked_scenario_id")
    private UUID linkedScenarioId;
    @Column(name = "linked_project_id")
    private UUID linkedProjectId;
    @Column(name = "linked_project_reference", length = 80)
    private String linkedProjectReference;
    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected SpaceChangeRequestRecord() {
    }

    void apply(SpaceChangeRequest r) {
        id = r.id();
        reference = r.reference();
        siteCode = r.siteCode();
        requestingUnit = r.requestingUnit();
        justification = r.justification();
        targetRoomId = r.targetRoomId();
        targetRoomCode = r.targetRoomCode();
        targetAreaDescription = r.targetAreaDescription();
        requiredHeadcount = r.requiredHeadcount();
        urgency = r.urgency();
        status = r.status();
        requestedBy = r.requestedBy();
        requestedAt = r.requestedAt();
        decidedBy = r.decidedBy();
        decidedAt = r.decidedAt();
        decisionReason = r.decisionReason();
        outcomeType = r.outcomeType();
        linkedScenarioId = r.linkedScenarioId();
        linkedProjectId = r.linkedProjectId();
        linkedProjectReference = r.linkedProjectReference();
        resolvedBy = r.resolvedBy();
        resolvedAt = r.resolvedAt();
        resolutionNote = r.resolutionNote();
        metadata = RecordMetadataEmbeddable.from(r.metadata());
    }

    SpaceChangeRequest toDomain() {
        return new SpaceChangeRequest(id, reference, siteCode, requestingUnit, justification, targetRoomId,
                targetRoomCode, targetAreaDescription, requiredHeadcount, urgency, status, requestedBy, requestedAt,
                decidedBy, decidedAt, decisionReason, outcomeType, linkedScenarioId, linkedProjectId,
                linkedProjectReference, resolvedBy, resolvedAt, resolutionNote, metadata.toDomain(recordVersion()));
    }
}
