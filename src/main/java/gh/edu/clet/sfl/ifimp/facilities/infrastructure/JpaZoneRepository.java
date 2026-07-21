package gh.edu.clet.sfl.ifimp.facilities.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaZoneRepository extends JpaRepository<ZoneRecord, UUID> {
    List<ZoneRecord> findAllByOrderBySiteCodeAscZoneCodeAsc();

    List<ZoneRecord> findBySiteCodeOrderByZoneCodeAsc(String siteCode);
}
