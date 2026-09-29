package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EnergyBudgetJpaRepository extends JpaRepository<EnergyBudgetRecord, UUID> {

    List<EnergyBudgetRecord> findBySiteCodeAndUtilityAndPeriodTypeAndPeriodStart(String siteCode, Utility utility,
            EnergyPeriod.PeriodType periodType, LocalDate periodStart);

    List<EnergyBudgetRecord> findBySiteCodeAndPeriodStartGreaterThanEqualAndPeriodStartLessThan(String siteCode,
            LocalDate from, LocalDate to);
}
