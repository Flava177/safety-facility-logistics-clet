package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
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

@Entity
@Table(name = "facility_floors", schema = "facilities")
public class FacilityFloorRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "building_id", nullable = false)
    private UUID buildingId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "floor_code", nullable = false, length = 60)
    private String floorCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "level_number")
    private Integer levelNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected FacilityFloorRecord() {
    }

    public static FacilityFloorRecord from(FacilityFloor floor) {
        FacilityFloorRecord record = new FacilityFloorRecord();
        record.apply(floor);
        return record;
    }

    /** Copies the aggregate onto this row - deliberately not {@code record_version}; see VersionedRecord. */
    public void apply(FacilityFloor floor) {
        id = floor.id();
        buildingId = floor.buildingId();
        siteCode = floor.siteCode();
        floorCode = floor.floorCode();
        name = floor.name();
        levelNumber = floor.levelNumber();
        lifecycleStatus = floor.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(floor.metadata());
    }

    public FacilityFloor toDomain() {
        return new FacilityFloor(id, buildingId, siteCode, floorCode, name, levelNumber, lifecycleStatus,
                metadata.toDomain(recordVersion()));
    }
}
