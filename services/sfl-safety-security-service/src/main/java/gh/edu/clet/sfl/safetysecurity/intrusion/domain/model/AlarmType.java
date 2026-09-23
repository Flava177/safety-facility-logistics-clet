package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

/** The alarm-worthy conditions an {@link IntrusionAlarm} is raised from. {@link #ZONE_ALARM},
 * {@link #TAMPER} and {@link #DEVICE_FAULT} come from ingested panel signals (excludes
 * {@link SignalType#RESTORATION}, which closes an alarm rather than being one); {@link #ARM_FAILURE}
 * comes from zone-administration itself - SRS-SFL-S162-04: "a zone that fails to arm when scheduled,
 * or is disarmed out of policy, raises an exception to the SOC". */
public enum AlarmType {
    ZONE_ALARM,
    TAMPER,
    DEVICE_FAULT,
    ARM_FAILURE
}
