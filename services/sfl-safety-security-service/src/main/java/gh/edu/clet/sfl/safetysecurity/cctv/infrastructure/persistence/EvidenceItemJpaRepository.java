package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItemStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EvidenceItemJpaRepository extends JpaRepository<EvidenceItemJpaEntity, UUID> {

    List<EvidenceItemJpaEntity> findByRequestId(UUID requestId);

    List<EvidenceItemJpaEntity> findByStatus(EvidenceItemStatus status);
}
