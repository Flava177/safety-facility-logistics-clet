package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ComplianceDocumentJpaRepository extends JpaRepository<ComplianceDocumentEntity, UUID> {

    List<ComplianceDocumentEntity> findByVehicleIdOrderByExpiresOnDesc(UUID vehicleId);

    @Query("""
            select d from ComplianceDocumentEntity d
            where d.vehicleId = :vehicleId
              and d.status in (
                  gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus.ACTIVE,
                  gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus.EXPIRING)
            order by d.expiresOn asc
            """)
    List<ComplianceDocumentEntity> findCurrentByVehicle(@Param("vehicleId") UUID vehicleId);

    @Query("""
            select d from ComplianceDocumentEntity d
            where d.vehicleId = :vehicleId
              and d.documentType = :documentType
              and d.status in (
                  gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus.ACTIVE,
                  gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus.EXPIRING)
            """)
    Optional<ComplianceDocumentEntity> findCurrentByVehicleAndType(@Param("vehicleId") UUID vehicleId,
            @Param("documentType") ComplianceDocumentType documentType);

    @Query("""
            select d from ComplianceDocumentEntity d
            where d.expiresOn <= :threshold
              and d.status in (
                  gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus.ACTIVE,
                  gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus.EXPIRING)
            order by d.expiresOn asc
            """)
    List<ComplianceDocumentEntity> findCurrentExpiringOnOrBefore(@Param("threshold") LocalDate threshold);

    @Query("""
            select d from ComplianceDocumentEntity d
            where (:allSites = true or d.siteCode in :siteScopes)
            order by d.expiresOn asc
            """)
    List<ComplianceDocumentEntity> findInScope(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes);
}
