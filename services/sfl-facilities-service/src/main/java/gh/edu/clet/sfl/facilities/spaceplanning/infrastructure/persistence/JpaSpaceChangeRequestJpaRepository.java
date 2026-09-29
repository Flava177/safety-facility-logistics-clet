package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaSpaceChangeRequestJpaRepository extends JpaRepository<SpaceChangeRequestRecord, UUID> {

    /** Site-filtered in SQL, and narrowed to one requester's own when {@code requestedBy} is set. */
    @Query("""
            select r from SpaceChangeRequestRecord r
            where r.siteCode = :siteCode
              and (:requestedBy is null or r.requestedBy = :requestedBy)
              and (:status is null or r.status = :status)
            order by r.requestedAt desc
            """)
    List<SpaceChangeRequestRecord> search(@Param("siteCode") String siteCode, @Param("requestedBy") String requestedBy,
            @Param("status") SpaceChangeRequest.Status status);
}
