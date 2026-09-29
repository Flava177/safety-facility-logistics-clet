package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * A registered device's lifecycle - SRS-SFL-S156-04.
 *
 * <p>Two states and one transition, on purpose. "Decommissioning a device retires rather than deletes
 * its record and history": there is no DELETE anywhere in this module, and {@code RETIRED} is terminal.
 * A replacement device is a new registration, so a retired device's readings, alerts and work-order
 * links keep pointing at the record that actually produced them.
 */
public enum DeviceStatus {
    ACTIVE,
    RETIRED;

    public boolean canTransitionTo(DeviceStatus target) {
        return this == ACTIVE && target == RETIRED;
    }
}
