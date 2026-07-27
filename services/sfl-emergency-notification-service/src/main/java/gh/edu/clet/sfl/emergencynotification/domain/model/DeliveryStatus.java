package gh.edu.clet.sfl.emergencynotification.domain.model;

/** Per-recipient/provider delivery outcome reported by a provider callback. */
public enum DeliveryStatus {
    QUEUED,
    SENT,
    DELIVERED,
    FAILED,
    EXPIRED;

    public boolean failed() {
        return this == FAILED || this == EXPIRED;
    }
}
