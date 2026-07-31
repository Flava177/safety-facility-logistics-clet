package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.EvidenceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMaintenanceEvidenceRepository extends JpaRepository<MaintenanceEvidenceRecord, UUID> {

    List<MaintenanceEvidenceRecord> findByWorkOrderIdOrderByUploadedAtAsc(UUID workOrderId);

    /**
     * Candidates for disposal: not held, not already disposed of, oldest first.
     *
     * <p>Whether a candidate is actually eligible is the domain's arithmetic, not this query's — the
     * retention class decides that. This exists so the sweep reads a bounded slice rather than the
     * whole table, and the partial index in V13 matches it.
     */
    List<MaintenanceEvidenceRecord> findByDisposedAtIsNullAndLegalHoldFalseOrderByUploadedAtAsc(
            org.springframework.data.domain.Pageable pageable);

    /**
     * Counts what satisfies the closure requirement.
     *
     * <p>Invoices are excluded, matching {@code MaintenanceEvidence.supportsClosure()}: a parts
     * invoice proves money was spent, not that the work was done, and closure evidence exists to
     * prove the second thing.
     */
    long countByWorkOrderIdAndEvidenceTypeNot(UUID workOrderId, EvidenceType excluded);
}
