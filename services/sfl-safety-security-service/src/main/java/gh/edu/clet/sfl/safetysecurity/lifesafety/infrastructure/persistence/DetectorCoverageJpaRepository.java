package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectorCoverageJpaRepository extends JpaRepository<DetectorCoverageJpaEntity, UUID> {

    List<DetectorCoverageJpaEntity> findBySiteCode(String siteCode);

    @org.springframework.data.jpa.repository.Query("select distinct e.siteCode from DetectorCoverageJpaEntity e")
    List<String> findDistinctSiteCodes();
}
