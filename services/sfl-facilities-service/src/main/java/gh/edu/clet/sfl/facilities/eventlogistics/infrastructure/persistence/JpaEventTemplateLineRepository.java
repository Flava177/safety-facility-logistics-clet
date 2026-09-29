package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventTemplateLineRepository extends JpaRepository<EventTemplateLineRecord, UUID> {

    @Query("""
            select t from EventTemplateLineRecord t
            where t.siteCode = :siteCode and t.eventCategory = :category and t.resourceType = :type
            """)
    Optional<EventTemplateLineRecord> findLine(@Param("siteCode") String siteCode, @Param("category") String category,
            @Param("type") EventResourceType type);

    @Query("""
            select t from EventTemplateLineRecord t
            where t.siteCode = :siteCode and (:category is null or t.eventCategory = :category)
            order by t.eventCategory asc, t.resourceType asc
            """)
    List<EventTemplateLineRecord> findLines(@Param("siteCode") String siteCode, @Param("category") String category);
}
