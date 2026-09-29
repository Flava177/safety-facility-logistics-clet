package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaRuleConflictJpaRepository extends JpaRepository<RuleConflictRecord, UUID> {

    List<RuleConflictRecord> findBySiteCode(String siteCode);

    boolean existsByRuleIdAndConflictingRuleIdAndWinningRuleId(UUID ruleId, UUID conflictingRuleId,
            UUID winningRuleId);
}
