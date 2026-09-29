package gh.edu.clet.sfl.facilities.buildingsystems.application.ports;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import java.util.Optional;
import java.util.UUID;

/**
 * S156's way into S153 - SRS-SFL-S156-02: "a sustained breach ... raises an S153 work order pre-populated
 * with the system, location, telemetry evidence and suggested priority".
 *
 * <p>A port rather than a direct call to {@code AutomatedWorkOrderIntake} so the S156 application names no
 * S153 type, and so the unit tests can use a double. The one adapter,
 * {@code MaintenanceWorkOrderAdapter}, is a real in-process call into S153 - same deployable, so nothing is
 * simulated here.
 */
public interface BuildingWorkOrderPort {

    /** Raises - or, for a key already used, returns - the work order. Joins the caller's transaction. */
    WorkOrderReference raise(WorkOrderRequest request);

    /** The work order's current state, for the correlation check. Empty if S153 has no such order. */
    Optional<WorkOrderReference> find(UUID workOrderId);

    /**
     * @param alertId becomes the S153 origin reference and, as {@code bms-alert:<alertId>}, the idempotency
     *        key - derived from the subject so a retried transaction replays rather than duplicates
     * @param evidenceReference the triggering reading ids - the back-reference S156-02 requires
     */
    record WorkOrderRequest(String siteCode, UUID roomId, String locationCode, String title, String description,
            AlertPriority priority, UUID alertId, String evidenceReference, String correlationId) {
    }

    record WorkOrderReference(UUID workOrderId, String workOrderNumber, UUID faultId, boolean open) {
    }
}
