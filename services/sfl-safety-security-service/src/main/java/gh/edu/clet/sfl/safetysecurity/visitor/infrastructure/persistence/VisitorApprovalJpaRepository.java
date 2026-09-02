package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VisitorApprovalJpaRepository extends JpaRepository<VisitorApprovalJpaEntity, UUID> {

    List<VisitorApprovalJpaEntity> findByVisitIdOrderByDecidedAtAsc(UUID visitId);
}
