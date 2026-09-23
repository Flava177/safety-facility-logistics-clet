package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ProvisioningStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessProvisioningJpaRepository extends JpaRepository<AccessProvisioningJpaEntity, UUID> {

    List<AccessProvisioningJpaEntity> findBySiteCodeAndPersonRef(String siteCode, String personRef);

    List<AccessProvisioningJpaEntity> findBySiteCodeAndStatus(String siteCode, ProvisioningStatus status);
}
