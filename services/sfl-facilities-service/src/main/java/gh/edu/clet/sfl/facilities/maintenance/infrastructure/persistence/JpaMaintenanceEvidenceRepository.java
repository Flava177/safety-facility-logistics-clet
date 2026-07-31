package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.EvidenceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMaintenanceEvidenceRepository extends JpaRepository<MaintenanceEvidenceRecord, UUID> {

    List<MaintenanceEvidenceRecord> findByWorkOrderIdOrderByUploadedAtAsc(UUID workOrderId);

    /**
     * Counts what satisfies the closure requirement.
     *
     * <p>Invoices are excluded, matching {@code MaintenanceEvidence.supportsClosure()}: a parts
     * invoice proves money was spent, not that the work was done, and closure evidence exists to
     * prove the second thing.
     */
    long countByWorkOrderIdAndEvidenceTypeNot(UUID workOrderId, EvidenceType excluded);
}
