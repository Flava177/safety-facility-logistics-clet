package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningChecklistItemRepository extends JpaRepository<CleaningTaskChecklistItemRecord, UUID> {

    @Query("select i from CleaningTaskChecklistItemRecord i where i.taskId = :taskId order by i.sequence asc")
    List<CleaningTaskChecklistItemRecord> findForTask(@Param("taskId") UUID taskId);
}
