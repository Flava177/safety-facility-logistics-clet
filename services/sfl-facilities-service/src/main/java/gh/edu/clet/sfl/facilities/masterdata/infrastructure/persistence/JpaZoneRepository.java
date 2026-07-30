package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaZoneRepository extends JpaRepository<ZoneRecord, UUID> {

    List<ZoneRecord> findAllByOrderBySiteCodeAscZoneCodeAsc();

    List<ZoneRecord> findBySiteCodeOrderByZoneCodeAsc(String siteCode);

    /** Backs the duplicate-identifier check: a zone code is unique within its site. */
    Optional<ZoneRecord> findBySiteCodeAndZoneCode(String siteCode, String zoneCode);
}
