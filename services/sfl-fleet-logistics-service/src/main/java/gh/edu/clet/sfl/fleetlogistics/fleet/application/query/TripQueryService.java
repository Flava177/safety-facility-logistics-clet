package gh.edu.clet.sfl.fleetlogistics.fleet.application.query;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DriverProfileRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.TripRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleInspectionRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetReadinessService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
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
    private final DriverProfileRepository driverProfiles;
    private final FleetReadinessService readinessService;
    private final FleetAccessPolicy accessPolicy;

    public TripQueryService(TripRepository trips, VehicleRepository vehicles,
            VehicleInspectionRepository inspections, DriverProfileRepository driverProfiles,
            FleetReadinessService readinessService,
            FleetAccessPolicy accessPolicy) {
        this.trips = trips;
        this.vehicles = vehicles;
        this.inspections = inspections;
        this.driverProfiles = driverProfiles;
        this.readinessService = readinessService;
        this.accessPolicy = accessPolicy;
    }

    /**
     * Site scope is the wrong boundary for a driver, and it was the only one applied here.
     *
     * <p>{@link FleetAccessPolicy#requireRecordScope} is documented as "what keeps the limited
     * driver/mobile user class to their own trips and inspections", and it is unit-tested — but until
     * now no read called it, so a {@code FLEET_DRIVER} holding any trip id read that trip in full:
     * route, purpose, operating mode and the driver it belongs to. Applying it to the collection and
     * not to the record would be decorative, so it is applied here, where the record is returned.
     *
     * <p>A supervising {@code FLEET_TRIP_MANAGE} passes through, which is what keeps an officer or a
     * manager unaffected by the narrowing.
     */
    @Transactional(readOnly = true)
    public Trip findById(UUID tripId, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_TRIP_READ, RESOURCE_TYPE);
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, tripId));
        accessPolicy.requireSiteAccess(actor, trip.siteCode(), RESOURCE_TYPE, tripId.toString());
        accessPolicy.requireRecordScope(actor, driverOwnerReference(trip.driverId()),
                SflPermission.FLEET_TRIP_MANAGE, RESOURCE_TYPE, tripId.toString());
        return trip;
    }

    /** See {@link #findById}: the trip's owner is its driver's staff reference, the id they sign in as. */
    private String driverOwnerReference(UUID driverId) {
        return driverId == null ? null
                : driverProfiles.findById(driverId).map(DriverProfileReference::staffReference).orElse(null);
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
     * The readiness preview the dashboard shows before an officer commits to an assignment.
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
