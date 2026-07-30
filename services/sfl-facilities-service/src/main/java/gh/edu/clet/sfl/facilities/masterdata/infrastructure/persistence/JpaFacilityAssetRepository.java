package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaFacilityAssetRepository extends JpaRepository<FacilityAssetRecord, UUID> {

    /** Backs the duplicate-identifier check: an asset code is unique within its site. */
    Optional<FacilityAssetRecord> findBySiteCodeAndAssetCode(String siteCode, String assetCode);

    @Query("""
            select a from FacilityAssetRecord a
            where (:siteCode is null or a.siteCode = :siteCode)
              and (:roomId is null or a.roomId = :roomId)
              and (:category is null or a.category = :category)
              and (:criticality is null or a.criticality = :criticality)
              and (:operationalStatus is null or a.operationalStatus = :operationalStatus)
            order by a.siteCode asc, a.assetCode asc
            """)
    Page<FacilityAssetRecord> search(
            @Param("siteCode") String siteCode,
            @Param("roomId") UUID roomId,
            @Param("category") AssetCategory category,
            @Param("criticality") AssetCriticality criticality,
            @Param("operationalStatus") AssetOperationalStatus operationalStatus,
            Pageable pageable);

    /**
     * Assets in a space that are not operational — the readiness engine's hot path.
     *
     * <p>Restricted to {@code ACTIVE} records: a decommissioned chiller is not a reason a hall is
     * unusable, and an archived record raising blockers would be a permanent, unfixable one.
     */
    @Query("""
            select a from FacilityAssetRecord a
            where a.roomId = :roomId
              and a.lifecycleStatus = gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus.ACTIVE
              and a.operationalStatus in (
                    gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus.DEGRADED,
                    gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus.UNDER_MAINTENANCE,
                    gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus.OUT_OF_SERVICE)
            order by a.criticality asc, a.assetCode asc
            """)
    List<FacilityAssetRecord> findImpairingByRoom(@Param("roomId") UUID roomId);

    /** Every active asset in a site — the dashboard's asset input. */
    @Query("""
            select a from FacilityAssetRecord a
            where (:siteCode is null or a.siteCode = :siteCode)
              and a.lifecycleStatus = gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus.ACTIVE
            order by a.siteCode asc, a.assetCode asc
            """)
    List<FacilityAssetRecord> findActiveForDashboard(@Param("siteCode") String siteCode);
}
