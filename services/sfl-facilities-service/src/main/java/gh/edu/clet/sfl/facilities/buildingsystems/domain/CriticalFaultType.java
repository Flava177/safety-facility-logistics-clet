package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * The three critical faults SRS-SFL-S156-03 names: "total power loss, lift entrapment signal, generator
 * failed-start during an outage". These bypass debounce and go to the emergency fast lane immediately.
 */
public enum CriticalFaultType {
    TOTAL_POWER_LOSS,
    LIFT_ENTRAPMENT,
    GENERATOR_FAILED_START_DURING_OUTAGE
}
