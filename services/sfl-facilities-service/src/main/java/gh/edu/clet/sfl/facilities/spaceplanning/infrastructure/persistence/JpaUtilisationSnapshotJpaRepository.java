package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaUtilisationSnapshotJpaRepository extends JpaRepository<UtilisationSnapshotRecord, UUID> {

    Optional<UtilisationSnapshotRecord> findByRoomIdAndPeriodStartAndPeriodEnd(UUID roomId, Instant periodStart,
            Instant periodEnd);

    @Query("""
            select s from UtilisationSnapshotRecord s
            where s.roomId = :roomId and s.periodEnd <= :upTo
            order by s.periodEnd desc
            """)
    List<UtilisationSnapshotRecord> recent(@Param("roomId") UUID roomId, @Param("upTo") Instant upTo, Pageable page);

    @Query("""
            select s from UtilisationSnapshotRecord s
            where s.siteCode = :siteCode and s.periodEnd = (
                select max(latest.periodEnd) from UtilisationSnapshotRecord latest where latest.siteCode = :siteCode)
            order by s.roomCode asc
            """)
    List<UtilisationSnapshotRecord> latest(@Param("siteCode") String siteCode);
}
