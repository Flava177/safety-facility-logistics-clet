package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnalyticsAlertJpaRepository extends JpaRepository<AnalyticsAlertJpaEntity, UUID> {

    List<AnalyticsAlertJpaEntity> findBySiteCodeAndStatus(String siteCode, AlertStatus status);
}
