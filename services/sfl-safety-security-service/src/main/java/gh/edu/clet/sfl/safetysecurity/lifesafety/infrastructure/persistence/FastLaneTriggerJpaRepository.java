package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FastLaneTriggerJpaRepository extends JpaRepository<FastLaneTriggerJpaEntity, UUID> {

    List<FastLaneTriggerJpaEntity> findBySiteCodeOrderByTriggeredAtDesc(String siteCode, Pageable pageable);
}
