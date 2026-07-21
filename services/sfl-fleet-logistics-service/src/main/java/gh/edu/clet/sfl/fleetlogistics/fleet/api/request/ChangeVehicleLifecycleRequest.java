package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /api/v1/fleet/vehicles/{vehicleId}/lifecycle}. */
public record ChangeVehicleLifecycleRequest(
        @NotNull VehicleLifecycleStatus targetStatus,
        @NotBlank @Size(max = 1000) String reason,
        Long expectedVersion) {
}
