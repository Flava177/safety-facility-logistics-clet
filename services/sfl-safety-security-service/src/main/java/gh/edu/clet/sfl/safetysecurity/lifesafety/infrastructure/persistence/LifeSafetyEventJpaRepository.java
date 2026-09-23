package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifeSafetyEventJpaRepository extends JpaRepository<LifeSafetyEventJpaEntity, UUID> {

    List<LifeSafetyEventJpaEntity> findBySiteCodeOrderByOccurredAtDesc(String siteCode, Pageable pageable);

    LifeSafetyEventJpaEntity findFirstBySiteCodeOrderByOccurredAtDesc(String siteCode);
}
