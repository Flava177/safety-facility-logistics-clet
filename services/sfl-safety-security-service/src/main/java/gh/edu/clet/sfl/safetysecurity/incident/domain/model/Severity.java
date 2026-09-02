package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

/**
 * The severity an HSE Officer assigns at triage - SRS §D.9 step 2. Extends the platform's usual
 * four-band scheme ({@code emergency.domain.model.Priority}) with {@link #EMERGENCY}, because D.9
 * hard rule 2 treats an emergency rating as a distinct, automatically-escalating case rather than
 * just the top of an ordinary severity scale.
 */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    EMERGENCY;

    /** {@code true} when this rating triggers the command/emergency path automatically (hard rule 2). */
    public boolean triggersEmergencyEscalation() {
        return this == EMERGENCY;
    }
}
