package gh.edu.clet.sfl.fleetlogistics.fleet.application.query;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.ComplianceDocumentRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleServiceRecordRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read use cases for the vehicle register.
 *
 * <p>Every query is authorised and site-scoped. The scope filter is passed into the repository so it
 * becomes a SQL predicate: filtering after loading would let a caller's query touch rows they may not
 * see, which is the failure SRS-SFL-S166-01 is guarding against.
 */
@Service
public class VehicleQueryService {

    private static final String RESOURCE_TYPE = "Vehicle";

    private final VehicleRepository vehicles;
    private final ComplianceDocumentRepository complianceDocuments;
    private final VehicleServiceRecordRepository serviceRecords;
    private final FleetAccessPolicy accessPolicy;

    public VehicleQueryService(VehicleRepository vehicles, ComplianceDocumentRepository complianceDocuments,
            VehicleServiceRecordRepository serviceRecords, FleetAccessPolicy accessPolicy) {
        this.vehicles = vehicles;
        this.complianceDocuments = complianceDocuments;
        this.serviceRecords = serviceRecords;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public Vehicle findById(UUID vehicleId, ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_VEHICLE_READ, RESOURCE_TYPE);
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, vehicleId));
        accessPolicy.requireSiteAccess(actor, vehicle.siteCode(), RESOURCE_TYPE, vehicleId.toString());
        return vehicle;
    }

    @Transactional(readOnly = true)
    public VehicleRepository.VehiclePage search(VehicleRepository.VehicleSearchCriteria criteria,
            ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_VEHICLE_READ, RESOURCE_TYPE);
        SiteScopeFilter scope = accessPolicy.requireSiteScopeFilter(actor);
        return vehicles.search(criteria, scope);
    }

    @Transactional(readOnly = true)
    public List<ComplianceDocument> findComplianceDocuments(UUID vehicleId, ActorContext actor) {
        Vehicle vehicle = findById(vehicleId, actor);
        return complianceDocuments.findByVehicle(vehicle.id());
    }

    @Transactional(readOnly = true)
    public List<VehicleServiceRecord> findServiceHistory(UUID vehicleId, ActorContext actor) {
        Vehicle vehicle = findById(vehicleId, actor);
        return serviceRecords.findByVehicle(vehicle.id());
    }

    /** Whether the caller may see the unmasked sensitive fields on a vehicle. */
    public boolean canReadSensitive(ActorContext actor) {
        return accessPolicy.canReadSensitive(actor, SflPermission.FLEET_VEHICLE_SENSITIVE_READ);
    }
}
