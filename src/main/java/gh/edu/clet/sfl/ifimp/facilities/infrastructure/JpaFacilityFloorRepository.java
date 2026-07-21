package gh.edu.clet.sfl.ifimp.facilities.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaFacilityFloorRepository extends JpaRepository<FacilityFloorRecord, UUID> {
    List<FacilityFloorRecord> findByBuildingIdOrderByLevelNumberAscFloorCodeAsc(UUID buildingId);
}
