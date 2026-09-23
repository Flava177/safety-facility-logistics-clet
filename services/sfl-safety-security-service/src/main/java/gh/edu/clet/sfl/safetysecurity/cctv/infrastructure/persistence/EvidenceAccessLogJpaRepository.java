package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EvidenceAccessLogJpaRepository extends JpaRepository<EvidenceAccessLogJpaEntity, UUID> {

    List<EvidenceAccessLogJpaEntity> findByEvidenceItemIdOrderByOccurredAtDesc(UUID evidenceItemId);
}
