package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vehicle readiness assessment (SRS-SFL-S166-05 "readiness blockers").
 *
 * <p>Always computed from the current facts; the response carries the assessment instant and the
 * period it was assessed for so a client can tell what question was actually answered.
 */
public record ReadinessResponse(
        UUID vehicleId,
        UUID driverId,
        ReadinessStatus status,
        boolean permitsAssignment,
        List<BlockerResponse> blockers,
        Instant assessedAt,
        Instant periodStart,
        Instant periodEnd,
        OperatingMode operatingMode) {
}
