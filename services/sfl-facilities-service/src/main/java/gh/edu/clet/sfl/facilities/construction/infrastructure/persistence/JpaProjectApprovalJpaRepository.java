package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProjectApprovalJpaRepository extends JpaRepository<ProjectApprovalRecord, UUID> {

    List<ProjectApprovalRecord> findByProjectIdOrderByApprovedAtAsc(UUID projectId);
}
