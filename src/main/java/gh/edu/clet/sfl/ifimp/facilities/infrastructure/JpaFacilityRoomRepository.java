package gh.edu.clet.sfl.ifimp.facilities.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaFacilityRoomRepository extends JpaRepository<FacilityRoomRecord, UUID> {
    List<FacilityRoomRecord> findAllByOrderBySiteCodeAscRoomCodeAsc();

    List<FacilityRoomRecord> findBySiteCodeOrderByRoomCodeAsc(String siteCode);
}
