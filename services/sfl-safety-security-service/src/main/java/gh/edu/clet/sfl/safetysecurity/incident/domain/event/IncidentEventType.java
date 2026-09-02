package gh.edu.clet.sfl.safetysecurity.incident.domain.event;

/**
 * S163 integration events. Names follow the platform-wide canonical rule {@code sfl.ssemp.{name}.v1}
 * - the same prefix S174 and S160 use, since all three share the SSEMP deployable and event namespace.
 */
public enum IncidentEventType {

    SECURITY_INCIDENT_REPORTED("sfl.ssemp.security-incident-reported.v1", "SecurityIncident"),
    SECURITY_INCIDENT_TRIAGED("sfl.ssemp.security-incident-triaged.v1", "SecurityIncident"),
    /** Hard rule 2: an emergency-rated incident triggers the command/emergency path automatically. */
    SECURITY_INCIDENT_EMERGENCY_ESCALATED("sfl.ssemp.security-incident-emergency-escalated.v1", "SecurityIncident"),
    SECURITY_INCIDENT_INVESTIGATION_OPENED("sfl.ssemp.security-incident-investigation-opened.v1", "SecurityIncident"),
    SECURITY_INCIDENT_EVIDENCE_ATTACHED("sfl.ssemp.security-incident-evidence-attached.v1", "SecurityIncident"),
    SECURITY_INCIDENT_CAPA_OPENED("sfl.ssemp.security-incident-capa-opened.v1", "SecurityIncident"),
    SECURITY_INCIDENT_CAPA_VERIFIED("sfl.ssemp.security-incident-capa-verified.v1", "SecurityIncident"),
    SECURITY_INCIDENT_CLOSED("sfl.ssemp.security-incident-closed.v1", "SecurityIncident");

    private final String eventType;
    private final String defaultAggregateType;

    IncidentEventType(String eventType, String defaultAggregateType) {
        this.eventType = eventType;
        this.defaultAggregateType = defaultAggregateType;
    }

    public String eventType() {
        return eventType;
    }

    public String defaultAggregateType() {
        return defaultAggregateType;
    }

    public int version() {
        return Integer.parseInt(eventType.substring(eventType.lastIndexOf(".v") + 2));
    }
}
