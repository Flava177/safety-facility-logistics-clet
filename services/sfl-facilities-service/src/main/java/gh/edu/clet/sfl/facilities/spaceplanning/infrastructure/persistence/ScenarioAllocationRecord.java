package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ComplianceStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioAllocation;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA mapping for {@link ScenarioAllocation}. */
@Entity
@Table(name = "space_scenario_allocations", schema = "facilities")
public class ScenarioAllocationRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Column(name = "allocated_unit", length = 200)
    private String allocatedUnit;
    @Column(nullable = false)
    private int headcount;
    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_status", nullable = false, length = 20)
    private ComplianceStatus complianceStatus;
    @Column(name = "compliance_detail", length = 2000)
    private String complianceDetail;
    @Column(name = "standard_id")
    private UUID standardId;
    @Column(name = "standard_version")
    private Integer standardVersion;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ScenarioAllocationRecord() {
    }

    void apply(ScenarioAllocation line) {
        id = line.id();
        scenarioId = line.scenarioId();
        siteCode = line.siteCode();
        roomId = line.roomId();
        roomCode = line.roomCode();
        allocatedUnit = line.allocatedUnit();
        headcount = line.headcount();
        complianceStatus = line.complianceStatus();
        complianceDetail = line.complianceDetail();
        standardId = line.standardId();
        standardVersion = line.standardVersion();
        metadata = RecordMetadataEmbeddable.from(line.metadata());
    }

    ScenarioAllocation toDomain() {
        return new ScenarioAllocation(id, scenarioId, siteCode, roomId, roomCode, allocatedUnit, headcount,
                complianceStatus, complianceDetail, standardId, standardVersion, metadata.toDomain(recordVersion()));
    }
}
