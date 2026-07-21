package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverEligibilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DriverProfileJpaRepository extends JpaRepository<DriverProfileEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DriverProfileEntity d where d.id = :id")
    Optional<DriverProfileEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select d from DriverProfileEntity d
            where d.siteCode = :siteCode
              and upper(d.staffReference) = upper(:staffReference)
              and d.lifecycleStatus <> gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus.ARCHIVED
            """)
    Optional<DriverProfileEntity> findActiveByStaffReference(@Param("siteCode") String siteCode,
            @Param("staffReference") String staffReference);

    @Query("""
            select d from DriverProfileEntity d
            where d.siteCode = :siteCode
              and upper(d.licenceNumber) = upper(:licenceNumber)
              and d.lifecycleStatus <> gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus.ARCHIVED
            """)
    Optional<DriverProfileEntity> findActiveByLicenceNumber(@Param("siteCode") String siteCode,
            @Param("licenceNumber") String licenceNumber);

    @Query("""
            select d from DriverProfileEntity d
            where (:allSites = true or d.siteCode in :siteScopes)
              and (:siteCode is null or d.siteCode = :siteCode)
              and (:lifecycleStatus is null or d.lifecycleStatus = :lifecycleStatus)
              and (:eligibilityStatus is null or d.eligibilityStatus = :eligibilityStatus)
              and (:responsibleUnit is null or upper(d.responsibleUnit) = upper(:responsibleUnit))
              and (:licenceExpiringBefore is null or d.licenceExpiresOn <= :licenceExpiringBefore)
              and (:search is null
                   or upper(d.displayName) like upper(concat('%', :search, '%'))
                   or upper(d.staffReference) like upper(concat('%', :search, '%')))
            """)
    Page<DriverProfileEntity> search(
            @Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes,
            @Param("siteCode") String siteCode,
            @Param("lifecycleStatus") DriverLifecycleStatus lifecycleStatus,
            @Param("eligibilityStatus") DriverEligibilityStatus eligibilityStatus,
            @Param("responsibleUnit") String responsibleUnit,
            @Param("licenceExpiringBefore") LocalDate licenceExpiringBefore,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select d from DriverProfileEntity d
            where (:allSites = true or d.siteCode in :siteScopes)
            order by d.siteCode asc, d.displayName asc
            """)
    List<DriverProfileEntity> findAllInScope(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes);

    @Query("""
            select d from DriverProfileEntity d
            where d.lifecycleStatus <> gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus.ARCHIVED
              and (d.licenceExpiresOn <= :threshold
                   or (d.medicalClearanceExpiresOn is not null and d.medicalClearanceExpiresOn <= :threshold))
            order by d.licenceExpiresOn asc
            """)
    List<DriverProfileEntity> findExpiringOnOrBefore(@Param("threshold") LocalDate threshold);
}
