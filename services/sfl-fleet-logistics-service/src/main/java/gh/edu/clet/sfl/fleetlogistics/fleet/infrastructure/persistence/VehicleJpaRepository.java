package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VehicleJpaRepository extends JpaRepository<VehicleEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VehicleEntity v where v.id = :id")
    Optional<VehicleEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select v from VehicleEntity v
            where v.siteCode = :siteCode
              and upper(v.registrationNumber) = upper(:registrationNumber)
              and v.lifecycleStatus <> gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus.ARCHIVED
            """)
    Optional<VehicleEntity> findActiveByRegistration(@Param("siteCode") String siteCode,
            @Param("registrationNumber") String registrationNumber);

    @Query("""
            select v from VehicleEntity v
            where v.siteCode = :siteCode
              and upper(v.vin) = upper(:vin)
              and v.lifecycleStatus <> gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus.ARCHIVED
            """)
    Optional<VehicleEntity> findActiveByVin(@Param("siteCode") String siteCode, @Param("vin") String vin);

    @Query("""
            select v from VehicleEntity v
            where (:allSites = true or v.siteCode in :siteScopes)
              and (:siteCode is null or v.siteCode = :siteCode)
              and (:lifecycleStatus is null or v.lifecycleStatus = :lifecycleStatus)
              and (:serviceStatus is null or v.serviceStatus = :serviceStatus)
              and (:availabilityStatus is null or v.availabilityStatus = :availabilityStatus)
              and (:category is null or v.category = :category)
              and (:responsibleUnit is null or upper(v.responsibleUnit) = upper(:responsibleUnit))
              and (:registrationNumberContains is null
                   or upper(v.registrationNumber) like upper(concat('%', :registrationNumberContains, '%')))
            """)
    Page<VehicleEntity> search(
            @Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes,
            @Param("siteCode") String siteCode,
            @Param("lifecycleStatus")
            gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus lifecycleStatus,
            @Param("serviceStatus")
            gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus serviceStatus,
            @Param("availabilityStatus")
            gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleAvailabilityStatus availabilityStatus,
            @Param("category") gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory category,
            @Param("responsibleUnit") String responsibleUnit,
            @Param("registrationNumberContains") String registrationNumberContains,
            Pageable pageable);

    @Query("""
            select v from VehicleEntity v
            where (:allSites = true or v.siteCode in :siteScopes)
            order by v.siteCode asc, v.registrationNumber asc
            """)
    List<VehicleEntity> findAllInScope(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes);
}
