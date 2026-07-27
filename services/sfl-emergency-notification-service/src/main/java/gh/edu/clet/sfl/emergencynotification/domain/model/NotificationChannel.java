package gh.edu.clet.sfl.emergencynotification.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Per-activation×channel fan-out record with delivery/acknowledgement counters. Mutable via copies. */
public record NotificationChannel(UUID id, UUID activationId, SiteCode siteCode, ChannelType channelType,
        ChannelStatus status, int targetCount, int sentCount, int deliveredCount, int failedCount,
        int acknowledgedCount, RecordMetadata metadata) {

    public NotificationChannel {
        Objects.requireNonNull(id);
        Objects.requireNonNull(activationId);
        Objects.requireNonNull(siteCode);
        Objects.requireNonNull(channelType);
        Objects.requireNonNull(status);
        Objects.requireNonNull(metadata);
        if (targetCount < 0 || sentCount < 0 || deliveredCount < 0 || failedCount < 0 || acknowledgedCount < 0) {
            throw new IllegalArgumentException("channel counts cannot be negative");
        }
    }

    public NotificationChannel recordDelivery(DeliveryStatus status, RecordMetadata changed) {
        int delivered = deliveredCount + (status == DeliveryStatus.DELIVERED ? 1 : 0);
        int failed = failedCount + (status.failed() ? 1 : 0);
        int sent = sentCount + (status == DeliveryStatus.SENT || status == DeliveryStatus.DELIVERED ? 1 : 0);
        ChannelStatus next = failed > 0 && delivered > 0 ? ChannelStatus.PARTIALLY_DELIVERED
                : failed > 0 && delivered == 0 ? ChannelStatus.FAILED
                : delivered >= targetCount && targetCount > 0 ? ChannelStatus.DELIVERED : ChannelStatus.SENDING;
        return new NotificationChannel(id, activationId, siteCode, channelType, next, targetCount, sent, delivered,
                failed, acknowledgedCount, changed);
    }

    public NotificationChannel recordAcknowledgement(RecordMetadata changed) {
        return new NotificationChannel(id, activationId, siteCode, channelType, status, targetCount, sentCount,
                deliveredCount, failedCount, acknowledgedCount + 1, changed);
    }
}
