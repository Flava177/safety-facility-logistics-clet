package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facility_readiness_blockers", schema = "facilities")
class ReadinessBlockerEntity {

    @Id
    private UUID id;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "assessment_id")
    private UUID assessmentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BlockerSource source;
    @Column(name = "source_reference", length = 160)
    private String sourceReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockerSeverity severity;
    @Column(nullable = false, length = 1000)
    private String description;
    @Column(name = "raised_by", nullable = false, length = 160)
    private String raisedBy;
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;
    @Column(nullable = false)
    private boolean resolved;
    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    protected ReadinessBlockerEntity() {
    }

    static ReadinessBlockerEntity from(ReadinessBlocker blocker) {
        ReadinessBlockerEntity entity = new ReadinessBlockerEntity();
        entity.id = blocker.id();
        entity.roomId = blocker.roomId();
        entity.siteCode = blocker.siteCode();
        entity.assessmentId = blocker.assessmentId();
        entity.source = blocker.source();
        entity.sourceReference = blocker.sourceReference();
        entity.severity = blocker.severity();
        entity.description = blocker.description();
        entity.raisedBy = blocker.raisedBy();
        entity.raisedAt = blocker.raisedAt();
        entity.resolved = blocker.resolved();
        entity.resolvedBy = blocker.resolvedBy();
        entity.resolvedAt = blocker.resolvedAt();
        entity.resolutionNotes = blocker.resolutionNotes();
        return entity;
    }

    ReadinessBlocker toDomain() {
        return new ReadinessBlocker(id, roomId, siteCode, assessmentId, source, sourceReference, severity,
                description, raisedBy, raisedAt, resolved, resolvedBy, resolvedAt, resolutionNotes);
    }
}
