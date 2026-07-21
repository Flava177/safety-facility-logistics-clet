package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Request body for {@code PATCH /api/v1/fleet/vehicles/{vehicleId}}.
 *
 * <p>{@code expectedVersion} is the optimistic-lock version the client last read. Supplying it turns a
 * concurrent edit into a 409 the client can resolve, instead of a silent last-write-wins overwrite.
 */
public record UpdateVehicleRequest(
        @Size(max = 40) String vin,
        @NotBlank @Size(max = 80) String make,
        @NotBlank @Size(max = 80) String model,
        @NotNull @Min(1950) @Max(2100) Integer manufactureYear,
        @NotNull VehicleCategory category,
        @NotNull @Min(1) @Max(200) Integer capacity,
        @NotBlank @Size(max = 160) String responsibleUnit,
        @NotBlank @Size(max = 160) String operationalOwner,
        @Size(max = 120) String acquisitionReference,
        Boolean emergencyOnly,
        Set<OperatingMode> allowedOperatingModes,
        Long expectedVersion) {

    public boolean emergencyOnlyOrDefault() {
        return Boolean.TRUE.equals(emergencyOnly);
    }
}
