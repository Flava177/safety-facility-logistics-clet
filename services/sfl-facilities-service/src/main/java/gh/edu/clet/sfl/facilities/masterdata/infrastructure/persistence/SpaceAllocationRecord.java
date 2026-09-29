package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link SpaceAllocation}. Column names match V18 exactly. */
@Entity
@Table(name = "space_allocations", schema = "facilities")
public class SpaceAllocationRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Column(name = "allocated_unit", nullable = false, length = 200)
    private String allocatedUnit;
    @Column(nullable = false)
    private int headcount;
    @Column(name = "allocated_since", nullable = false)
    private Instant allocatedSince;
    @Column(name = "source_scenario_id", nullable = false)
    private UUID sourceScenarioId;
    @Column(name = "source_reference", length = 80)
    private String sourceReference;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "ended_by_scenario_id")
    private UUID endedByScenarioId;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected SpaceAllocationRecord() {
    }

    void apply(SpaceAllocation allocation) {
        id = allocation.id();
        siteCode = allocation.siteCode();
        roomId = allocation.roomId();
        roomCode = allocation.roomCode();
        allocatedUnit = allocation.allocatedUnit();
        headcount = allocation.headcount();
        allocatedSince = allocation.allocatedSince();
        sourceScenarioId = allocation.sourceScenarioId();
        sourceReference = allocation.sourceReference();
        endedAt = allocation.endedAt();
        endedByScenarioId = allocation.endedByScenarioId();
        metadata = RecordMetadataEmbeddable.from(allocation.metadata());
    }

    SpaceAllocation toDomain() {
        return new SpaceAllocation(id, siteCode, roomId, roomCode, allocatedUnit, headcount, allocatedSince,
                sourceScenarioId, sourceReference, endedAt, endedByScenarioId, metadata.toDomain(recordVersion()));
    }
}
