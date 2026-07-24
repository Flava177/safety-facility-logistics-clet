package gh.edu.clet.sfl.fleetlogistics.fuel.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;

/**
 * Outbound seam that surfaces material fuel exceptions to Finance and Audit.
 * Implementations MUST NOT write to any Finance database directly; visibility is
 * delivered through the transactional outbox so it is atomic with the state change.
 */
public interface FinanceAuditVisibilityPort {

    /** Surface a material fuel exception for Finance/Audit reconciliation review. */
    void surfaceMaterialException(FuelAnomalyCase anomaly, ActorContext actor);
}
