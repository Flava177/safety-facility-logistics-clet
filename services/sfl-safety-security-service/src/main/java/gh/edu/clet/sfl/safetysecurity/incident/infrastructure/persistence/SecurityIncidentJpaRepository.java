package gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SecurityIncidentJpaRepository extends JpaRepository<SecurityIncidentJpaEntity, UUID> {

    @Query("""
            SELECT i FROM SecurityIncidentJpaEntity i
            WHERE (:siteCode IS NULL OR i.siteCode = :siteCode)
              AND (:status IS NULL OR i.status = :status)
              AND (:severity IS NULL OR i.severity = :severity)
            ORDER BY i.createdAt DESC
            """)
    List<SecurityIncidentJpaEntity> search(@Param("siteCode") String siteCode,
            @Param("status") IncidentStatus status, @Param("severity") Severity severity, Pageable pageable);

    @Query("SELECT i.status, COUNT(i) FROM SecurityIncidentJpaEntity i WHERE i.siteCode = :siteCode GROUP BY i.status")
    List<Object[]> countByStatus(@Param("siteCode") String siteCode);

    @Query("""
            SELECT i.severity, COUNT(i) FROM SecurityIncidentJpaEntity i
            WHERE i.siteCode = :siteCode AND i.severity IS NOT NULL GROUP BY i.severity
            """)
    List<Object[]> countBySeverity(@Param("siteCode") String siteCode);
}
