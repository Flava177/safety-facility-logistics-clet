package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReadinessAssessmentJpaRepository extends JpaRepository<ReadinessAssessmentEntity, UUID> {

    // `items` is LAZY on the entity (see ReadinessAssessmentEntity). A single row fetches it with an
    // @EntityGraph; a page of rows fetches ids first (see below) rather than joining the collection
    // directly, because JOIN FETCH combined with a Pageable makes Hibernate page the join in memory -
    // fine for one row, wrong for a page of them.
    @EntityGraph(attributePaths = "items")
    Optional<ReadinessAssessmentEntity> findById(UUID id);

    @Query("select a.id from ReadinessAssessmentEntity a where a.roomId = :roomId order by a.assessedAt desc")
    List<UUID> findIdsByRoomIdOrderByAssessedAtDesc(@Param("roomId") UUID roomId, Pageable pageable);

    @Query(value = """
            select a.id from ReadinessAssessmentEntity a
            where (:siteCode is null or a.siteCode = :siteCode)
              and (:roomId is null or a.roomId = :roomId)
            order by a.assessedAt desc
            """,
            countQuery = """
            select count(a) from ReadinessAssessmentEntity a
            where (:siteCode is null or a.siteCode = :siteCode)
              and (:roomId is null or a.roomId = :roomId)
            """)
    Page<UUID> searchIds(@Param("siteCode") String siteCode, @Param("roomId") UUID roomId, Pageable pageable);

    /** Loaded with items, in no particular order - the caller re-applies the id order it asked for. */
    @EntityGraph(attributePaths = "items")
    List<ReadinessAssessmentEntity> findByIdIn(Collection<UUID> ids);
}
