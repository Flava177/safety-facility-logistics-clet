package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PanelHealthJpaRepository extends JpaRepository<PanelHealthJpaEntity, UUID> {

    Optional<PanelHealthJpaEntity> findBySiteCodeAndPanelId(String siteCode, String panelId);
}
