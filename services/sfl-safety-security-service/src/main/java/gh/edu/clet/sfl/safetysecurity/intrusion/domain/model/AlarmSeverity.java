package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

public enum AlarmSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** SRS-SFL-S162-02: "After-hours and examination-content-vault alarms carry elevated severity". */
    public AlarmSeverity elevate() {
        return switch (this) {
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH, CRITICAL -> CRITICAL;
        };
    }
}
