package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSiteAccessGrantJpaRepository extends JpaRepository<SiteAccessGrantRecord, UUID> {

    List<SiteAccessGrantRecord> findByContractorId(UUID contractorId);

    List<SiteAccessGrantRecord> findByStatusOrderByRequestedAtAsc(SiteAccessGrant.Status status, Pageable pageable);
}
