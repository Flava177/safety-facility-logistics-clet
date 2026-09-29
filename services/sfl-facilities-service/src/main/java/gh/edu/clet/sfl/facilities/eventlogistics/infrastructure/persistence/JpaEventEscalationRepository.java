package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventEscalationRepository extends JpaRepository<EventReadinessEscalationRecord, UUID> {

    @Query("select e from EventReadinessEscalationRecord e where e.setupTaskId = :taskId order by e.escalatedAt asc")
    List<EventReadinessEscalationRecord> findForTask(@Param("taskId") UUID taskId);
}
