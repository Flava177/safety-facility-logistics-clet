package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventReconciliationRepository extends JpaRepository<EventReconciliationLineRecord, UUID> {

    @Query("select l from EventReconciliationLineRecord l where l.setupTaskId = :taskId order by l.recordedAt asc")
    List<EventReconciliationLineRecord> findForTask(@Param("taskId") UUID taskId);
}
