package gh.edu.clet.sfl.fleetlogistics.fuel.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.UUID;

/** Public application boundary from S168_fuel to S166; fuel never reaches Fleet persistence directly. */
public interface FuelFleetReferencePort {
    Snapshot resolve(UUID vehicleId, UUID driverId, UUID tripId, String siteCode);
    void acceptOdometer(UUID vehicleId,long reading,Instant recordedAt,ActorContext actor,SourceChannel channel);
    record Snapshot(long acceptedOdometer,String vehicleLifecycle,String vehicleAvailability,String driverEligibility,
            boolean tripMatches, String driverStaffReference) {}
}
