package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A vehicle's service history together with the current standing derived from it, so a client does not
 * have to re-derive "is this vehicle due?" from the raw list and risk disagreeing with the dashboard.
 */
public record ServiceHistoryResponse(
        UUID vehicleId,
        VehicleServiceStatus currentServiceStatus,
        LocalDate nextDueOn,
        Long nextDueOdometer,
        long currentOdometer,
        List<ServiceRecordResponse> history) {
}
