package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Whether a vehicle is free to be assigned (SRS-SFL-S166-01 "availability", SRS-SFL-S166-05
 * "vehicle availability" indicator).
 *
 * <p>Derived state: it follows the lifecycle status, the service status and the current assignment.
 * It is never a free-form field a client can set.
 */
public enum VehicleAvailabilityStatus {
    AVAILABLE,
    RESERVED,
    ASSIGNED,
    IN_USE,
    UNAVAILABLE;

    public boolean isFree() {
        return this == AVAILABLE;
    }
}
