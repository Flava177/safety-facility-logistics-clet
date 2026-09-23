package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EvidenceRequestJpaRepository extends JpaRepository<EvidenceRequestJpaEntity, UUID> {

    List<EvidenceRequestJpaEntity> findBySiteCode(String siteCode);
}
