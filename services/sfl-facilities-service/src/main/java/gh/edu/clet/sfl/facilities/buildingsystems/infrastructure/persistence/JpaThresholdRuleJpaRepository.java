package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaThresholdRuleJpaRepository extends JpaRepository<ThresholdRuleRecord, UUID> {

    Optional<ThresholdRuleRecord> findByRuleIdAndSupersededAtIsNull(UUID ruleId);

    List<ThresholdRuleRecord> findByRuleIdOrderByRuleVersion(UUID ruleId);

    List<ThresholdRuleRecord> findBySiteCodeAndSupersededAtIsNull(String siteCode);
}
