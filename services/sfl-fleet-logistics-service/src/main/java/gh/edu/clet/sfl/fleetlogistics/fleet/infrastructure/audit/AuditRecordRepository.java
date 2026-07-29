package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read/insert access to the audit log.
 *
 * <p>There are deliberately no update or delete query methods; the SRS forbids modification by normal
 * application roles and a database trigger enforces the same rule.
 *
 * <p>Filtered search lives in {@link AuditRecordSearch} rather than in a derived query — the JPQL
 * version could not execute at all against PostgreSQL. See that interface for the detail.
 */
public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, UUID>, AuditRecordSearch {

    List<AuditRecordEntity> findAllByOrderBySequenceNoAsc();
}
