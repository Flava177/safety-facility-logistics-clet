package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IntrusionSignalJpaRepository extends JpaRepository<IntrusionSignalJpaEntity, UUID> {

    Optional<IntrusionSignalJpaEntity> findBySourceAndExternalEventId(String source, String externalEventId);
}
