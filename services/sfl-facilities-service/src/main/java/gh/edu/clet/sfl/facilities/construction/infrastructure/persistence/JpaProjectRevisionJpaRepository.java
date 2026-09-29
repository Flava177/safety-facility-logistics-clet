package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Insert-only: V21's trigger refuses an UPDATE or DELETE, so no method here ever issues one. */
public interface JpaProjectRevisionJpaRepository extends JpaRepository<ProjectRevisionRecord, UUID> {

    List<ProjectRevisionRecord> findByProjectIdOrderByRevisedAtAsc(UUID projectId);
}
