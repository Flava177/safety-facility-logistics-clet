package gh.edu.clet.sfl.emergencynotification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recipient acknowledgement, idempotent by {@code (activationId, recipientRef)} so a replayed callback
 * counts once.
 */
public record Acknowledgement(UUID id, UUID activationId, SiteCode siteCode, ChannelType channelType,
        String recipientRef, Instant acknowledgedAt, String createdBy, Instant createdAt, SourceChannel sourceChannel,
        String correlationId) {

    public Acknowledgement {
        Objects.requireNonNull(id);
        Objects.requireNonNull(activationId);
        Objects.requireNonNull(siteCode);
        Objects.requireNonNull(acknowledgedAt);
        Objects.requireNonNull(sourceChannel);
        Objects.requireNonNull(createdAt);
        recipientRef = require(recipientRef, "recipientRef");
        createdBy = require(createdBy, "createdBy");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
