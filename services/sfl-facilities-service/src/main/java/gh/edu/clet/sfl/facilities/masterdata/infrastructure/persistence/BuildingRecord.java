package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "buildings", schema = "facilities")
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
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected BuildingRecord() {
    }

    private BuildingRecord(Building building) {
        id = building.id();
        siteId = building.siteId();
        siteCode = building.siteCode();
        buildingCode = building.buildingCode();
        name = building.name();
        description = building.description();
        lifecycleStatus = building.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(building.metadata());
    }

    public static BuildingRecord from(Building building) {
        return new BuildingRecord(building);
    }

    public Building toDomain() {
        return new Building(id, siteId, siteCode, buildingCode, name, description, lifecycleStatus,
                metadata.toDomain());
    }
}
