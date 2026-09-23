package gh.edu.clet.sfl.safetysecurity.cctv.domain.event;

/**
 * S161 integration events. Names follow the platform-wide canonical rule {@code sfl.ssemp.{name}.v1}.
 * {@code CAMERA_HEALTH_CHANGED} and {@code EVIDENCE_REQUESTED} are the pre-seeded catalog names from
 * {@code docs/integration/event-catalog.md} and are reused verbatim; the rest are new S161 lifecycle
 * events added to that catalog alongside this build.
 */
public enum CctvEventType {

    CAMERA_HEALTH_CHANGED("sfl.ssemp.camera-health-changed.v1", "Camera"),
    EVIDENCE_REQUESTED("sfl.ssemp.cctv-evidence-requested.v1", "EvidenceRequest"),
    EVIDENCE_REQUEST_DECIDED("sfl.ssemp.cctv-evidence-request-decided.v1", "EvidenceRequest"),
    EVIDENCE_ITEM_RECORDED("sfl.ssemp.cctv-evidence-item-recorded.v1", "EvidenceItem"),
    ANALYTICS_ALERT_RAISED("sfl.ssemp.cctv-analytics-alert-raised.v1", "AnalyticsAlert"),
    ANALYTICS_ALERT_RESOLVED("sfl.ssemp.cctv-analytics-alert-resolved.v1", "AnalyticsAlert"),
    RETENTION_PURGED("sfl.ssemp.cctv-retention-purged.v1", "EvidenceItem"),
    DISCLOSURE_DECIDED("sfl.ssemp.cctv-disclosure-decided.v1", "Disclosure");

    private final String eventType;
    private final String defaultAggregateType;

    CctvEventType(String eventType, String defaultAggregateType) {
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
