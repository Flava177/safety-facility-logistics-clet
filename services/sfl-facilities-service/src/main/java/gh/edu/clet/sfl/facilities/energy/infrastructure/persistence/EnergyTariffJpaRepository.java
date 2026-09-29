package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EnergyTariffJpaRepository extends JpaRepository<EnergyTariffRecord, UUID> {

    List<EnergyTariffRecord> findBySiteCodeAndUtility(String siteCode, Utility utility);
}
