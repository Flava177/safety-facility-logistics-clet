package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DefectSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records an inspection against a trip (traced to SRS-SFL-S166-01 and -02; gap report C-01).
 *
 * <p>A critical finding takes the vehicle out of service and opens a defect workflow item, so the
 * command carries the findings rather than a pre-computed result.
 */
public record RecordInspectionCommand(
        UUID tripId,
        UUID vehicleId,
        InspectionType inspectionType,
        long odometerReading,
        UUID evidenceId,
        List<Finding> findings,
        String notes,
        ActorContext actor,
        SourceChannel sourceChannel,
        String idempotencyKey) implements FleetCommand {

    public record Finding(String checkCode, String description, DefectSeverity severity) {
    }

    public Map<String, Object> idempotencyPayload() {
        return Map.of(
                "tripId", String.valueOf(tripId),
                "vehicleId", String.valueOf(vehicleId),
                "inspectionType", String.valueOf(inspectionType),
                "odometerReading", odometerReading,
                "findingCount", findings == null ? 0 : findings.size());
    }
}
