package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.event;

/** S160a integration events. Names follow the platform-wide canonical rule {@code sfl.ssemp.{name}.v1}. */
public enum AccessControlEventType {

    ACCESS_EVENT_INGESTED("sfl.ssemp.access-event-ingested.v1", "AccessEvent"),
    ACCESS_READER_HEALTH_CHANGED("sfl.ssemp.access-reader-health-changed.v1", "ReaderHealth"),
    ACCESS_PROVISIONING_GRANTED("sfl.ssemp.access-provisioning-granted.v1", "AccessProvisioning"),
    ACCESS_PROVISIONING_REVOKED("sfl.ssemp.access-provisioning-revoked.v1", "AccessProvisioning"),
    ACCESS_OVERRIDE_ACTIVATED("sfl.ssemp.access-override-activated.v1", "AccessOverride"),
    ACCESS_OVERRIDE_EXPIRED("sfl.ssemp.access-override-expired.v1", "AccessOverride"),
    ACCESS_EXCEPTION_RAISED("sfl.ssemp.access-exception-raised.v1", "AccessException"),
    ACCESS_EXCEPTION_RESOLVED("sfl.ssemp.access-exception-resolved.v1", "AccessException"),
    ACCESS_ZONE_DEFINED("sfl.ssemp.access-zone-defined.v1", "AccessZone");

    private final String eventType;
    private final String defaultAggregateType;

    AccessControlEventType(String eventType, String defaultAggregateType) {
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
