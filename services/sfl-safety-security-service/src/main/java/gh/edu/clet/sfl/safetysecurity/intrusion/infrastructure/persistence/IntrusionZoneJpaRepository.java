package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IntrusionZoneJpaRepository extends JpaRepository<IntrusionZoneJpaEntity, UUID> {

    Optional<IntrusionZoneJpaEntity> findBySiteCodeAndZoneCode(String siteCode, String zoneCode);

    List<IntrusionZoneJpaEntity> findBySiteCode(String siteCode);
}
