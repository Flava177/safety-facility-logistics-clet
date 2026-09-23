package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.OverrideStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessOverrideJpaRepository extends JpaRepository<AccessOverrideJpaEntity, UUID> {

    List<AccessOverrideJpaEntity> findBySiteCodeAndStatus(String siteCode, OverrideStatus status);

    List<AccessOverrideJpaEntity> findByStatus(OverrideStatus status);
}
