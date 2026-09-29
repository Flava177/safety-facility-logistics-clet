package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access for one S169 table - see {@code JpaCleaningRepositoryAdapter}. */
public interface JpaCleaningTemplateRepository extends JpaRepository<CleaningChecklistTemplateRecord, UUID> {

    @Query("""
            select t from CleaningChecklistTemplateRecord t
             where t.siteCode = :siteCode and t.spaceType = :spaceType and t.active = true
            """)
    Optional<CleaningChecklistTemplateRecord> findActive(@Param("siteCode") String siteCode,
            @Param("spaceType") SpaceType spaceType);

    @Query("""
            select coalesce(max(t.templateVersion), 0) from CleaningChecklistTemplateRecord t
             where t.siteCode = :siteCode and t.spaceType = :spaceType
            """)
    int latestVersion(@Param("siteCode") String siteCode, @Param("spaceType") SpaceType spaceType);

    @Query("""
            select t from CleaningChecklistTemplateRecord t where t.siteCode = :siteCode
             order by t.spaceType asc, t.templateVersion desc
            """)
    List<CleaningChecklistTemplateRecord> findForSite(@Param("siteCode") String siteCode);
}
