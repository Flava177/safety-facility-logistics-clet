package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.event;

/**
 * S162a integration events. Names follow the platform-wide canonical rule {@code sfl.ssemp.{name}.v1}
 * - the pre-defined ingestion name is reused verbatim from docs/integration/event-catalog.md; the
 * remainder are new lifecycle events added to that same catalog by this slice.
 */
public enum LifeSafetyEventType {

    LIFESAFETY_EVENT_OBSERVED("sfl.ssemp.fire-alarm-received.v1", "LifeSafetyEvent"),
    LIFESAFETY_FAST_LANE_TRIGGERED("sfl.ssemp.lifesafety-fast-lane-triggered.v1", "FastLaneTrigger"),
    LIFESAFETY_COMPLIANCE_EXCEPTION_RAISED("sfl.ssemp.lifesafety-compliance-exception-raised.v1",
            "LifeSafetyComplianceException"),
    LIFESAFETY_COMPLIANCE_EXCEPTION_RESOLVED("sfl.ssemp.lifesafety-compliance-exception-resolved.v1",
            "LifeSafetyComplianceException");

    private final String eventType;
    private final String defaultAggregateType;

    LifeSafetyEventType(String eventType, String defaultAggregateType) {
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
