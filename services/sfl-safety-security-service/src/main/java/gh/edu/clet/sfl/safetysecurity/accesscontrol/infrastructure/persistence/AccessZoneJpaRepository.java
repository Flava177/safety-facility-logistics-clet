package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessZoneJpaRepository extends JpaRepository<AccessZoneJpaEntity, UUID> {

    Optional<AccessZoneJpaEntity> findBySiteCodeAndZoneCode(String siteCode, String zoneCode);

    List<AccessZoneJpaEntity> findBySiteCode(String siteCode);
}
