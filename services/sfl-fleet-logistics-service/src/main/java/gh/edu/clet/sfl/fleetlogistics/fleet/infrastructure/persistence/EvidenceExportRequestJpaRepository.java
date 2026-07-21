package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EvidenceExportRequestJpaRepository extends JpaRepository<EvidenceExportRequestEntity, UUID> {
}
