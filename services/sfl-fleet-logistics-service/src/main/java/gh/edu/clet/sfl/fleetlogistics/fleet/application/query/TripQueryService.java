package gh.edu.clet.sfl.fleetlogistics.fleet.application.query;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.TripRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleInspectionRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetReadinessService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read use cases for trips, inspections and the readiness preview shown before an assignment. */
@Service
public class TripQueryService {

    private static final String RESOURCE_TYPE = "Trip";

    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final VehicleInspectionRepository inspections;
    private final FleetReadinessService readinessService;
    private final FleetAccessPolicy accessPolicy;

    public TripQueryService(TripRepository trips, VehicleRepository vehicles,
            VehicleInspectionRepository inspections, FleetReadinessService readinessService,
            FleetAccessPolicy accessPolicy) {
        this.trips = trips;
        this.vehicles = vehicles;
        this.inspections = inspections;
        this.readinessService = readinessService;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public Trip findById(UUID tripId, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_TRIP_READ, RESOURCE_TYPE);
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, tripId));
        accessPolicy.requireSiteAccess(actor, trip.siteCode(), RESOURCE_TYPE, tripId.toString());
        return trip;
    }

    @Transactional(readOnly = true)
    public TripRepository.TripPage search(TripRepository.TripSearchCriteria criteria, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_TRIP_READ, RESOURCE_TYPE);
        SiteScopeFilter scope = accessPolicy.requireSiteScopeFilter(actor);
        return trips.search(criteria, scope);
    }

    @Transactional(readOnly = true)
    public List<VehicleInspection> findInspections(UUID tripId, ActorContext actor) {
        Trip trip = findById(tripId, actor);
        return inspections.findByTrip(trip.id());
    }

    /**
     * The readiness preview the console shows before an officer commits to an assignment.
     *
     * <p>Answering the same question the assignment path will ask — with the same policy and the same
     * inputs — is what stops the preview and the outcome disagreeing.
     */
    @Transactional(readOnly = true)
    public ReadinessAssessment previewAssignment(UUID vehicleId, UUID driverId, Instant from, Instant to,
            OperatingMode operatingMode, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_VEHICLE_READ, "Vehicle");
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> RecordNotFoundException.of("Vehicle", vehicleId));
        accessPolicy.requireSiteAccess(actor, vehicle.siteCode(), "Vehicle", vehicleId.toString());

        if (from == null || to == null) {
            return readinessService.assessVehicle(vehicle);
        }
        return readinessService.assessForAssignment(vehicle, driverId, DateTimeRange.of(from, to),
                operatingMode == null ? OperatingMode.ROUTINE : operatingMode, vehicle.siteCode(), null, false);
    }
}
