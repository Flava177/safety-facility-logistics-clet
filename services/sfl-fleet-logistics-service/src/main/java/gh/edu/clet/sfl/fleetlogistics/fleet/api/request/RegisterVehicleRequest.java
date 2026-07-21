package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Request body for {@code POST /api/v1/fleet/vehicles} (SRS-SFL-S166-01). */
public record RegisterVehicleRequest(
        @NotBlank @Size(max = 40) String registrationNumber,
        @Size(max = 40) String vin,
        @NotBlank @Size(max = 80) String make,
        @NotBlank @Size(max = 80) String model,
        @NotNull @Min(1950) @Max(2100) Integer manufactureYear,
        @NotNull VehicleCategory category,
        @NotNull @Min(1) @Max(200) Integer capacity,
        @NotBlank @Size(max = 40) String siteCode,
        @NotBlank @Size(max = 160) String responsibleUnit,
        @NotBlank @Size(max = 160) String operationalOwner,
        @Size(max = 120) String acquisitionReference,
        @NotNull @PositiveOrZero Long initialOdometer,
        Boolean emergencyOnly,
        Set<OperatingMode> allowedOperatingModes) {

    /** Absent means "not restricted"; the field is optional in the contract. */
    public boolean emergencyOnlyOrDefault() {
        return Boolean.TRUE.equals(emergencyOnly);
    }
}
