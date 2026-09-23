package gh.edu.clet.sfl.safetysecurity.intrusion.domain.event;

/**
 * S162 integration events. Names follow the platform-wide canonical rule {@code sfl.ssemp.{name}.v1}.
 * {@code ALARM_RAISED} reuses {@code sfl.ssemp.intrusion-alarm-received.v1}, which was already
 * pre-seeded in docs/integration/event-catalog.md before this module existed; the rest are new
 * lifecycle events added to that same catalog by this slice.
 */
public enum IntrusionEventType {

    ALARM_RAISED("sfl.ssemp.intrusion-alarm-received.v1", "IntrusionAlarm"),
    PANEL_HEALTH_CHANGED("sfl.ssemp.intrusion-panel-health-changed.v1", "PanelHealth"),
    ALARM_ACKNOWLEDGED("sfl.ssemp.intrusion-alarm-acknowledged.v1", "IntrusionAlarm"),
    ALARM_ESCALATED("sfl.ssemp.intrusion-alarm-escalated.v1", "IntrusionAlarm"),
    ALARM_RESOLVED("sfl.ssemp.intrusion-alarm-resolved.v1", "IntrusionAlarm"),
    ZONE_DISARMED("sfl.ssemp.intrusion-zone-disarmed.v1", "IntrusionZone"),
    ZONE_ARMED("sfl.ssemp.intrusion-zone-armed.v1", "IntrusionZone");

    private final String eventType;
    private final String defaultAggregateType;

    IntrusionEventType(String eventType, String defaultAggregateType) {
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
