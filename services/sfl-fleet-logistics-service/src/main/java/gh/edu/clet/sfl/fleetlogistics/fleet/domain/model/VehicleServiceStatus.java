package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Maintenance standing of a vehicle (SRS-SFL-S166-01 "service status").
 *
 * <p>Derived from the latest service record: whichever of the next-due date or the next-due odometer
 * is reached first drives the status. {@link #OUT_OF_SERVICE} is set by an authorised action or
 * automatically by an inspection that finds a critical defect.
 */
public enum VehicleServiceStatus {
    IN_SERVICE,
    DUE,
    OVERDUE,
    OUT_OF_SERVICE;

    public boolean blocksAssignment() {
        return this == OVERDUE || this == OUT_OF_SERVICE;
    }
}
