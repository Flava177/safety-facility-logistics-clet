package gh.edu.clet.sfl.emergencynotification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable provider delivery-status fact, idempotent by {@code (activationId, provider,
 * providerMessageId)} so a replayed callback is applied once.
 */
public record DeliveryReceipt(UUID id, UUID activationId, SiteCode siteCode, ChannelType channelType, String provider,
        String providerMessageId, String recipientRef, DeliveryStatus status, String reason, Instant occurredAt,
        String createdBy, Instant createdAt, SourceChannel sourceChannel, String correlationId) {

    public DeliveryReceipt {
        Objects.requireNonNull(id);
        Objects.requireNonNull(activationId);
        Objects.requireNonNull(siteCode);
        Objects.requireNonNull(channelType);
        Objects.requireNonNull(status);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(sourceChannel);
        Objects.requireNonNull(createdAt);
        provider = require(provider, "provider");
        providerMessageId = require(providerMessageId, "providerMessageId");
        createdBy = require(createdBy, "createdBy");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
