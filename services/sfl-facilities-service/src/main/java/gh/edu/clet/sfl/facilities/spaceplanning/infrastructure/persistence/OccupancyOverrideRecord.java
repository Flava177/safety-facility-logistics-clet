package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link OccupancyOverride}. */
@Entity
@Table(name = "occupancy_overrides", schema = "facilities")
public class OccupancyOverrideRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Column(name = "headcount_covered", nullable = false)
    private int headcountCovered;
    @Column(name = "compliance_detail", length = 2000)
    private String complianceDetail;
    @Column(nullable = false, length = 2000)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OccupancyOverride.OverrideStatus status;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "approved_by", length = 160)
    private String approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected OccupancyOverrideRecord() {
    }

    void apply(OccupancyOverride o) {
        id = o.id();
        siteCode = o.siteCode();
        scenarioId = o.scenarioId();
        roomId = o.roomId();
        roomCode = o.roomCode();
        headcountCovered = o.headcountCovered();
        complianceDetail = o.complianceDetail();
        reason = o.reason();
        status = o.status();
        requestedBy = o.requestedBy();
        requestedAt = o.requestedAt();
        approvedBy = o.approvedBy();
        approvedAt = o.approvedAt();
        withdrawnAt = o.withdrawnAt();
        metadata = RecordMetadataEmbeddable.from(o.metadata());
    }

    OccupancyOverride toDomain() {
        return new OccupancyOverride(id, siteCode, scenarioId, roomId, roomCode, headcountCovered, complianceDetail,
                reason, status, requestedBy, requestedAt, approvedBy, approvedAt, withdrawnAt,
                metadata.toDomain(recordVersion()));
    }
}
