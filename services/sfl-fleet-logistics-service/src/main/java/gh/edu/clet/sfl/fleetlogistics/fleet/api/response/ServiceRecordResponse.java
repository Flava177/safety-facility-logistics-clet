package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceOutcome;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One entry in a vehicle's service history. */
public record ServiceRecordResponse(
        UUID id,
        UUID vehicleId,
        String siteCode,
        ServiceType serviceType,
        LocalDate performedOn,
        long odometerAtService,
        LocalDate nextDueOn,
        Long nextDueOdometer,
        String providerReference,
        String workSummary,
        ServiceOutcome outcome,
        UUID evidenceId,
        Instant createdAt,
        String createdBy,
        long version) {
}
