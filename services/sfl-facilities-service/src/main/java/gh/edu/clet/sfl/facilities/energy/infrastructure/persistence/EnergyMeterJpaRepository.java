package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.MeterStatus;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EnergyMeterJpaRepository extends JpaRepository<EnergyMeterRecord, UUID> {

    Optional<EnergyMeterRecord> findBySiteCodeAndMeterCode(String siteCode, String meterCode);

    Optional<EnergyMeterRecord> findByAvampAssetId(String avampAssetId);

    Optional<EnergyMeterRecord> findByVendorMeterRef(String vendorMeterRef);

    @Query("""
            select m from EnergyMeterRecord m
             where (:site is null or m.siteCode = :site)
               and (:utility is null or m.utility = :utility)
               and (:status is null or m.status = :status)
             order by m.siteCode, m.meterCode
            """)
    List<EnergyMeterRecord> search(@Param("site") String site, @Param("utility") Utility utility,
            @Param("status") MeterStatus status);
}
