package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifeSafetyComplianceExceptionJpaRepository
        extends JpaRepository<LifeSafetyComplianceExceptionJpaEntity, UUID> {

    List<LifeSafetyComplianceExceptionJpaEntity> findBySiteCodeAndStatus(String siteCode,
            ComplianceExceptionStatus status);

    List<LifeSafetyComplianceExceptionJpaEntity> findBySiteCode(String siteCode);

    boolean existsBySiteCodeAndRefIdAndKindAndStatus(String siteCode, UUID refId, ComplianceExceptionKind kind,
            ComplianceExceptionStatus status);
}
