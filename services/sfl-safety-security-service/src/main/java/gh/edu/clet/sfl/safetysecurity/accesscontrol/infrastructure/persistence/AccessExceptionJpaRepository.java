package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessExceptionJpaRepository extends JpaRepository<AccessExceptionJpaEntity, UUID> {

    List<AccessExceptionJpaEntity> findBySiteCodeAndStatus(String siteCode, ExceptionStatus status);
}
