package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverrideStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DisarmOverrideJpaRepository extends JpaRepository<DisarmOverrideJpaEntity, UUID> {

    List<DisarmOverrideJpaEntity> findBySiteCodeAndStatus(String siteCode, DisarmOverrideStatus status);

    List<DisarmOverrideJpaEntity> findByStatus(DisarmOverrideStatus status);
}
