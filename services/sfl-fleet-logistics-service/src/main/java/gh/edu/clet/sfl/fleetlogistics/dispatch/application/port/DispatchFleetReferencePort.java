package gh.edu.clet.sfl.fleetlogistics.dispatch.application.port;

import java.util.UUID;

/**
 * Application boundary from S171 to S166. A sensitive dispatch may be carried by an S166 trip; the
 * references are validated read-only through this port and dispatch never reaches Fleet persistence
 * directly. All references are optional: chain-of-custody, receipt and return work with no trip linked.
 */
public interface DispatchFleetReferencePort {

    /**
     * Validate the optional trip/vehicle/driver soft references against the site. A no-op when all are
     * null. Throws {@code RecordNotFoundException} when a provided reference does not resolve to the site.
     */
    void validate(UUID tripId, UUID vehicleId, UUID driverId, String siteCode);
}
