package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VehicleLocationSnapshotJpaRepository extends JpaRepository<VehicleLocationSnapshotEntity, UUID> {

    Optional<VehicleLocationSnapshotEntity> findFirstByVehicleIdOrderByRecordedAtDescIdDesc(UUID vehicleId);

    @Query("""
            select location from VehicleLocationSnapshotEntity location
             where (:allSites = true or location.siteCode in :siteScopes)
             order by location.recordedAt desc, location.id desc
            """)
    List<VehicleLocationSnapshotEntity> findRecentInScope(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes, Pageable pageable);
}
