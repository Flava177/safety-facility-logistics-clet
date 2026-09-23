package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusterCheckInJpaRepository extends JpaRepository<MusterCheckInJpaEntity, UUID> {

    List<MusterCheckInJpaEntity> findByMusterSessionId(UUID musterSessionId);
}
