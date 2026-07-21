package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceOutcome;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Request body for {@code POST /api/v1/fleet/vehicles/{vehicleId}/service-records}. */
public record RecordVehicleServiceRequest(
        @NotNull ServiceType serviceType,
        @NotNull LocalDate performedOn,
        @NotNull @PositiveOrZero Long odometerAtService,
        LocalDate nextDueOn,
        @PositiveOrZero Long nextDueOdometer,
        @Size(max = 160) String providerReference,
        @NotBlank @Size(max = 2000) String workSummary,
        @NotNull ServiceOutcome outcome,
        UUID evidenceId) {
}
