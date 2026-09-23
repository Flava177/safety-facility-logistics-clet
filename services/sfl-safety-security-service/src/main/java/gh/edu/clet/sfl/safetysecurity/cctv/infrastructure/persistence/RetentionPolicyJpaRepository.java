package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RetentionScope;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RetentionPolicyJpaRepository extends JpaRepository<RetentionPolicyJpaEntity, UUID> {

    Optional<RetentionPolicyJpaEntity> findBySiteCodeAndScopeAndScopeRef(String siteCode, RetentionScope scope,
            String scopeRef);
}
