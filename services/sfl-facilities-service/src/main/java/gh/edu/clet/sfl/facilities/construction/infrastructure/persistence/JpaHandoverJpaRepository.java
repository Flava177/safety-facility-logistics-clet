package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaHandoverJpaRepository extends JpaRepository<HandoverRecord, UUID> {

    List<HandoverRecord> findByProjectIdOrderByRecordedAtAsc(UUID projectId);
}
