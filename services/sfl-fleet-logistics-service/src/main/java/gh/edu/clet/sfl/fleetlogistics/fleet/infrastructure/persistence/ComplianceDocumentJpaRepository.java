package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    /**
     * Cross-fleet compliance search.
     *
     * <p>Every optional parameter is **cast** in its {@code is null} test, and that cast is load
     * bearing. Hibernate expands a named parameter used twice into two separate JDBC placeholders,
     * so the one inside {@code is null} stands alone — and Postgres cannot infer a type for a
     * parameter it only ever sees compared to null. Without the cast this query answers
     * {@code could not determine data type of parameter $7} the moment {@code expiringBefore} is
     * supplied.
     *
     * <p>This is the same defect that made {@code GET /fleet/audit/records} return 500 on every call
     * before the S168 round. It was rediscovered here by driving the endpoint rather than by reading
     * the code, which is the only way it shows up.
     */
    @Query("""
            select d from ComplianceDocumentEntity d
            where (:allSites = true or d.siteCode in :siteScopes)
              and (cast(:documentType as string) is null or d.documentType = :documentType)
              and (cast(:status as string) is null or d.status = :status)
              and (cast(:expiringBefore as date) is null or d.expiresOn <= :expiringBefore)
            order by d.expiresOn asc, d.id asc
            """)
    List<ComplianceDocumentEntity> search(@Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes,
            @Param("documentType") ComplianceDocumentType documentType,
            @Param("status") ComplianceDocumentStatus status,
            @Param("expiringBefore") LocalDate expiringBefore, Pageable pageable);
}
