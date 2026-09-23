package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DispatchOutcome;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ResponseDispatchJpaRepository extends JpaRepository<ResponseDispatchJpaEntity, UUID> {

    Optional<ResponseDispatchJpaEntity> findByAlarmId(UUID alarmId);

    long countBySiteCodeAndOutcome(String siteCode, DispatchOutcome outcome);
}
