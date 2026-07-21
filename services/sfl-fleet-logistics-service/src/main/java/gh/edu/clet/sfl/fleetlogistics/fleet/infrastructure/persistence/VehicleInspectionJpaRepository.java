package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VehicleInspectionJpaRepository extends JpaRepository<VehicleInspectionEntity, UUID> {

    Optional<VehicleInspectionEntity> findFirstByVehicleIdOrderByPerformedAtDescIdDesc(UUID vehicleId);

    List<VehicleInspectionEntity> findByVehicleIdOrderByPerformedAtDescIdDesc(UUID vehicleId);

    List<VehicleInspectionEntity> findByTripIdOrderByPerformedAtAsc(UUID tripId);

    /** One row per vehicle: the newest inspection, which is the one readiness cares about. */
    @Query("""
            select i from VehicleInspectionEntity i
            where (:allSites = true or i.siteCode in :siteScopes)
              and i.id = (
                  select i2.id from VehicleInspectionEntity i2
                  where i2.vehicleId = i.vehicleId
                  order by i2.performedAt desc, i2.id desc
                  limit 1)
            """)
    List<VehicleInspectionEntity> findLatestInScope(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes);

    @Query("""
            select i from VehicleInspectionEntity i
            where (:allSites = true or i.siteCode in :siteScopes)
              and i.result = gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionResult.FAILED
              and i.performedAt >= :from
            order by i.performedAt desc
            """)
    List<VehicleInspectionEntity> findFailuresSince(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes, @Param("from") Instant from);
}
