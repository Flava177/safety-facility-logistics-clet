package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaSiteRepository extends JpaRepository<SiteRecord, UUID> {
    List<SiteRecord> findAllByOrderBySiteCodeAsc();
}
