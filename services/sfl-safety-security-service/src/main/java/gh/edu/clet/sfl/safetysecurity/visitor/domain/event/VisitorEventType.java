package gh.edu.clet.sfl.safetysecurity.visitor.domain.event;

/**
 * S160 integration events. Names follow the platform-wide canonical rule {@code sfl.ssemp.{name}.v1}
 * - the same prefix S174 uses, since both are SSEMP - not a module-scoped prefix.
 */
public enum VisitorEventType {

    VISITOR_VISIT_PRE_REGISTERED("sfl.ssemp.visitor-visit-pre-registered.v1", "VisitorVisit"),
    VISITOR_VISIT_CONFIRMED("sfl.ssemp.visitor-visit-confirmed.v1", "VisitorVisit"),
    VISITOR_VISIT_REJECTED("sfl.ssemp.visitor-visit-rejected.v1", "VisitorVisit"),
    VISITOR_BADGE_ASSIGNED("sfl.ssemp.visitor-badge-assigned.v1", "VisitorVisit"),
    VISITOR_CHECKED_IN("sfl.ssemp.visitor-checked-in.v1", "VisitorVisit"),
    VISITOR_CHECKED_OUT("sfl.ssemp.visitor-checked-out.v1", "VisitorVisit"),
    VISITOR_VISIT_CANCELLED("sfl.ssemp.visitor-visit-cancelled.v1", "VisitorVisit");

    private final String eventType;
    private final String defaultAggregateType;

    VisitorEventType(String eventType, String defaultAggregateType) {
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
