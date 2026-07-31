package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaBookableResourceJpaRepository extends JpaRepository<BookableResourceRecord, UUID> {

    Optional<BookableResourceRecord> findBySiteCodeAndResourceCode(String siteCode, String resourceCode);

    List<BookableResourceRecord> findByIdIn(Collection<UUID> ids);

    @Query("""
            select r from BookableResourceRecord r
            where (:siteCode is null or r.siteCode = :siteCode)
              and (:category is null or r.category = :category)
            order by r.resourceCode asc
            """)
    List<BookableResourceRecord> search(@Param("siteCode") String siteCode,
            @Param("category") ResourceCategory category);
}
