package gh.edu.clet.sfl.emergencynotification.domain.model;

/** Aggregate delivery status of one channel of an activation. */
public enum ChannelStatus {
    PENDING,
    SENDING,
    DELIVERED,
    PARTIALLY_DELIVERED,
    FAILED
}
