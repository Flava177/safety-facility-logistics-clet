package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReaderHealthJpaRepository extends JpaRepository<ReaderHealthJpaEntity, UUID> {

    Optional<ReaderHealthJpaEntity> findBySiteCodeAndReaderId(String siteCode, String readerId);

    List<ReaderHealthJpaEntity> findBySiteCode(String siteCode);
}
