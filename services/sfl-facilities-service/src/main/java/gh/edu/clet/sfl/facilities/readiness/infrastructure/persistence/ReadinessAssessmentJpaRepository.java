package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReadinessAssessmentJpaRepository extends JpaRepository<ReadinessAssessmentEntity, UUID> {

    List<ReadinessAssessmentEntity> findByRoomIdOrderByAssessedAtDesc(UUID roomId, Pageable pageable);

    @Query("""
            select a from ReadinessAssessmentEntity a
            where (:siteCode is null or a.siteCode = :siteCode)
              and (:roomId is null or a.roomId = :roomId)
            order by a.assessedAt desc
            """)
    List<ReadinessAssessmentEntity> search(@Param("siteCode") String siteCode, @Param("roomId") UUID roomId,
            Pageable pageable);
}
