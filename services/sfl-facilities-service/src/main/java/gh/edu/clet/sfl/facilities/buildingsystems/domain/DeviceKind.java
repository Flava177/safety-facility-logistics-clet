package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * What a registered BMS/IoT device physically is. SRS-SFL-S156-03 treats "sensor/gateway offline" as
 * one condition, so both are registered the same way; the kind is what the alert names.
 */
public enum DeviceKind {
    SENSOR,
    GATEWAY,
    CONTROLLER,
    METER
}
