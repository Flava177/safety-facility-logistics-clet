package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Driver profile lifecycle, mirroring the vehicle lifecycle (SRS-SFL-S166-01 active/inactive/
 * suspended/archived).
 */
public enum DriverLifecycleStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    ARCHIVED;

    public boolean isEditable() {
        return this != ARCHIVED;
    }

    public boolean isOperational() {
        return this == ACTIVE;
    }
}
