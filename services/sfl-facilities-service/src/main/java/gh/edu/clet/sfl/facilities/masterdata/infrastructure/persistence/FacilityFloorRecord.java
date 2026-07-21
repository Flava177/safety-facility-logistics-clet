package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "facility_floors", schema = "facilities")
public class FacilityFloorRecord {
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
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FacilityFloorRecord() {
    }

    private FacilityFloorRecord(FacilityFloor floor) {
        id = floor.id();
        buildingId = floor.buildingId();
        siteCode = floor.siteCode();
        floorCode = floor.floorCode();
        name = floor.name();
        levelNumber = floor.levelNumber();
        createdAt = floor.createdAt();
    }

    public static FacilityFloorRecord from(FacilityFloor floor) {
        return new FacilityFloorRecord(floor);
    }

    public FacilityFloor toDomain() {
        return new FacilityFloor(id, buildingId, siteCode, floorCode, name, levelNumber, createdAt);
    }
}
