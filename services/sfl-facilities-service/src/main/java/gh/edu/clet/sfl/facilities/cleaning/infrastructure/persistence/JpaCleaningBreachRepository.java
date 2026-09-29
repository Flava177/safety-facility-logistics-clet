package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreachType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningBreachRepository extends JpaRepository<CleaningSlaBreachRecord, UUID> {

    @Query("""
            select count(b) > 0 from CleaningSlaBreachRecord b
             where b.taskId = :taskId and b.breachType = :type
            """)
    boolean exists(@Param("taskId") UUID taskId, @Param("type") SlaBreachType type);

    @Query("select b from CleaningSlaBreachRecord b where b.taskId = :taskId order by b.recordedAt asc")
    List<CleaningSlaBreachRecord> findForTask(@Param("taskId") UUID taskId);

    @Query("""
            select b from CleaningSlaBreachRecord b
             where b.vendorId = :vendorId and b.recordedAt >= :from and b.recordedAt < :to
             order by b.recordedAt desc
            """)
    List<CleaningSlaBreachRecord> findForVendor(@Param("vendorId") UUID vendorId, @Param("from") Instant from,
            @Param("to") Instant to);
}
