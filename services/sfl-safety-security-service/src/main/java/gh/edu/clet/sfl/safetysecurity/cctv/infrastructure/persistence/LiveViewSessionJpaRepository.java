package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface LiveViewSessionJpaRepository extends JpaRepository<LiveViewSessionJpaEntity, UUID> {
}
