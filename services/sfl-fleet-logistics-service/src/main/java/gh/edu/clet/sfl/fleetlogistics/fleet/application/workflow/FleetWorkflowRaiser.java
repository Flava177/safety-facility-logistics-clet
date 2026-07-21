package gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;

/**
 * Raises fleet workflow items in reaction to something the fleet observed.
 *
 * <p>Kept as a narrow interface so the aggregates that detect a problem — an inspection that fails, a
 * document that lapses — do not have to know how the workflow queue is built. It also keeps the
 * dependency one-way: trips and sweeps depend on this, the workflow service implements it.
 */
public interface FleetWorkflowRaiser {

    /** Opens a defect item after an inspection fails or records a critical defect. */
    FleetWorkflowItem raiseInspectionDefect(VehicleInspection inspection, Vehicle vehicle, ActorContext actor,
            SourceChannel sourceChannel);

    /** Opens a compliance item when a document has expired or is about to. */
    FleetWorkflowItem raiseComplianceExpiry(ComplianceDocument document, Vehicle vehicle, boolean expired,
            ActorContext actor, SourceChannel sourceChannel);

    /** Opens a maintenance item when a vehicle's service becomes due or overdue. */
    FleetWorkflowItem raiseServiceDue(Vehicle vehicle, boolean overdue, ActorContext actor,
            SourceChannel sourceChannel);
}
