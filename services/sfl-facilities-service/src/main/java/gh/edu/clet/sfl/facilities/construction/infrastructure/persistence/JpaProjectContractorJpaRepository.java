package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProjectContractorJpaRepository extends JpaRepository<ProjectContractorRecord, UUID> {

    List<ProjectContractorRecord> findByProjectId(UUID projectId);
}
