package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * What kind of condition an S156 alert records.
 *
 * <p>{@link #SENSOR_OFFLINE} is its own type because S156-03 requires it: "a sensor/gateway offline for
 * longer than a configured window raises its own alert distinct from a telemetry fault". A dead sensor and
 * a hot server room need different people - an IoT engineer and an HVAC technician - and conflating them
 * sends the wrong one.
 */
public enum AlertType {
    /** A band rule breached for longer than its debounce window. */
    THRESHOLD_BREACH,
    /** A fault-code, lift-code or run-state rule matched for longer than its debounce window. */
    FAULT_CODE,
    /** Total power loss, lift entrapment or generator failed-start during an outage (S156-03). */
    CRITICAL_FAULT,
    /** No telemetry for longer than the offline window. */
    SENSOR_OFFLINE
}
