package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningFeedbackRepository extends JpaRepository<CleaningFeedbackRecord, UUID> {

    @Query("""
            select count(f) > 0 from CleaningFeedbackRecord f
             where f.taskId = :taskId and f.submittedBy = :submittedBy
            """)
    boolean exists(@Param("taskId") UUID taskId, @Param("submittedBy") String submittedBy);

    @Query("select f from CleaningFeedbackRecord f where f.taskId = :taskId order by f.submittedAt asc")
    List<CleaningFeedbackRecord> findForTask(@Param("taskId") UUID taskId);

    @Query("""
            select f from CleaningFeedbackRecord f
             where f.siteCode = :siteCode and f.submittedAt >= :from and f.submittedAt < :to
             order by f.submittedAt asc
            """)
    List<CleaningFeedbackRecord> findForSite(@Param("siteCode") String siteCode, @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select f from CleaningFeedbackRecord f
             where f.vendorId = :vendorId and f.submittedAt >= :from and f.submittedAt < :to
             order by f.submittedAt asc
            """)
    List<CleaningFeedbackRecord> findForVendor(@Param("vendorId") UUID vendorId, @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select count(f) from CleaningFeedbackRecord f
             where f.roomId = :roomId and f.rating <= :maxRating and f.submittedAt >= :since
            """)
    long countLowForRoom(@Param("roomId") UUID roomId, @Param("maxRating") int maxRating,
            @Param("since") Instant since);

    @Query("""
            select count(f) from CleaningFeedbackRecord f
             where f.vendorId = :vendorId and f.rating <= :maxRating and f.submittedAt >= :since
            """)
    long countLowForVendor(@Param("vendorId") UUID vendorId, @Param("maxRating") int maxRating,
            @Param("since") Instant since);
}
