package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EnergyDailyConsumptionJpaRepository extends JpaRepository<EnergyDailyConsumptionRecord, UUID> {

    Optional<EnergyDailyConsumptionRecord> findByMeterIdAndDay(UUID meterId, LocalDate day);

    List<EnergyDailyConsumptionRecord> findByMeterIdAndDayGreaterThanEqualAndDayLessThanOrderByDayAsc(UUID meterId,
            LocalDate from, LocalDate to);

    @Query("""
            select d from EnergyDailyConsumptionRecord d
             where (:site is null or d.siteCode = :site)
               and (:utility is null or d.utility = :utility)
               and d.day >= :from and d.day < :to
             order by d.day
            """)
    List<EnergyDailyConsumptionRecord> search(@Param("site") String site, @Param("utility") Utility utility,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
