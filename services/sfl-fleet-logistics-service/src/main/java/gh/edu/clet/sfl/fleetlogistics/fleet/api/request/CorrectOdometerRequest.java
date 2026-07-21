package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/fleet/vehicles/{vehicleId}/odometer-corrections}.
 *
 * <p>Reason and evidence are both required: an odometer correction is the one operation allowed to move
 * a reading backwards, so it has to leave a defensible trail.
 */
public record CorrectOdometerRequest(
        @NotNull @PositiveOrZero Long correctedReading,
        @NotBlank @Size(max = 1000) String reason,
        @NotNull UUID evidenceId,
        Long expectedVersion) {
}
