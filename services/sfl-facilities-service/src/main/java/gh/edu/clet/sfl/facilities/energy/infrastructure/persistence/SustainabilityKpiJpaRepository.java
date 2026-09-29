package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SustainabilityKpiJpaRepository extends JpaRepository<SustainabilityKpiRecord, UUID> {

    Optional<SustainabilityKpiRecord> findByKpiKey(String kpiKey);

    @Query("""
            select k from SustainabilityKpiRecord k
             where (:site is null or k.siteCode = :site)
               and (:scope is null or k.scope = :scope)
               and (:utility is null or k.utility = :utility)
               and (:periodType is null or k.periodType = :periodType)
               and k.periodStart >= :from and k.periodStart < :to
             order by k.periodStart desc, k.siteCode, k.utility
            """)
    List<SustainabilityKpiRecord> search(@Param("site") String site, @Param("scope") SustainabilityKpi.KpiScope scope,
            @Param("utility") Utility utility, @Param("periodType") EnergyPeriod.PeriodType periodType,
            @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable page);
}
