package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaFacilityFloorRepository extends JpaRepository<FacilityFloorRecord, UUID> {

    List<FacilityFloorRecord> findByBuildingIdOrderByLevelNumberAscFloorCodeAsc(UUID buildingId);

    /** Backs the duplicate-identifier check: a floor code is unique within its building. */
    Optional<FacilityFloorRecord> findByBuildingIdAndFloorCode(UUID buildingId, String floorCode);
}
