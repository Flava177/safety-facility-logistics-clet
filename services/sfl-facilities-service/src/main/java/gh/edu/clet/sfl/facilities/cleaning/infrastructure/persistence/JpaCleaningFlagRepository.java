package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.FlagSubjectType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningFlagRepository extends JpaRepository<CleaningLowRatingFlagRecord, UUID> {

    @Query("""
            select f from CleaningLowRatingFlagRecord f
             where f.siteCode = :siteCode and f.subjectType = :subjectType and f.subjectId = :subjectId
               and f.status = 'OPEN'
            """)
    Optional<CleaningLowRatingFlagRecord> findOpen(@Param("siteCode") String siteCode,
            @Param("subjectType") FlagSubjectType subjectType, @Param("subjectId") UUID subjectId);

    @Query("""
            select f from CleaningLowRatingFlagRecord f
             where f.siteCode = :siteCode and (:status is null or f.status = :status)
             order by f.flaggedAt desc
            """)
    List<CleaningLowRatingFlagRecord> findForSite(@Param("siteCode") String siteCode,
            @Param("status") String status);
}
