package gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CorrectiveActionJpaRepository extends JpaRepository<CorrectiveActionJpaEntity, UUID> {

    List<CorrectiveActionJpaEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    @Query("""
            SELECT COUNT(a) FROM CorrectiveActionJpaEntity a
            WHERE a.incidentId = :incidentId AND a.mandatory = true AND a.status IN ('OPEN', 'IN_PROGRESS')
            """)
    long countOpenMandatory(@Param("incidentId") UUID incidentId);
}
