package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverEligibilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Driver eligibility assessment (SRS-SFL-S166-05 "driver eligibility blockers"). */
public record EligibilityResponse(
        UUID driverId,
        DriverEligibilityStatus status,
        boolean permitsAssignment,
        List<BlockerResponse> blockers,
        Instant assessedAt,
        VehicleCategory assessedForCategory) {
}
