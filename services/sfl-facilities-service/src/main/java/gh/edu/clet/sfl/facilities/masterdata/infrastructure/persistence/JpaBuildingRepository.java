package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaBuildingRepository extends JpaRepository<BuildingRecord, UUID> {
    List<BuildingRecord> findAllByOrderBySiteCodeAscBuildingCodeAsc();

    List<BuildingRecord> findBySiteCodeOrderByBuildingCodeAsc(String siteCode);
}
