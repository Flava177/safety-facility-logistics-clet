package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Result of a service event.
 *
 * <p>{@link #INCOMPLETE} and {@link #FAILED} deliberately do not clear an overdue service: work that
 * did not finish must not make a vehicle look serviceable.
 */
public enum ServiceOutcome {
    COMPLETED,
    COMPLETED_WITH_ADVISORIES,
    INCOMPLETE,
    FAILED;

    public boolean returnsVehicleToService() {
        return this == COMPLETED || this == COMPLETED_WITH_ADVISORIES;
    }
}
