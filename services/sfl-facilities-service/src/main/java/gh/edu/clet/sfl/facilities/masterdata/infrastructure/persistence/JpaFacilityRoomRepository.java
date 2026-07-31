package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaFacilityRoomRepository extends JpaRepository<FacilityRoomRecord, UUID> {

    List<FacilityRoomRecord> findAllByOrderBySiteCodeAscRoomCodeAsc();

    List<FacilityRoomRecord> findBySiteCodeOrderByRoomCodeAsc(String siteCode);

    /**
     * Backs the duplicate-identifier check.
     *
     * <p>Uniqueness is per <em>site</em>, matching the constraint V2 declared, not per building. A room
     * code is how an operator refers to a space over a radio; two "HALL-A"s on one site would be
     * ambiguous whichever buildings they sat in.
     */
    Optional<FacilityRoomRecord> findBySiteCodeAndRoomCode(String siteCode, String roomCode);

    List<FacilityRoomRecord> findByFloorIdOrderByRoomCodeAsc(UUID floorId);

    /**
     * The space search behind {@code GET /api/v1/facilities/rooms}.
     *
     * <p>Every filter is optional and null-tolerant. {@code buildingId} reaches through the floor,
     * which is why it is a subquery rather than another derived method.
     */
    @Query("""
            select r from FacilityRoomRecord r
            where (:siteCode is null or r.siteCode = :siteCode)
              and (:floorId is null or r.floorId = :floorId)
              and (:buildingId is null or r.floorId in
                    (select f.id from FacilityFloorRecord f where f.buildingId = :buildingId))
              and (:spaceType is null or r.spaceType = :spaceType)
              and (:readinessStatus is null or r.readinessStatus = :readinessStatus)
              and (:bookable is null or r.bookable = :bookable)
              and (:examinationCapable is null or r.examinationCapable = :examinationCapable)
            order by r.siteCode asc, r.roomCode asc
            """)
    Page<FacilityRoomRecord> search(
            @Param("siteCode") String siteCode,
            @Param("buildingId") UUID buildingId,
            @Param("floorId") UUID floorId,
            @Param("spaceType") SpaceType spaceType,
            @Param("readinessStatus") LocationReadinessStatus readinessStatus,
            @Param("bookable") Boolean bookable,
            @Param("examinationCapable") Boolean examinationCapable,
            Pageable pageable);

    /**
     * Spaces whose readiness has not been reassessed since {@code threshold}.
     *
     * <p>A null {@code readinessUpdatedAt} counts as stale. A space that has never been assessed is
     * the most stale thing on the estate, and treating "never" as "not yet due" is how an examination
     * hall goes a year without a look.
     */
    @Query("""
            select r from FacilityRoomRecord r
            where (:siteCode is null or r.siteCode = :siteCode)
              and r.lifecycleStatus = gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus.ACTIVE
              and (r.readinessUpdatedAt is null or r.readinessUpdatedAt < :threshold)
            order by r.readinessUpdatedAt asc
            """)
    List<FacilityRoomRecord> findStaleReadiness(@Param("siteCode") String siteCode,
            @Param("threshold") Instant threshold);

    /** Every active space in a site — the dashboard's input, counted in memory rather than four times. */
    @Query("""
            select r from FacilityRoomRecord r
            where (:siteCode is null or r.siteCode = :siteCode)
              and r.lifecycleStatus = gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus.ACTIVE
            order by r.siteCode asc, r.roomCode asc
            """)
    List<FacilityRoomRecord> findActiveForDashboard(@Param("siteCode") String siteCode);
}
