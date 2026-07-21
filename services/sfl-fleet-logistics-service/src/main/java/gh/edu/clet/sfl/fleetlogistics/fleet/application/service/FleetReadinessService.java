package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.ComplianceDocumentRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DriverProfileRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.TripRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleInspectionRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.ReadinessContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.VehicleReadinessPolicy;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads everything {@link VehicleReadinessPolicy} needs and asks it for a decision.
 *
 * <p>The split matters: this class does the I/O, the policy does the reasoning. That is what lets the
 * readiness rules be unit-tested exhaustively without a database, and it keeps the loading in one place
 * so the register, the dashboard and the assignment path all answer the same question the same way.
 */
@Service
public class FleetReadinessService {

    private final ComplianceDocumentRepository complianceDocuments;
    private final VehicleInspectionRepository inspections;
    private final TripRepository trips;
    private final DriverProfileRepository drivers;
    private final RuntimeConfigurationPort runtimeConfiguration;
    private final Clock clock;

    public FleetReadinessService(ComplianceDocumentRepository complianceDocuments,
            VehicleInspectionRepository inspections, TripRepository trips, DriverProfileRepository drivers,
            RuntimeConfigurationPort runtimeConfiguration, Clock clock) {
        this.complianceDocuments = complianceDocuments;
        this.inspections = inspections;
        this.trips = trips;
        this.drivers = drivers;
        this.runtimeConfiguration = runtimeConfiguration;
        this.clock = clock;
    }

    /** "Is this vehicle ready right now?" — the register and dashboard question. */
    @Transactional(readOnly = true)
    public ReadinessAssessment assessVehicle(Vehicle vehicle) {
        return assess(vehicle, null, null, OperatingMode.ROUTINE, null, null, false);
    }

    /**
     * "Can this vehicle and driver take this trip?" — the assignment question.
     *
     * @param excludingTripId the trip being (re)assigned, so it does not conflict with itself
     * @param inspectionRequired whether a valid inspection is a precondition; true when starting a trip
     */
    @Transactional(readOnly = true)
    public ReadinessAssessment assessForAssignment(Vehicle vehicle, UUID driverId, DateTimeRange period,
            OperatingMode operatingMode, SiteCode requestedSite, UUID excludingTripId,
            boolean inspectionRequired) {
        DriverProfileReference driver = driverId == null
                ? null
                : drivers.findById(driverId).orElse(null);
        return assess(vehicle, driver, period, operatingMode, requestedSite, excludingTripId, inspectionRequired);
    }

    private ReadinessAssessment assess(Vehicle vehicle, DriverProfileReference driver, DateTimeRange period,
            OperatingMode operatingMode, SiteCode requestedSite, UUID excludingTripId,
            boolean inspectionRequired) {
        String site = vehicle.siteCode().value();

        List<String> vehicleConflicts = period == null
                ? List.of()
                : trips.findVehicleConflicts(vehicle.id(), period, excludingTripId).stream()
                        .map(Trip::id).map(UUID::toString).toList();
        List<String> driverConflicts = period == null || driver == null
                ? List.of()
                : trips.findDriverConflicts(driver.id(), period, excludingTripId).stream()
                        .map(Trip::id).map(UUID::toString).toList();

        ReadinessContext context = new ReadinessContext(
                vehicle,
                driver,
                complianceDocuments.findByVehicle(vehicle.id()),
                null,
                inspections.findLatestByVehicle(vehicle.id()).orElse(null),
                vehicleConflicts,
                driverConflicts,
                period,
                operatingMode,
                requestedSite,
                clock.instant(),
                runtimeConfiguration.complianceExpiryWarningWindow(site),
                runtimeConfiguration.inspectionValidityWindow(site),
                runtimeConfiguration.odometerStalenessThreshold(site),
                true,
                inspectionRequired);

        return VehicleReadinessPolicy.assess(context);
    }
}
