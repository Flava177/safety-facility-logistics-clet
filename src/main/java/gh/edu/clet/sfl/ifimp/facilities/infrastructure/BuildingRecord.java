package gh.edu.clet.sfl.ifimp.facilities.infrastructure;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.ifimp.facilities.domain.Building;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "buildings", schema = "ifimp")
public class BuildingRecord {
    @Id
    private UUID id;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "building_code", nullable = false, length = 60)
    private String buildingCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 1000)
    private String description;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BuildingRecord() {
    }

    private BuildingRecord(Building building) {
        id = building.id();
        siteId = building.siteId();
        siteCode = building.siteCode();
        buildingCode = building.buildingCode();
        name = building.name();
        description = building.description();
        createdAt = building.createdAt();
    }

    public static BuildingRecord from(Building building) {
        return new BuildingRecord(building);
    }

    public Building toDomain() {
        return new Building(id, siteId, siteCode, buildingCode, name, description, createdAt);
    }
}
