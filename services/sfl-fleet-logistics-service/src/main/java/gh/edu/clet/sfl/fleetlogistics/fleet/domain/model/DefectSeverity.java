package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * How serious an inspection finding is.
 *
 * <p>A {@link #CRITICAL} defect takes the vehicle out of service immediately and raises a defect
 * workflow item, whatever the overall inspection result says.
 */
public enum DefectSeverity {
    ADVISORY,
    MINOR,
    MAJOR,
    CRITICAL;

    public boolean takesVehicleOutOfService() {
        return this == CRITICAL;
    }
}
