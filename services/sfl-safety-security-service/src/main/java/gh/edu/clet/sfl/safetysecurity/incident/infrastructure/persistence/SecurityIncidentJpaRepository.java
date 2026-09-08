package gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SecurityIncidentJpaRepository extends JpaRepository<SecurityIncidentJpaEntity, UUID> {

    /**
     * Kept alongside {@link #searchPage} rather than replaced by it: {@code SecurityIncidentRepositoryAdapter}
     * (already shipped and covered by the optimistic-locking regression tests) calls this exact
     * signature, and is deliberately not touched here.
     */
    @Query("""
            SELECT i FROM SecurityIncidentJpaEntity i
            WHERE (:siteCode IS NULL OR i.siteCode = :siteCode)
              AND (:status IS NULL OR i.status = :status)
              AND (:severity IS NULL OR i.severity = :severity)
            ORDER BY i.createdAt DESC
            """)
    List<SecurityIncidentJpaEntity> search(@Param("siteCode") String siteCode,
            @Param("status") IncidentStatus status, @Param("severity") Severity severity, Pageable pageable);

    /**
     * Backs {@code SecurityIncidentSearchPageAdapter} - the proper {@code Page<T>}/{@code Pageable}
     * counterpart to {@link #search}, used by the paginated {@code GET /api/v1/incidents} response
     * (total count, page number, page size) instead of the old client-supplied-{@code limit}-only
     * shape. Spring Data derives the {@code COUNT(...)} query from this same JPQL automatically because
     * the return type is {@code Page<T>}.
     */
    @Query("""
            SELECT i FROM SecurityIncidentJpaEntity i
            WHERE (:siteCode IS NULL OR i.siteCode = :siteCode)
              AND (:status IS NULL OR i.status = :status)
              AND (:severity IS NULL OR i.severity = :severity)
            """)
    Page<SecurityIncidentJpaEntity> searchPage(@Param("siteCode") String siteCode,
            @Param("status") IncidentStatus status, @Param("severity") Severity severity, Pageable pageable);

    @Query("SELECT i.status, COUNT(i) FROM SecurityIncidentJpaEntity i WHERE i.siteCode = :siteCode GROUP BY i.status")
    List<Object[]> countByStatus(@Param("siteCode") String siteCode);

    @Query("""
            SELECT i.severity, COUNT(i) FROM SecurityIncidentJpaEntity i
            WHERE i.siteCode = :siteCode AND i.severity IS NOT NULL GROUP BY i.severity
            """)
    List<Object[]> countBySeverity(@Param("siteCode") String siteCode);
}
