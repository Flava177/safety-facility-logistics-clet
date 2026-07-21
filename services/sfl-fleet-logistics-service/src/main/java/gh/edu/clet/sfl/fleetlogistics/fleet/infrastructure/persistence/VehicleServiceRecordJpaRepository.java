package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VehicleServiceRecordJpaRepository extends JpaRepository<VehicleServiceRecordEntity, UUID> {

    List<VehicleServiceRecordEntity> findByVehicleIdOrderByPerformedOnDescIdDesc(UUID vehicleId);

    Optional<VehicleServiceRecordEntity> findFirstByVehicleIdOrderByPerformedOnDescIdDesc(UUID vehicleId);

    /**
     * The newest service record per vehicle in scope.
     *
     * <p>Correlated so the sweep and the dashboard read one row per vehicle instead of loading the whole
     * history and discarding most of it.
     */
    @Query("""
            select s from VehicleServiceRecordEntity s
            where (:allSites = true or s.siteCode in :siteScopes)
              and s.id = (
                  select s2.id from VehicleServiceRecordEntity s2
                  where s2.vehicleId = s.vehicleId
                  order by s2.performedOn desc, s2.id desc
                  limit 1)
            """)
    List<VehicleServiceRecordEntity> findLatestInScope(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes);
}
