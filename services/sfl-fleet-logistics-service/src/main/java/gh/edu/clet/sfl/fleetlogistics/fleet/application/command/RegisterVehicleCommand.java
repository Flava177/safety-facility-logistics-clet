package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import java.util.Map;
import java.util.Set;

/** Registers a vehicle in the site-scoped fleet register (SRS-SFL-S166-01). */
public record RegisterVehicleCommand(
        String registrationNumber,
        String vin,
        String make,
        String model,
        int manufactureYear,
        VehicleCategory category,
        int capacity,
        String siteCode,
        String responsibleUnit,
        String operationalOwner,
        String acquisitionReference,
        long initialOdometer,
        boolean emergencyOnly,
        Set<OperatingMode> allowedOperatingModes,
        ActorContext actor,
        SourceChannel sourceChannel,
        String idempotencyKey) implements FleetCommand {

    /**
     * The payload the idempotency store fingerprints. Excludes the actor and the key itself, so the
     * same registration retried by the same client is recognised while a different payload under a
     * reused key is rejected.
     */
    public Map<String, Object> idempotencyPayload() {
        return Map.of(
                "registrationNumber", String.valueOf(registrationNumber),
                "siteCode", String.valueOf(siteCode),
                "vin", String.valueOf(vin),
                "make", String.valueOf(make),
                "model", String.valueOf(model),
                "manufactureYear", manufactureYear,
                "capacity", capacity,
                "initialOdometer", initialOdometer);
    }
}
