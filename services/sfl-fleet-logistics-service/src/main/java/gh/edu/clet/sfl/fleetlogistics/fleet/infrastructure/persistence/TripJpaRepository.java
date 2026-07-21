package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TripJpaRepository extends JpaRepository<TripEntity, UUID> {

    /**
     * Live trips overlapping {@code [from, to)} on this vehicle.
     *
     * <p>Half-open comparison: {@code plannedStart < to and plannedEnd > from}. Back-to-back trips do
     * not conflict, which matches how the exclusion constraint is defined and how dispatchers schedule.
     */
    @Query("""
            select t from TripEntity t
            where t.vehicleId = :vehicleId
              and t.status in (gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.PLANNED,
                               gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.ASSIGNED,
                               gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.IN_PROGRESS,
                               gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.ON_HOLD)
              and (:excludingTripId is null or t.id <> :excludingTripId)
              and t.plannedStart < :to
              and t.plannedEnd > :from
            """)
    List<TripEntity> findVehicleConflicts(@Param("vehicleId") UUID vehicleId, @Param("from") Instant from,
            @Param("to") Instant to, @Param("excludingTripId") UUID excludingTripId);

    @Query("""
            select t from TripEntity t
            where t.driverId = :driverId
              and t.status in (gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.PLANNED,
                               gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.ASSIGNED,
                               gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.IN_PROGRESS,
                               gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.ON_HOLD)
              and (:excludingTripId is null or t.id <> :excludingTripId)
              and t.plannedStart < :to
              and t.plannedEnd > :from
            """)
    List<TripEntity> findDriverConflicts(@Param("driverId") UUID driverId, @Param("from") Instant from,
            @Param("to") Instant to, @Param("excludingTripId") UUID excludingTripId);

    @Query("""
            select t from TripEntity t
            where (:allSites = true or t.siteCode in :siteScopes)
              and (:siteCode is null or t.siteCode = :siteCode)
              and (:status is null or t.status = :status)
              and (:vehicleId is null or t.vehicleId = :vehicleId)
              and (:driverId is null or t.driverId = :driverId)
              and (:operatingMode is null or t.operatingMode = :operatingMode)
              and (:from is null or t.plannedEnd >= :from)
              and (:to is null or t.plannedStart <= :to)
            """)
    Page<TripEntity> search(
            @Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes,
            @Param("siteCode") String siteCode,
            @Param("status") TripStatus status,
            @Param("vehicleId") UUID vehicleId,
            @Param("driverId") UUID driverId,
            @Param("operatingMode") OperatingMode operatingMode,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            select t from TripEntity t
            where (:allSites = true or t.siteCode in :siteScopes)
            order by t.plannedStart desc
            """)
    List<TripEntity> findAllInScope(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes);

    @Query("""
            select t from TripEntity t
            where t.status in (gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.ASSIGNED,
                               gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.IN_PROGRESS,
                               gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus.ON_HOLD)
              and t.plannedEnd < :threshold
            order by t.plannedEnd asc
            """)
    List<TripEntity> findLiveTripsEndingBefore(@Param("threshold") Instant threshold);
}
