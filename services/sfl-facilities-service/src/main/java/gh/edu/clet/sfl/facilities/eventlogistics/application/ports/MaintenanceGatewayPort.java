package gh.edu.clet.sfl.facilities.eventlogistics.application.ports;

import java.util.Optional;
import java.util.UUID;

/**
 * Pre-event maintenance through CMMS (S153) - SRS-SFL-S173-02: "CMMS (S153) for any maintenance work
 * needed ahead of the event".
 *
 * <p>Backed by S153's {@code AutomatedWorkOrderIntake}, which raises the fault, triages it and cuts the
 * work order as a platform account under category {@code EVENT_PRE_MAINTENANCE}, so an S153 reader can
 * tell event work from ordinary maintenance.
 */
public interface MaintenanceGatewayPort {

    GatewayOutcome raise(PreEventMaintenance request);

    /** The work order's current state, mapped. Empty if S153 has no such work order. */
    Optional<GatewayOutcome> state(String workOrderId);

    /**
     * @param idempotencyKey {@code event-logistics-maintenance:<requestId>:<attempt>} - derived from the
     *        subject, as the intake requires
     */
    record PreEventMaintenance(String siteCode, UUID roomId, String locationCode, String title,
            String description, String originReference, String requestedBy, String correlationId,
            String idempotencyKey) {
    }
}
