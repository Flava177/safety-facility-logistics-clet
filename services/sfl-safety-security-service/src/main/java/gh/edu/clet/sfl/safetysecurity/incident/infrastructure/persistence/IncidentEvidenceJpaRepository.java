package gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IncidentEvidenceJpaRepository extends JpaRepository<IncidentEvidenceJpaEntity, UUID> {

    List<IncidentEvidenceJpaEntity> findByIncidentIdOrderByUploadedAtAsc(UUID incidentId);
}
