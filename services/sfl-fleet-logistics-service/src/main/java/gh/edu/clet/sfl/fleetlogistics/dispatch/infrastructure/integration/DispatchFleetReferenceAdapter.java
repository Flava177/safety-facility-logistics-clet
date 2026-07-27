package gh.edu.clet.sfl.fleetlogistics.dispatch.infrastructure.integration;

import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchFleetReferencePort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DriverProfileRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.TripRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Read-only S166 reference validation for a dispatch's optional carrying trip/vehicle/driver. Dispatch
 * never writes to Fleet persistence; every reference is validated against the dispatch site through the
 * S166 application ports. All references are optional — chain-of-custody, receipt and return work with
 * no trip linked.
 */
@Component
public class DispatchFleetReferenceAdapter implements DispatchFleetReferencePort {

    private final VehicleRepository vehicles;
    private final DriverProfileRepository drivers;
    private final TripRepository trips;

    public DispatchFleetReferenceAdapter(VehicleRepository vehicles, DriverProfileRepository drivers,
            TripRepository trips) {
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.trips = trips;
    }

    @Override
    public void validate(UUID tripId, UUID vehicleId, UUID driverId, String siteCode) {
        if (vehicleId != null) {
            var vehicle = vehicles.findById(vehicleId)
                    .orElseThrow(() -> RecordNotFoundException.of("Vehicle", vehicleId));
            requireSite(vehicle.siteCode().value(), siteCode, "Vehicle", vehicleId);
        }
        if (driverId != null) {
            var driver = drivers.findById(driverId)
                    .orElseThrow(() -> RecordNotFoundException.of("Driver", driverId));
            requireSite(driver.siteCode().value(), siteCode, "Driver", driverId);
        }
        if (tripId != null) {
            var trip = trips.findById(tripId).orElseThrow(() -> RecordNotFoundException.of("Trip", tripId));
            requireSite(trip.siteCode().value(), siteCode, "Trip", tripId);
        }
    }

    private static void requireSite(String actual, String expected, String type, UUID id) {
        if (!actual.equals(gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode.of(expected).value())) {
            throw RecordNotFoundException.of(type, id);
        }
    }
}
